package controllers

import play.api.mvc._
import model.{Instance, Cluster}
import lib.{AWSCost, AmazonConnection, Config}
import java.text.DecimalFormat

object Application extends Controller {

  implicit lazy val amazonConnection = new AmazonConnection(Config.credentials, Config.clientConfiguration)

  def index = Action {
    Redirect(routes.Application.stage("PROD"))
  }

  def stage(stage: String) = Action {
    Ok(views.html.index(Cluster.stages, Cluster.findAll.groupBy(_.stage)(stage), AWSCost.totalSunkCost, new DecimalFormat("#,###.00")))
  }

  def instance(id: String) = Action {
    Ok(views.html.instance(Instance.get(id)))
  }

  def es = Action {
    Ok(views.html.elasticsearch())
  }
}
