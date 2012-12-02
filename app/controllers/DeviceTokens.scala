package controllers

import play.api._
import play.api.mvc._

import models._
import views._

object DeviceTokens extends Controller with Secured {

  def index(key: String) = withAuth { username => implicit request =>
    App.findByKey(key).map { app =>
      val deviceTokens = DeviceToken.findAllByAppKey(key)
      Ok(html.device_tokens.index(app, deviceTokens))
    } getOrElse NotFound
  }

}