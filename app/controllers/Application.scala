package controllers

import play.api.mvc._
import model.{Estate, AWSCost, ASG, Instance}
import lib.{AmazonConnection, Config}
import java.text.DecimalFormat
import scala.concurrent.ExecutionContext

import ExecutionContext.Implicits.global

object Application extends Controller {

  implicit lazy val amazonConnection = new AmazonConnection(Config.credentials, Config.clientConfiguration)

  def index = AuthAction { implicit req =>
    Estate().stages.headOption map (stage =>
      Redirect(routes.Application.stage(stage))
    ) getOrElse (
      Ok(views.html.loading())
    )
  }

  def stage(stage: String) = AuthAction { implicit req =>
    if (Estate().populated)
      Ok(views.html.index(
        stage,
        Estate(),
        AWSCost.totalSunkCost,
        new DecimalFormat("#,###.00")
      ))
    else
      Ok(views.html.loading())
  }

  def instance(id: String) = AuthAction {
    Async {
      Instance.get(id) map (i => Ok(views.html.instance(i)))
    }
  }

  def es = AuthAction {
    val esHost = Estate().values.flatten.filter(_.appName.contains("elasticsearch"))
      .headOption.flatMap(_.members.headOption).map(_.instance.publicDns)
    Ok(views.html.elasticsearch(esHost))
  }

  def void = Action {
    NotFound
  }
  def robots = Action {
    Ok(
      """
        |User-agent: *
        |Disallow: *
      """.stripMargin)
  }
}
