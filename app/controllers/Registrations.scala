package controllers

import play.api._
import play.api.mvc._

import models._
import views._

object Registrations extends Controller with Secured {

  def index(appKey: String, offset: Int, count: Int) = withAuth { username => implicit request =>
    App.findByKey(appKey).map { app =>
      val registrations = Registration.findAllByAppKey(appKey, Some(offset, count + 1))
      Ok(html.registrations.index(app, registrations, offset, count))
    } getOrElse NotFound
  }

}