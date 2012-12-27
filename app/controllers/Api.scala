package controllers

import play.api._
import play.api.mvc._
import play.api.libs.json.JsValue

import models._

object Api extends Controller {

  def ApiAction[A](bp: BodyParser[A])(f: (Request[A], App) => Result): Action[A] = {
    // TODO: flatten
    Action(bp) { request =>
      request.headers.get(AUTHORIZATION) match {
        case Some(header) => {
          header.split(" ", 2).toList match {
            case "Basic" :: encodedAuth :: Nil => {
              val decodedAuth = new String(new sun.misc.BASE64Decoder().decodeBuffer(encodedAuth))
              decodedAuth.split(":").toList match {
                case key :: secret :: Nil => {
                  App.findByKey(key) map { app =>
                    if (app.secret == secret) f(request, app) else Unauthorized
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

  def createDeviceToken(value: String) = ApiAction(parse.anyContent) { (request, app) =>
    // TODO: 201 Created for first registrations, 200 OK otherwise
    value.length match {
      case 64 => DeviceToken.create(app.key, value).map(dt => Created).getOrElse(InternalServerError)
      case _ => BadRequest("Device token should be 64 characters long")
    }
  }

  def deleteDeviceToken(value: String) = ApiAction(parse.anyContent) { (request, app) =>
    if (DeviceToken.delete(app.key, value)) NoContent else NotFound
  }

  def createRegistration(value: String) = ApiAction(parse.anyContent) { (request, app) =>
    // TODO: 201 Created for first registrations, 200 OK otherwise
    Registration.create(app.key, value).map(dt => Created).getOrElse(InternalServerError)
  }

  def deleteRegistration(value: String) = ApiAction(parse.anyContent) { (request, app) =>
    if (Registration.delete(app.key, value)) NoContent else NotFound
  }

}