package controllers

import play.api._
import play.api.mvc._

import models._
import views._

object DeviceTokens extends Controller with Secured {

  def index(key: String, offset: Int, count: Int) = withAuth { username => implicit request =>
    App.findByKey(key).map { app =>
      val deviceTokens = DeviceToken.findAllByAppKey(key, Some(offset, count + 1))
      Ok(html.device_tokens.index(app, deviceTokens, offset, count))
    } getOrElse NotFound
  }

}