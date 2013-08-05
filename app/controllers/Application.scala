package controllers

import play.api.mvc._
import model.{ASG, Instance}
import lib.{AWSCost, AmazonConnection, Config}
import java.text.DecimalFormat
import scala.concurrent.ExecutionContext

import ExecutionContext.Implicits.global

object Application extends Controller {

  implicit lazy val amazonConnection = new AmazonConnection(Config.credentials, Config.clientConfiguration)

  def index = Action {
    Redirect(routes.Application.stage("PROD"))
  }

  def stage(stage: String) = Action {
    Async {
      def stages(clusters: Seq[ASG]): Seq[String] =
        clusters.map(_.stage).toSet.toSeq

      for {
        clusters <- ASG.all
      } yield {
        Ok(views.html.index(
          stage,
          stages(clusters),
          clusters.groupBy(_.stage)(stage).sortBy(_.appName),
          AWSCost.totalSunkCost,
          new DecimalFormat("#,###.00")
        ))
      }
    }
  }

  def instance(id: String) = Action {
    Async {
      Instance.get(id) map (i => Ok(views.html.instance(i)))
    }
  }

  def es = Action {
    Ok(views.html.elasticsearch())
  }
}
