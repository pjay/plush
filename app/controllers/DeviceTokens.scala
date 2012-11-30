package controllers

import play.api._
import play.api.mvc._

import models._
import views._

object DeviceTokens extends Controller {

  def index(key: String) = Action { implicit request =>
    App.findByKey(key).map { app =>
      val deviceTokens = DeviceToken.findAllByAppKey(key)
      Ok(html.device_tokens.index(app, deviceTokens))
    } getOrElse NotFound
  }

}