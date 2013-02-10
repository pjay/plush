package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.libs.json._

import models._
import views._

object Push extends Controller with Secured {

  val iosBroadcastForm = Form(
    tuple(
      "badge" -> optional(number(min = 0)),
      "alert" -> optional(text),
      "sound" -> optional(text)
    ) verifying ("All notification fields are empty", fields => fields match {
      case (None, None, None) => false
      case _ => true
    }))

  val gcmBroadcastForm = Form(
    tuple(
      "extra_key" -> nonEmptyText,
      "extra_value" -> nonEmptyText
    ))

  def broadcast(appKey: String) = withAuth { username => implicit request =>
    App.findByKey(appKey).map { app =>
      Ok(html.push.broadcast(app, iosBroadcastForm, gcmBroadcastForm))
    } getOrElse NotFound
  }

  def sendBroadcast(appKey: String) = withAuth(parse.urlFormEncoded) { username => implicit request =>
    App.findByKey(appKey).map { app =>
      request.body.get("type") match {
        case Some(Seq("ios")) => {
          iosBroadcastForm.bindFromRequest.fold(
            formWithErrors => BadRequest(html.push.broadcast(app, formWithErrors, gcmBroadcastForm)),
            values => {
              val (badge, alert, sound) = values
              val payloadMap = Json.obj("badge" -> badge, "alert" -> alert, "sound" -> sound).value filter { pair => pair._2 != JsNull }
              val payload = JsObject(payloadMap.toSeq)
              models.Push.sendIosBroadcast(app, payload)
              Redirect(routes.Apps.show(appKey)).flashing("success" -> "The broadcast notification is being sent")
            }
          )
        }
        case Some(Seq("gcm")) => {
          gcmBroadcastForm.bindFromRequest.fold(
            formWithErrors => BadRequest(html.push.broadcast(app, iosBroadcastForm, formWithErrors)),
            values => {
              val (extraKey, extraValue) = values
              models.Push.sendGcmBroadcast(app, Json.obj("data" -> Json.obj(extraKey -> extraValue)))
              Redirect(routes.Apps.show(appKey)).flashing("success" -> "The broadcast notification is being sent")
            }
          )
        }
        case _ => BadRequest("Invalid type parameter")
      }
    } getOrElse NotFound
  }

}
