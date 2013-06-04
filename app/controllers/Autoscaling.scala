package controllers

import play.api.mvc.{Action, Controller}
import model.ASG

object Autoscaling extends Controller {

  implicit lazy val amazonConnection = Application.amazonConnection

  def suspend(groupName: String) = Action {
    ASG.suspend(groupName)
    Redirect(routes.Application.index())
  }

  def resume(groupName: String) = Action {
    ASG.resume(groupName)
    Redirect(routes.Application.index())
  }

  def desiredCapacity(groupName: String, newSize: Int) = Action {
    ASG.desiredCapacity(groupName, newSize)
    Redirect(routes.Application.index())
  }

}
