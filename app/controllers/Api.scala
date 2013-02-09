package controllers

import play.api._
import play.api.mvc._
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
    // TODO: validate presence of 'aps' key
    request.body.validate((__ \ 'device_tokens).read[List[String]]).fold(
      e => BadRequest("JSON object doesn't contain a 'device_tokens' array"),
      res => {
        res flatMap { tokenValue => DeviceToken.findByAppKeyAndValue(app.key, tokenValue) } match {
          case Nil => BadRequest("No valid and registered device tokens found in 'device_tokens' array")
          case deviceTokens => {
            request.body.transform((__ \ 'device_tokens).json.prune).fold(
              e => BadRequest(e.toString),
              res => {
                models.Push.sendIosNotifications(app, deviceTokens, res)
                Ok
              }
            )
          }
        }
      }
    )
  }

  def pushBroadcast = SecuredApiAction(parse.json) { (request, app) =>
    // TODO: validate presence of 'aps' key
    request.body.transform((__).json.pick[JsObject]).fold(
      e => BadRequest("JSON is not an object"),
      res => {
        models.Push.sendIosBroadcast(app, res)
        Ok
      }
    )
  }

}