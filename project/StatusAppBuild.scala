import sbt._
import Keys._
import play.Project._

object StatusAppBuild extends Build {

  val awsSdk = "com.amazonaws" % "aws-java-sdk" % "1.4.3"

  val statusAppDependencies = Seq(
    awsSdk,
    "com.typesafe.akka" %% "akka-agent" % "2.1.0"
  )

  lazy val statusApp = play.Project("status-app", "1.0", statusAppDependencies, path = file(".")).settings(
    resolvers ++= Seq(Classpaths.typesafeReleases),
    scalacOptions ++= List("-feature"),

    scalaVersion := "2.10.2"
  )
}
