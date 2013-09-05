package controllers

import play.api.mvc._
import model.{Estate, AWSCost, ASG, Instance}
import lib.{AmazonConnection, Config}
import java.text.DecimalFormat
import scala.concurrent.ExecutionContext

import ExecutionContext.Implicits.global

object Application extends Controller {

  implicit lazy val amazonConnection = new AmazonConnection(Config.credentials, Config.clientConfiguration)
  implicit val moneyFormat = new DecimalFormat("#,###.00")

  def index = Authenticated { implicit req =>
    Estate().stages.headOption map (stage =>
      Redirect(routes.Application.stage(stage))
    ) getOrElse (
      Ok(views.html.loading())
    )
  }

  def stage(stage: String) = Authenticated { implicit req =>

    if (Estate().populated)
      Ok(views.html.index(
        stage,
        Estate(),
        AWSCost.totalSunkCost
      ))
    else
      Ok(views.html.loading())
  }

  def instance(id: String) = Authenticated.async {
    Instance.get(id) map (i => Ok(views.html.instance(i)))
  }

  def es = Authenticated {
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
