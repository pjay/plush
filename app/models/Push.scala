package models

import play.api.libs.concurrent._
import play.api.libs.json._
import play.api.libs.ws._
import akka.actor._
import akka.routing.RoundRobinRouter
import akka.util.Duration
import akka.util.duration._
import com.notnoop.apns._

sealed trait PushMessage
case class SendIosBroadcast(app: App, payload: Map[String, Any]) extends PushMessage
case class SendIosNotifications(deviceTokens: List[DeviceToken], payload: Map[String, Any]) extends PushMessage
case class StopIosWorkers(app: App) extends PushMessage
case class SendGcmBroadcast(app: App, payload: Map[String, Any]) extends PushMessage
case class SendGcmNotifications(app: App, registrations: List[Registration], payload: Map[String, Any]) extends PushMessage

object Push {

  private val system = ActorSystem("PushSystem")
  private val iosDispatcher = system.actorOf(Props(new IosDispatcher()))
  private val gcmDispatcher = system.actorOf(Props(new GcmDispatcher()))

  def sendIosBroadcast(app: App, payload: Map[String, Any]) =
    iosDispatcher ! SendIosBroadcast(app, payload)

  def stopIosWorkers(app: App) =
    iosDispatcher ! StopIosWorkers(app)

  def sendGcmBroadcast(app: App, payload: Map[String, Any]) =
    gcmDispatcher ! SendGcmBroadcast(app, payload)

}

class IosDispatcher extends Actor {

  var workers = Map[String, ActorRef]()

  def receive = {
    case SendIosBroadcast(app, payload) => {
      val worker = workers.get(app.key) match {
        case Some(worker) => worker
        case None => {
          val worker = context.actorOf(Props(new IosDispatchWorker(app)))
          workers = workers + (app.key -> worker)
          worker
        }
      }
      val deviceTokens = DeviceToken.findAllByAppKey(app.key)
      worker ! SendIosNotifications(deviceTokens, payload)
    }
    case StopIosWorkers(app) => {
      workers.get(app.key) foreach { w => context.stop(w) }
      workers = workers - app.key
    }
  }

}

class GcmDispatcher extends Actor {

  val maxMessagesByWorker = 1000

  def receive = {
    case SendGcmBroadcast(app, payload) => {
      Registration.findAllByAppKey(app.key) grouped(maxMessagesByWorker) foreach { registrations =>
        val worker = context.actorOf(Props(new GcmDispatchWorker()))
        worker ! SendGcmNotifications(app, registrations, payload)
      }
    }
  }

}

class IosDispatchWorker(app: App) extends Actor {

  val builder = APNS.newService.withCert(app.certFile.getAbsolutePath, app.iosCertPassword.getOrElse("")).withAppleDestination(app.appMode == 1).withReconnectPolicy(ReconnectPolicy.Provided.EVERY_HALF_HOUR).asPool(15)
  val service = if (app.debugMode) builder.build else builder.withNoErrorDetection.build
  // TODO: test connection

  def receive = {
    case SendIosNotifications(deviceTokens, payload) => {
      val startTime = System.currentTimeMillis
      deviceTokens foreach { token => service.push(token.value, Json.stringify(JsonUtil.toJson(payload))) }
      val elapsed = (System.currentTimeMillis - startTime).toFloat / 1000
      val log = "Successfully delivered %d iOS notifications in %.3f seconds".format(deviceTokens.length, elapsed)
      Event.create(app.key, Event.Severity.INFO, log)
    }
  }

  // TODO: handle debug mode
  // TODO: poll the feedback service regularly

}

class GcmDispatchWorker extends Actor {

  val apiEndpoint = "https://android.googleapis.com/gcm/send"
  val startTime = System.currentTimeMillis

  def receive = {
    case SendGcmNotifications(app, registrations, payload) => {
      val payloadWithRegistrations = payload.updated("registration_ids", registrations map (_.value))
      // TODO: set a timeout
      WS.url(apiEndpoint).withHeaders(
        "Authorization" -> ("key=" + app.gcmApiKey.getOrElse("")),
        "Content-Type" -> "application/json"
      ) post JsonUtil.toJson(payloadWithRegistrations) map { response =>
        handleResponse(app, registrations, response)
      } extend1 {
        // TODO: retry mechanism based on the exception type (e.g. ConnectException)
        case Thrown(throwable) => {
          play.Logger.debug(throwable.getClass.getName)
          throwable.printStackTrace
          val log = "An error occured while sending the GCM notifications - please contact the developers (error: " + throwable.getMessage() + ")"
          Event.create(app.key, Event.Severity.ERROR, log)
          context.stop(self)
        }
        case Redeemed(value) => {
          context.stop(self)
        }
      }
    }
  }

  def handleResponse(app: App, registrations: List[Registration], response: Response) = response.status match {
    case 200 => {
      // Success
      val elapsed = (System.currentTimeMillis - startTime).toFloat / 1000

      val json = Json.parse(response.body)
      val success = (json \ "success").asOpt[Int].getOrElse(-1)
      val failure = (json \ "failure").asOpt[Int].getOrElse(-1)
      val canonicalIds = (json \ "canonical_ids").asOpt[Int].getOrElse(-1)

      if (failure == 0 && canonicalIds == 0) {
        val log = "Successfully delivered %d GCM messages in %.3f seconds".format(registrations.length, elapsed)
        Event.create(app.key, Event.Severity.INFO, log)

        // TODO: stats (including the delivery time)
        // TODO: mark messages as sent (once the temporary messages are persistent)
      } else {
        val log = "Delivered %d GCM messages (%d successes, %d failures) in %.3f seconds".format(registrations.length, success, failure, elapsed)
        Event.create(app.key, Event.Severity.INFO, log)
        
        (json \ "results").asOpt[List[Map[String, String]]] match {
          case Some(results: List[Map[String, String]]) => {
            val retryItems = processResultItems(app, registrations, results)
            // TODO: retry items
          }
          case _ => {
            // TODO: handle error
          }
        }
      }
    }
    case 400 => {
      // Invalid JSON format or invalid fields
      // TODO
      play.Logger.debug("HTTP 400")
    }
    case 401 => {
      // Authentication error
      // TODO
      play.Logger.debug("HTTP 401")
    }
    case r if r >= 500 && r < 600 => {
      // Internal server error (500) or Server temporary unavailable (503)
      // TODO
      play.Logger.debug("HTTP 5xx")
    }
    case _ => {
      // Unknown status code
      // TODO
      play.Logger.debug("HTTP " + response.status)
    }
  }

  def processResultItems(app: App, registrations: List[Registration], resultItems: List[Map[String, String]]) = {
    ((resultItems zip registrations) foldLeft List[Registration]()) { case (acc, (item, reg)) =>
      if (item.get("message_id").isDefined) {
        if (item.get("registration_id").isDefined) {
          // TODO: check canonical registration ID and remove duplicate
        }
        acc
      } else {
        item.get("error") match {
          case Some("InvalidRegistration") => /* TODO: delete registration */ acc
          case Some("Unavailable")         => reg :: acc
          case Some("InternalServerError") => reg :: acc
          case Some("NotRegistered")       => /* TODO: delete registration */ acc
          case Some("MismatchSenderId")    => /* TODO: delete registration */ acc
          case Some("MessageTooBig")       => /* TODO: mark message as sent, log error */ acc
          case Some("InvalidTtl")          => /* TODO: mark message as sent, log error */ acc
          case _                           => /* TODO: log error */ acc
        }
      }
    }
  }

}

object JsonUtil {

  def toJson(value: Any): JsValue = value match {
    case v: Boolean => Json.toJson(v)
    case v: Double => Json.toJson(v)
    case v: Float => Json.toJson(v)
    case v: Int => Json.toJson(v)
    case v: Long => Json.toJson(v)
    case v: Short => Json.toJson(v)
    case v: String => Json.toJson(v)
    case v: Seq[_] => Json.toJson(v map toJson)
    case v: Map[_, _] => Json.toJson(v map { case (k: String, v) => (k, toJson(v)) })
    case _ => JsNull
  }

}