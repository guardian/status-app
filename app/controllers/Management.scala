package controllers

import play.api.mvc._
import java.util.Date

import model.GetEstate
import org.joda.time.DateTime

class Management(getEstate: GetEstate) extends ControllerHelpers {
  def healthcheck = Action {
    getEstate().lastUpdated match {
      case Some(dt) if dt.isBefore(DateTime.now().minusMinutes(5)) => InternalServerError("Out of date data")
      case _ => Ok("")
    }
  }

  def manifest() = Action {
    val data = Map(
      "Build" -> BuildInfo.buildNumber,
      "Commit" -> BuildInfo.gitCommitId,
      "Date" -> new Date(BuildInfo.buildTime).toString,
      "Dependencies" -> BuildInfo.libraryDependencies.mkString(", ")
    )

    Ok(data map { case (k, v) => s"$k: $v"} mkString "\n")
  }
}
