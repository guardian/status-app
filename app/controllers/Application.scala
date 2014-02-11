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

  def asg(asg: String) = Authenticated { implicit req =>
    implicit val memberWrites = new Writes[ClusterMember] {
      def writes(m: ClusterMember) = Json.obj(
        "id" -> m.id,
        "goodorbad" -> m.goodorbad,
        "lifecycleState" -> m.lifecycleState,
        "state" -> m.state,
        "description" -> m.description,
        "uptime" -> m.instance.uptime,
        "version" -> JsString(m.instance.version.getOrElse("?"))
      )
    }

    implicit val scalingActionWrites = new Writes[ScalingAction] {
      override def writes(a: ScalingAction) = Json.obj(
        "age" -> a.age,
        "cause" -> a.cause
      )
    }

    implicit val elbWrites = new Writes[ELB] {
      override def writes(elb: ELB) = Json.obj(
        "name" -> elb.name,
        "latency" -> elb.latency.map(d => new Datapoint().withTimestamp(d.getTimestamp).withAverage(d.getAverage * 1000)),
        "active" -> elb.active
      )
    }

    implicit val asgWrites = new Writes[ASG] {
      def writes(asg: ASG) = Json.obj(
        "appName" -> asg.appName,
        "members" -> asg.members,
        "recentActivity" -> asg.recentActivity,
        "averageCPU" -> asg.averageCPU,
        "elb" -> asg.elb
      )
    }

    if (req.contentType == Some("text/javascript")) {
      if (Estate().populated)
        Ok(Json.toJson(
          Estate().asgs.find(_.name == asg).get
        ))
      else
        Ok("")
    } else {
      if (Estate().populated)
        Ok(views.html.snippets.renderASG(
          Estate().asgs.find(_.name == asg).get
        ))
      else
        Ok(views.html.loading())
    }
  }

  def queue(queue: String) = Authenticated { implicit req =>
    implicit val queueWrites = Json.writes[Queue]

    if (req.contentType == Some("text/javascript")) {
      if (Estate().populated)
        Ok(Json.toJson(
          Estate().queues.find(_.name == queue).get
        ))
      else
        Ok("")
    } else {
      if (Estate().populated)
        Ok(views.html.snippets.queue(
          Estate().queues.find(_.name == queue).get
        ))
      else
        Ok(views.html.loading())
    }
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
