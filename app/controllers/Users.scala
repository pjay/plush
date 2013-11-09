package controllers

import play.api._
import play.api.data._
import play.api.data.Forms._
import play.api.mvc._

import models._
import views._

object Users extends Controller {

  val loginForm = Form(
    tuple(
      "email" -> text,
      "password" -> text
    ) verifying("Invalid email or password", result => result match {
      case (email, password) => User.authenticate(email, password)
    })
  )

  val userForm = Form(
    tuple(
      "email" -> nonEmptyText,
      "password" -> nonEmptyText,
      "passwordConfirmation" -> nonEmptyText
    ) verifying("Passwords don't match", result => result match {
      case (email, password, passwordConfirmation) => password == passwordConfirmation
    })
  )

  def index = Action {
    Redirect(routes.Users.login)
  }

  def login = Action { implicit request =>
    Ok(html.users.login(loginForm))
  }

  def authenticate = Action { implicit request =>
    loginForm.bindFromRequest.fold(
      formWithErrors => BadRequest(html.users.login(formWithErrors)),
      user => {
        val username = (Security.username -> user._1)
        request.session.get("uriBeforeLogin") map { uri =>
          Redirect(uri).withSession(session - "uriBeforeLogin" + username)
        } getOrElse {
          Redirect(routes.Apps.index).withSession(session + username)
        }
      }
    )
  }

  def logout = Action { implicit request =>
    Redirect(routes.Users.login).withSession(session - Security.username).flashing("success" -> "You are now logged out")
  }

  def add = Action { implicit request =>
    Ok(html.users.add(userForm))
  }

  def create = Action { implicit request =>
    userForm.bindFromRequest.fold(
      formWithErrors => BadRequest(html.users.add(formWithErrors)),
      values => {
        val (email, password, passwordConfirmation) = values
        User.create(email, password) match {
          case true => Redirect(routes.Apps.index).withSession(Security.username -> email)
          case false => InternalServerError
        }
      }
    )
  }

}

trait Secured {

  def username(request: RequestHeader) =
    request.session.get(Security.username)

  def onUnauthorized(request: RequestHeader) =
    Results.Redirect(routes.Users.login).withSession("uriBeforeLogin" -> request.uri).flashing("error" -> "Login required")

  def withAuth(f: => String => Request[AnyContent] => SimpleResult) =
    withAuth[AnyContent](BodyParsers.parse.anyContent)(f)

  def withAuth[A](bp: BodyParser[A])(f: => String => Request[A] => SimpleResult) = {
    Security.Authenticated(username, onUnauthorized) { user =>
      Action[A](bp)(request => f(user)(request))
    }
  }

}