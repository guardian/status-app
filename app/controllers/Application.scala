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
  getEstate: GetEstate,
  googleAuthConfig: GoogleAuthConfig,
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
    getEstate().stageNames.headOption map (stage =>
      Redirect(routes.Application.stage(stage))
      ) getOrElse Ok(views.html.loading(getEstate))

  }

  def stageJson(stage: String) = authAction {
    import ASG.writes
    Ok(Json.toJson(
      getEstate()(stage)
    ))
  }

  def stage(stage: String) = authAction { implicit req =>
    if (getEstate().populated)
      Ok(views.html.index(
        stage,
        awsCost.totalSunkCost,
        getEstate
      ))
    else
      Ok(views.html.loading(getEstate))
  }

  def queues = authAction {
    implicit val queueWrites = Json.writes[Queue]

    Ok(Json.toJson(
      getEstate().queues
    ))
  }

  def instance(id: String) = authAction { implicit req =>
    val instance = for {
      asg <- getEstate().asgs
      member <- asg.members if member.id == id
    } yield member
    instance.headOption map (i => Ok(views.html.instance(i.instance, getEstate))) getOrElse NotFound(views.html.instanceNotFound(id, getEstate))
  }

  def es(name: String) = authAction.async { implicit req =>
    import scala.concurrent.ExecutionContext.Implicits.global
    (for {
      asg <- getEstate().asgs if asg.name == name
      stats <- Random.shuffle(asg.members).headOption map (m => wsClient.url(s"http://${m.instance.publicDns}:9200/_nodes/stats?groups=_all").get())
    } yield stats map { r =>
      Ok(views.html.elasticsearch(ElasticsearchStatsGroups.parse(r.json), getEstate))
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
