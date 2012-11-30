package controllers

import play.api._
import play.api.mvc._

import models._
import views._

object Registrations extends Controller {

  def index(appKey: String) = Action { implicit request =>
    App.findByKey(appKey).map { app =>
      val registrations = Registration.findAllByAppKey(appKey)
      Ok(html.registrations.index(app, registrations))
    } getOrElse NotFound
  }

}