package controllers

import play.api.mvc._
import model.{Instance, Cluster}
import lib.{AWSCost, AmazonConnection, Config}
import java.text.DecimalFormat

object Application extends Controller {


  implicit lazy val amazonConnection = new AmazonConnection(Config.credentials, Config.clientConfiguration)

  def index = Action {
    Ok(views.html.index(Cluster.findAll, AWSCost.totalSunkCost, new DecimalFormat("#,###.00")))
    //Ok(views.html.index(Cluster.findAll, 0, new DecimalFormat("#,###.00")))
  }

  def instance(id: String) = Action {
    Ok(views.html.instance(Instance.get(id)))
  }

  def es = Action {
    Ok(views.html.elasticsearch())
  }



}
