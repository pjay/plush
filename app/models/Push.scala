package models

import play.api.libs.concurrent._
import play.api.libs.json._
import play.api.libs.ws._
import play.Logger
import akka.actor._
import akka.routing.RoundRobinRouter
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps // for durations
import com.notnoop.apns._

sealed trait PushMessage
case class SendIosBroadcast(app: App, payload: JsObject) extends PushMessage
case class SendIosNotifications(app: App, deviceTokens: List[DeviceToken], payload: JsObject) extends PushMessage
case class StopIosWorkers(app: App) extends PushMessage
case class SendGcmBroadcast(app: App, payload: JsObject) extends PushMessage
case class SendGcmMessage(app: App, registrations: List[Registration], payload: JsObject) extends PushMessage

object Push {

  val system = ActorSystem("PushSystem")
  private val iosDispatcher = system.actorOf(Props(new IosDispatcher()))
  private val gcmDispatcher = system.actorOf(Props(new GcmDispatcher()))

  def sendIosBroadcast(app: App, payload: JsObject) =
    iosDispatcher ! SendIosBroadcast(app, payload)

  def sendIosNotifications(app: App, deviceTokens: List[DeviceToken], payload: JsObject) =
    iosDispatcher ! SendIosNotifications(app, deviceTokens, payload)

  def stopIosWorkers(app: App) =
    iosDispatcher ! StopIosWorkers(app)

  def sendGcmBroadcast(app: App, payload: JsObject) =
    gcmDispatcher ! SendGcmBroadcast(app, payload)

  def sendGcmMessage(app: App, registrations: List[Registration], payload: JsObject) =
    gcmDispatcher ! SendGcmMessage(app, registrations, payload)

}

class IosDispatcher extends Actor {

  var workers = Map[String, ActorRef]()

  def receive = {
    case SendIosBroadcast(app, payload) => {
      val deviceTokens = DeviceToken.findAllByAppKey(app.key)
      workerForApp(app) ! SendIosNotifications(app, deviceTokens, payload)
    }
    case SendIosNotifications(app, deviceTokens, payload) => {
      workerForApp(app) ! SendIosNotifications(app, deviceTokens, payload)
    }
    case StopIosWorkers(app) => {
      workers.get(app.key) foreach { w => context.stop(w) }
      workers -= app.key
    }
  }

  def workerForApp(app: App) = {
    workers.get(app.key) match {
      case Some(worker) => worker
      case None => {
        val worker = context.actorOf(Props(new IosDispatchWorker(app)))
        workers += app.key -> worker
        worker
      }
    }
  }

}

class GcmDispatcher extends Actor {

  val maxMessagesByWorker = 1000

  def receive = {
    case SendGcmBroadcast(app, payload) => {
      val registrations = Registration.findAllByAppKey(app.key)
      self ! SendGcmMessage(app, registrations, payload)
    }
    case SendGcmMessage(app, registrations, payload) => {
      registrations grouped(maxMessagesByWorker) foreach { registrations =>
        val worker = context.actorOf(Props(new GcmDispatchWorker()))
        worker ! SendGcmMessage(app, registrations, payload)
      }
    }
  }

}

class IosDispatchWorker(app: App) extends Actor {

  val builder = APNS.newService.withCert(app.certFile.getAbsolutePath, app.iosCertPassword.getOrElse("")).withAppleDestination(app.appMode == 1).withReconnectPolicy(ReconnectPolicy.Provided.EVERY_HALF_HOUR).asPool(15)
  val service = if (app.debugMode) builder.build else builder.withNoErrorDetection.build
  // TODO: test connection

  def receive = {
    case SendIosNotifications(app, deviceTokens, payload) => {
      val startTime = System.currentTimeMillis
      val stringPayload = Json.stringify(payload)
      deviceTokens foreach { token => service.push(token.value, stringPayload) }
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
  val timeout = 30000
  var backoffDelay = 500
  val maxBackoffDelay = 60000
  val startTime = System.currentTimeMillis
  var finished = false

  def receive = {
    case SendGcmMessage(app, registrations, payload) => {
      val payloadWithRegistrations = payload + ("registration_ids", Json.arr(registrations map (_.value)))
      WS.url(apiEndpoint).withTimeout(timeout).withHeaders(
        "Authorization" -> ("key=" + app.gcmApiKey.getOrElse("")),
        "Content-Type" -> "application/json"
      ) post payloadWithRegistrations map { response =>
        handleResponse(app, registrations, payload, response)
      } extend1 {
        // TODO: retry mechanism based on the exception type (e.g. ConnectException)
        case Thrown(throwable) => {
          Logger.debug(throwable.getClass.getName)
          throwable.printStackTrace
          val log = "An error occured while sending the GCM notifications - please contact the developers (error: " + throwable.getMessage() + ")"
          Event.create(app.key, Event.Severity.ERROR, log)
          if (finished) context.stop(self)
        }
        case Redeemed(value) => {
          if (finished) context.stop(self)
        }
      }
    }
  }

  def handleResponse(app: App, registrations: List[Registration], payload: JsObject, response: Response) = response.status match {
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
        finished = true
      } else {
        val log = "Delivered %d GCM messages (%d successes, %d failures) in %.3f seconds".format(registrations.length, success, failure, elapsed)
        Event.create(app.key, Event.Severity.INFO, log)

        (json \ "results").asOpt[List[Map[String, String]]] match {
          case Some(results: List[Map[String, String]]) => {
            processResultItems(app, registrations, results) match {
              case retryRegistrations if retryRegistrations.length > 0 => Push.sendGcmMessage(app, retryRegistrations, payload)
              case _ => finished = true
            }
          }
          case _ => {
            Event.create(app.key, Event.Severity.ERROR, "Cannot parse response from GCM servers (%s) - please contact the developers")
            finished = true
          }
        }
      }
    }
    case 400 => {
      // Invalid JSON format or invalid fields
      Logger.error("HTTP Status Code 400 received from GCM server (%s)".format(response.body))
      Event.create(app.key, Event.Severity.ERROR, "Invalid request (JSON format or invalid fields) - please contact the developers")
      finished = true
    }
    case 401 => {
      // Authentication error
      Logger.error("HTTP Status Code 401 received from GCM server(%s)".format(response.body))
      Event.create(app.key, Event.Severity.ERROR, "Authentication error - please check the GCM API Key")
      finished = true
    }
    case status if status >= 500 && status < 600 => {
      // Internal server error (500) or Server temporary unavailable (503)
      response.header("Retry-After") map { retryAfterHeader =>
        val Numeric = "\\A\\d+\\Z".r

        retryAfterHeader match {
          case Numeric => {
            val secs = retryAfterHeader.toInt
            val log = "GCM server returned HTTP status code %i with Retry-After - will retry in %i seconds".format(status, secs)
            Event.create(app.key, Event.Severity.ERROR, log)
            retryAfter(secs seconds, app, registrations, payload)
          }
          case _ => {
            DateParser.parseDate(retryAfterHeader) match {
              case Some(date) => {
                val delta = date.getTime - System.currentTimeMillis
                val log = "GCM server returned HTTP status code %i with Retry-After - will retry in %.1f seconds".format(status, delta)
                Event.create(app.key, Event.Severity.ERROR, log)
                retryAfter(delta milliseconds, app, registrations, payload)
              }
              case _ => {
                val log = "GCM server returned HTTP status code %i with an unparsable Retry-After - will retry in %.1f seconds".format(status, backoffDelay.toFloat / 1000)
                Event.create(app.key, Event.Severity.ERROR, log)
                retryWithExponentialBackoff(app, registrations, payload)
              }
            }
          }
        }
      } getOrElse {
        // No Retry-After header
        val log = "GCM server returned HTTP status code %i without Retry-After - will retry in %.1f seconds".format(status, backoffDelay.toFloat / 1000)
        Event.create(app.key, Event.Severity.ERROR, log)
        retryWithExponentialBackoff(app, registrations, payload)
      }
    }
    case _ => {
      // Unknown status code
      Event.create(app.key, Event.Severity.ERROR, "GCM server returned an unknown error - will retry in %.1f seconds".format(backoffDelay))
      retryWithExponentialBackoff(app, registrations, payload)
    }
  }

  def processResultItems(app: App, registrations: List[Registration], resultItems: List[Map[String, String]]) = {
    ((resultItems zip registrations) foldLeft List[Registration]()) { case (acc, (item, reg)) =>
      if (item.get("message_id").isDefined) {
        if (item.get("registration_id").isDefined) {
          Registration.create(app.key, item.get("registration_id").get)  // No duplicates can be created
          reg.delete
        }
        acc
        // TODO: stats (including the delivery time)
      } else {
        item.get("error") match {
          case Some("InvalidRegistration") => reg.delete; acc
          case Some("Unavailable")         => reg :: acc
          case Some("InternalServerError") => reg :: acc
          case Some("NotRegistered")       => reg.delete; acc
          case Some("MismatchSenderId")    => reg.delete; acc
          case Some("MessageTooBig")       => Logger.warn("Received error from GCM (" + app.key + "): Payload is too big"); acc
          case Some("InvalidTtl")          => Logger.warn("Received error from GCM (" + app.key + "): invalid TTL"); acc
          case Some(error)                 => Logger.warn("Received unknown error from GCM (" + app.key + "): " + error); acc
          case None                        => acc
        }
      }
    }
  }

  def retryAfter(delay: FiniteDuration, app: App, registrations: List[Registration], payload: JsObject) =
    Push.system.scheduler.scheduleOnce(delay, self, SendGcmMessage(app, registrations, payload))

  def retryWithExponentialBackoff(app: App, registrations: List[Registration], payload: JsObject) = {
    retryAfter(backoffDelay milliseconds, app, registrations, payload)
    backoffDelay = List(backoffDelay * 2, maxBackoffDelay).min
  }

}

// Partially taken from https://github.com/unfiltered/unfiltered/blob/master/library/src/main/scala/request/headers.scala
object DateParser {

  import java.text.SimpleDateFormat
  import java.util.{ Date, Locale }

  def parseAs(fmt: String)(value: String): Option[Date] =
    try { Some(new SimpleDateFormat(fmt, Locale.US).parse(value)) }
    catch { case _: Throwable => None }

  def RFC1123 = parseAs("EEE, dd MMM yyyy HH:mm:ss z")_

  def parseDate(raw: String) = RFC1123(raw)

}