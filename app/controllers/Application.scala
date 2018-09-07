package controllers

import play.api.mvc._
import model._
import java.text.DecimalFormat
import play.api.libs.json.{JsString, Json, Writes}
import com.amazonaws.services.cloudwatch.model.Datapoint
import scala.util.Random
import play.api.libs.ws.WS
import scala.concurrent.Future

object Application extends Controller with AuthActions {

  implicit val moneyFormat = new DecimalFormat("#,###")

  implicit val datapointWrites = new Writes[Datapoint]{
    override def writes(d: Datapoint) = Json.obj(
      "x" -> d.getTimestamp.getTime,
      "y" -> d.getAverage.toInt
    )
  }

  def index = AuthAction { implicit req =>
    Estate().stageNames.headOption map (stage =>
      Redirect(routes.Application.stage(stage))
    ) getOrElse (
      Ok(views.html.loading())
    )
  }

  def stageJson(stage: String) = AuthAction {
    import ASG.writes
    Ok(Json.toJson(
      Estate()(stage)
    ))
  }

  def stage(stage: String) = AuthAction { implicit req =>
    if (Estate().populated)
      Ok(views.html.index(
        stage,
        Estate(),
        AWSCost.totalSunkCost
      ))
    else
      Ok(views.html.loading())
  }

  def queues = AuthAction {
    implicit val queueWrites = Json.writes[Queue]

    Ok(Json.toJson(
      Estate().queues
    ))
  }

  def instance(id: String) = AuthAction { implicit req =>
    val instance = for {
      asg <- Estate().asgs
      member <- asg.members if member.id == id
    } yield member
    instance.headOption map (i => Ok(views.html.instance(i.instance))) getOrElse NotFound(views.html.instanceNotFound(id))
  }

  def es(name: String) = AuthAction.async { implicit req =>
    import play.api.Play.current
    import scala.concurrent.ExecutionContext.Implicits.global
    (for {
      asg <- Estate().asgs if asg.name == name
      stats <- Random.shuffle(asg.members).headOption map (m => WS.url(s"http://${m.instance.publicDns}:9200/_nodes/stats?groups=_all").get())
    } yield stats map { r =>
      Ok(views.html.elasticsearch(ElasticsearchStatsGroups.parse(r.json)))
    }).headOption.getOrElse(Future.successful(NotFound))
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
