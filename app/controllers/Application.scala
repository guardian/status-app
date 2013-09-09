package controllers

import play.api.mvc._
import model._
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

  def es(name: String) = Authenticated {
    val asg = Estate().values.flatten.find(_.name == name)
    val esHost = asg.flatMap(_.members.headOption).map(_.instance.publicDns)
    asg map {
      case a: ElasticSearchASG => Ok(views.html.elasticsearch(a, esHost))
      case _ => NotFound
    } getOrElse Ok(views.html.loading())
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
