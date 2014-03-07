package controllers

import play.api.mvc._
import model._
import java.text.DecimalFormat
import play.api.libs.json.{JsString, Json, Writes}
import lib.UptimeDisplay
import com.amazonaws.services.cloudwatch.model.Datapoint

object Application extends Controller {

  implicit val moneyFormat = new DecimalFormat("#,###")

  implicit val datapointWrites = new Writes[Datapoint]{
    override def writes(d: Datapoint) = Json.obj(
      "x" -> d.getTimestamp.getTime,
      "y" -> d.getAverage.toInt
    )
  }

  def index = Authenticated { implicit req =>
    Estate().stageNames.headOption map (stage =>
      Redirect(routes.Application.stage(stage))
    ) getOrElse (
      Ok(views.html.loading())
    )
  }

  def stageJson(stage: String) = Authenticated {
    import ASG.writes
    Ok(Json.toJson(
      Estate()(stage)
    ))
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

  def queues = Authenticated {
    implicit val queueWrites = Json.writes[Queue]

    Ok(Json.toJson(
      Estate().queues
    ))
  }

  def instance(id: String) = Authenticated { implicit req =>
    val instance = for {
      asg <- Estate().asgs
      member <- asg.members if member.id == id
    } yield member
    instance.headOption map (i => Ok(views.html.instance(i.instance))) getOrElse NotFound
  }

  def es(name: String) = Authenticated { implicit req =>
    (for {
      asg <- Estate().asgs if asg.name == name
    } yield asg match {
      case a: ElasticSearchASG => Ok(views.html.elasticsearch(a))
      case _ => NotFound
    }).headOption getOrElse Ok(views.html.loading())
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
