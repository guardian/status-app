package controllers

import play.api.mvc._
import model.{Instance, Cluster}
import com.amazonaws.auth.BasicAWSCredentials
import lib.{AWSCost, AmazonConnection, Config}
import java.text.DecimalFormat
import java.io.File
import play.api.Configuration
import com.typesafe.config.ConfigFactory

object Application extends Controller {


  implicit lazy val amazonConnection = new AmazonConnection(Config.credentials)

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


  def fileConfig(filePath: String): Configuration = {
    val file = new File(filePath)
    if (!file.exists) throw new Error("Could not find %s" format (filePath))
    Configuration(ConfigFactory.parseFile(file))
  }



}
