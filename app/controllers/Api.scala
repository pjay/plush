package controllers

import play.api._
import play.api.mvc._
import play.api.libs.json.JsValue

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

}