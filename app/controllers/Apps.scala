package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.libs._

import models._
import views._

object Apps extends Controller with Secured {

  // TODO: certificate validation
  val appForm = Form(
    tuple(
      "name" -> nonEmptyText,
      "appMode" -> number(min=0, max=1),
      "debugMode" -> boolean,
      "iosCertPassword" -> optional(text),
      "gcmApiKey" -> optional(text)
    )
  )

  def index = withAuth { username => implicit request =>
    Ok(html.apps.index(App.all))
  }

  def show(key: String) = withAuth { username => implicit request =>
    App.findByKey(key) map { app =>
      Ok(html.apps.show(app))
    } getOrElse NotFound
  }

  def add = withAuth { username => implicit request =>
    Ok(html.apps.add(appForm))
  }

  def create = withAuth(parse.multipartFormData) { username => implicit request =>
    appForm.bindFromRequest.fold(
      formWithErrors => BadRequest(html.apps.add(formWithErrors)),
      values => {
        val (name, appMode, debugMode, iosCertPassword, gcmApiKey) = values
        // TODO: use real user ID once auth is done
        App.create(1, name, appMode, debugMode, iosCertPassword, gcmApiKey) match {
          case Some(key) => { App.findByKey(key) map { app =>
              moveCertificate(app)
              Redirect(routes.Apps.show(app.key)).flashing("success" -> "Application successfully created")
            }
          } getOrElse InternalServerError
          case _ => InternalServerError
        }
      }
    )
  }

  def edit(key: String) = withAuth { username => implicit request =>
    App.findByKey(key) map { app =>
      val values = (app.name, app.appMode, app.debugMode, app.iosCertPassword, app.gcmApiKey)
      Ok(html.apps.edit(app, appForm.fill(values)))
    } getOrElse NotFound
  }

  def update(key: String) = withAuth(parse.multipartFormData) { username => implicit request =>
    App.findByKey(key) map { app =>
      appForm.bindFromRequest.fold(
        formWithErrors => BadRequest(html.apps.edit(app, formWithErrors)),
        values => {
          val (name, appMode, debugMode, iosCertPassword, gcmApiKey) = values
          val attrs = Map("name" -> name, "appMode" -> appMode, "debugMode" -> debugMode, "iosCertPassword" -> iosCertPassword, "gcmApiKey" -> gcmApiKey)
          app.update(attrs) match {
            case true => {
              moveCertificate(app)
              // Stop the iOS workers of the modified app if any of the following conditions occurs:
              // * A new certificate has been uploaded
              // * The certificate password has changed
              // * The app mode has changed
              // * The debug mode has changed
              if (request.body.file("certificate").isDefined ||
                  attrs.get("iosCertPassword").get != app.iosCertPassword ||
                  attrs.get("appMode").get != app.appMode ||
                  attrs.get("debugMode").get != app.debugMode) {
                models.Push.stopIosWorkers(app)
              }
              Redirect(routes.Apps.show(app.key)).flashing("success" -> "Application successfully updated")
            }
            case false => InternalServerError
          }
        }
      )
    } getOrElse NotFound
  }

  def delete(key: String) = withAuth { username => implicit request =>
    App.findByKey(key) map { app =>
      app.delete match {
        case true => Redirect(routes.Apps.index).flashing("success" -> "Application successfully deleted")
        case false => InternalServerError
      }
    } getOrElse NotFound
  }

  private def moveCertificate(app: App)(implicit request: Request[MultipartFormData[Files.TemporaryFile]]) =
    request.body.file("certificate").map { certificate =>
      certificate.ref.moveTo(app.certFile, true)
    }

}