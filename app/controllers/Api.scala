package controllers

import play.api._
import play.api.mvc._
import play.api.libs.functional.syntax._
import play.api.libs.json._

import models._

object Api extends Controller {

  def BaseApiAction[A](bp: BodyParser[A], securityCheck: (App, String) => Boolean)(f: (Request[A], App) => Result): Action[A] = {
    Action(bp) { request =>
      request.headers.get(AUTHORIZATION) match {
        case Some(header) => {
          header.split(" ", 2).toList match {
            case "Basic" :: encodedAuth :: Nil => {
              val decodedAuth = new String(new sun.misc.BASE64Decoder().decodeBuffer(encodedAuth))
              decodedAuth.split(":").toList match {
                case key :: secret :: Nil => {
                  App.findByKey(key) map { app =>
                    if (securityCheck(app, secret)) f(request, app) else Unauthorized
                  } getOrElse Unauthorized
                }
                case _ => Unauthorized
              }
            }
            case _ => Unauthorized
          }
        }
        case None => Unauthorized
      }
    }
  }

  def SecuredApiAction[A](bp: BodyParser[A])(f: (Request[A], App) => Result): Action[A] =
    BaseApiAction(bp, (app, secret) => app.masterSecret == secret)(f)

  def UnsecuredApiAction[A](bp: BodyParser[A])(f: (Request[A], App) => Result): Action[A] =
    BaseApiAction(bp, (app, secret) => app.secret == secret)(f)

  def createDeviceToken(value: String) = UnsecuredApiAction(parse.anyContent) { (request, app) =>
    // TODO: 201 Created for first registrations, 200 OK otherwise
    value.length match {
      case 64 => DeviceToken.create(app.key, value).map(dt => Created).getOrElse(InternalServerError)
      case _ => BadRequest("Device token should be 64 characters long")
    }
  }

  def deleteDeviceToken(value: String) = UnsecuredApiAction(parse.anyContent) { (request, app) =>
    if (DeviceToken.delete(app.key, value)) NoContent else NotFound
  }

  def createRegistration(value: String) = UnsecuredApiAction(parse.anyContent) { (request, app) =>
    // TODO: 201 Created for first registrations, 200 OK otherwise
    Registration.create(app.key, value).map(dt => Created).getOrElse(InternalServerError)
  }

  def deleteRegistration(value: String) = UnsecuredApiAction(parse.anyContent) { (request, app) =>
    if (Registration.delete(app.key, value)) NoContent else NotFound
  }

  def push = SecuredApiAction(parse.json) { (request, app) =>
    val reads = (
      (__).read[JsObject] and
      (__ \ 'device_tokens).readNullable[List[String]] and
      (__ \ 'registration_ids).readNullable[List[String]] and
      (__ \ 'aps).readNullable[JsObject]
    ).tupled

    request.body.validate(reads).fold(
      e => BadRequest("Invalid JSON"),
      res => {
        val (obj, deviceTokensOpt, registrationsOpt, apsOpt) = res

        if (deviceTokensOpt.isDefined && apsOpt.isDefined) {
          val deviceTokens = deviceTokensOpt map (_ flatMap (DeviceToken.findByAppKeyAndValue(app.key, _))) getOrElse List()
          if (deviceTokens.nonEmpty) {
            models.Push.sendIosNotifications(app, deviceTokens, obj - "device_tokens")
            Ok
          } else {
            BadRequest("List of device tokens is empty or contains only invalid values")
          }
        } else if (registrationsOpt.isDefined) {
          val registrations = registrationsOpt map (_ flatMap (Registration.findByAppKeyAndValue(app.key, _))) getOrElse List()
          if (registrations.nonEmpty) {
            models.Push.sendGcmMessage(app, registrations, obj - "registration_ids")
            Ok
          } else {
            BadRequest("List of registration IDs is empty or contains only invalid values")
          }
        } else {
          BadRequest("Invalid JSON key/value pairs")
        }
      }
    )
  }

  def pushBroadcast = SecuredApiAction(parse.json) { (request, app) =>
    val reads = (
      (__).read[JsObject] and
      (__ \ 'aps).readNullable[JsObject]
    ).tupled

    request.body.validate(reads).fold(
      e => BadRequest("JSON is not an object"),
      res => {
        val (obj, aps) = res

        if (aps.isDefined) {
          models.Push.sendIosBroadcast(app, obj)
          Ok
        } else {
          models.Push.sendGcmBroadcast(app, obj)
          Ok
        }
      }
    )
  }

}