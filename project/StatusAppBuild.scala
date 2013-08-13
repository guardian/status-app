import sbt._
import Keys._
import play.Project._
import sbtbuildinfo.Plugin._

object StatusAppBuild extends Build {

  val awsSdk = "com.amazonaws" % "aws-java-sdk" % "1.4.3"

  val statusAppDependencies = Seq(
    awsSdk,
    "com.typesafe.akka" %% "akka-agent" % "2.1.0"
  )

  lazy val statusApp = play.Project("status-app", "1.0", statusAppDependencies, path = file("."))
    .settings(buildInfoSettings: _*).settings(

    resolvers ++= Seq(Classpaths.typesafeReleases),
    scalacOptions ++= List("-feature"),

    scalaVersion := "2.10.2",

    sourceGenerators in Compile <+= buildInfo,
    buildInfoKeys := Seq[BuildInfoKey](
      libraryDependencies in Compile,
      name,
      version,
      BuildInfoKey.constant("buildNumber", Option(System.getenv("BUILD_NUMBER")) getOrElse "DEV"),
      // so this next one is constant to avoid it always recompiling on dev machines.
      // we only really care about build time on teamcity, when a constant based on when
      // it was loaded is just fine
      BuildInfoKey.constant("buildTime", System.currentTimeMillis)
    ),
    buildInfoPackage := "controllers"
  )
}
