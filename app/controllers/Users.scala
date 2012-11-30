package controllers

import play.api._
import play.api.mvc._

object Users extends Controller {

  def index = Action {
    Redirect(routes.Users.login)
  }
  
  def login = TODO
  
  def authenticate = TODO
  
  def logout = TODO
  
}