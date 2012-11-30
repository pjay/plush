package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._

import models._
import views._

object Push extends Controller {

  val iosBroadcastForm = Form(
    tuple(
      "badge" -> optional(number(min = 0)),
      "alert" -> optional(text),
      "sound" -> optional(text)
    ) verifying ("All notification fields are empty", fields => fields match {
      case (Some(b), _, _) if b > 0 => true
      case (_, Some(a), _) => true
      case (_, _, Some(s)) => true
      case _ => false
    }))

  val gcmBroadcastForm = Form(
    tuple(
      "extra_key" -> nonEmptyText,
      "extra_value" -> nonEmptyText
    ))

  def broadcast(appKey: String) = Action { implicit request =>
    App.findByKey(appKey).map { app =>
      Ok(html.push.broadcast(app, iosBroadcastForm, gcmBroadcastForm))
    } getOrElse NotFound
  }

  def sendBroadcast(appKey: String) = Action(parse.urlFormEncoded) { implicit request =>
    App.findByKey(appKey).map { app =>
      request.body.get("type") match {
        case Some(Seq("ios")) => {
          iosBroadcastForm.bindFromRequest.fold(
            formWithErrors => BadRequest(html.push.broadcast(app, formWithErrors, gcmBroadcastForm)),
            values => {
              val (badge, alert, sound) = values
              val aps = Map("badge" -> badge, "alert" -> alert, "sound" -> sound) filter { case (k, v) => v.isDefined }
              val payload = Map("aps" -> (aps map { case (k, Some(v)) => k -> v; case (k, v) => k -> v }))
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
              models.Push.sendGcmBroadcast(app, Map("data" -> Map(extraKey -> extraValue)))
              Redirect(routes.Apps.show(appKey)).flashing("success" -> "The broadcast notification is being sent")
            }
          )
        }
        case _ => BadRequest("Invalid type parameter")
      }
    } getOrElse NotFound
  }

}
