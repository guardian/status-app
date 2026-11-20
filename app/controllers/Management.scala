package controllers

import com.gu.googleauth.AuthAction
import play.api.mvc._
import java.util.Date

import model.EstateProvider
import org.joda.time.DateTime

class Management(
  estateProvider: EstateProvider,
  authAction: AuthAction[AnyContent],
  controllerComponents: ControllerComponents
) extends AbstractController(controllerComponents) {
  // This endpoint is intentionally public: the load balancer uses it to decide
  // whether an instance should receive traffic.
  def healthcheck = Action {
    estateProvider().lastUpdated match {
      case Some(dt) if dt.isBefore(DateTime.now().minusMinutes(5)) => InternalServerError("Out of date data")
      case _ => Ok("")
    }
  }

  def manifest() = authAction {
    val data = Map(
      "Build" -> BuildInfo.buildNumber,
      "Commit" -> BuildInfo.gitCommitId,
      "Date" -> new Date(BuildInfo.buildTime).toString,
      "Dependencies" -> BuildInfo.libraryDependencies.mkString(", ")
    )

    Ok(data map { case (k, v) => s"$k: $v"} mkString "\n")
  }
}
