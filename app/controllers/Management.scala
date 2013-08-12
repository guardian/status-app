package controllers

import play.api.mvc._
import java.util.Date

object Management extends Controller {
  def healthcheck = Action {
    Ok("")
  }

  def manifest() = Action {
    val data = Map(
      "Build" -> BuildInfo.buildNumber,
      "Date" -> new Date(BuildInfo.buildTime).toString,
      "Dependencies" -> BuildInfo.libraryDependencies.mkString(", ")
    )

    Ok(data map { case (k, v) => s"$k: $v"} mkString "\n")
  }
}
