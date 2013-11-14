package controllers

import play.api.mvc._
import model._
import java.text.DecimalFormat

object Application extends Controller {

  implicit val moneyFormat = new DecimalFormat("#,###.00")

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
    if (Estate().populated)
      Ok(views.html.snippets.renderASG(
        Estate().asgs.find(_.name == asg).get
      ))
    else
      Ok(views.html.loading())
  }

  def queue(queue: String) = Authenticated { implicit req =>
    if (Estate().populated)
      Ok(views.html.snippets.queue(
        Estate().queues.find(_.name == queue).get
      ))
    else
      Ok(views.html.loading())
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
