package controllers

import play.api.mvc._
import model._
import java.text.DecimalFormat

import play.api.libs.json.{Json, Writes}
import com.amazonaws.services.cloudwatch.model.Datapoint
import com.gu.googleauth._
import scala.util.Random
import play.api.libs.ws.WSClient

import scala.concurrent.Future

class Application(
  wsClient:WSClient,
  authAction: AuthAction[AnyContent],
  awsCost: AWSCost,
  estateProvider: EstateProvider,
  controllerComponents: ControllerComponents)
  extends AbstractController(controllerComponents) {

  implicit val moneyFormat = new DecimalFormat("#,###")

  implicit val datapointWrites: Writes[Datapoint] = new Writes[Datapoint] {
    override def writes(d: Datapoint) = Json.obj(
      "x" -> d.getTimestamp.getTime,
      "y" -> d.getAverage.toInt
    )
  }

  def index = authAction { implicit req =>
    estateProvider().stageNames.headOption map (stage =>
      Redirect(routes.Application.stage(stage))
      ) getOrElse Ok(views.html.loading(estateProvider))

  }

  def stageJson(stage: String) = authAction {
    import ASG.writes
    Ok(Json.toJson(
      estateProvider()(stage)
    ))
  }

  def stage(stage: String) = authAction { implicit req =>
    if (estateProvider().populated)
      Ok(views.html.index(
        stage,
        awsCost.totalSunkCost,
        estateProvider
      ))
    else
      Ok(views.html.loading(estateProvider))
  }

  def queues = authAction {
    implicit val queueWrites = Json.writes[Queue]

    Ok(Json.toJson(
      estateProvider().queues
    ))
  }

  def instance(id: String) = authAction { implicit req =>
    val instance = for {
      asg <- estateProvider().asgs
      member <- asg.members if member.id == id
    } yield member
    instance.headOption map (i => Ok(views.html.instance(i.instance, estateProvider))) getOrElse NotFound(views.html.instanceNotFound(id, estateProvider))
  }

  def es(name: String) = authAction.async { implicit req =>
    import scala.concurrent.ExecutionContext.Implicits.global
    (for {
      asg <- estateProvider().asgs if asg.name.exists(_ == name)
      stats <- Random.shuffle(asg.members).headOption map (m => wsClient.url(s"http://${m.instance.publicDns}:9200/_nodes/stats?groups=_all").get())
    } yield stats map { r =>
      Ok(views.html.elasticsearch(ElasticsearchStatsGroups.parse(r.json), estateProvider))
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
