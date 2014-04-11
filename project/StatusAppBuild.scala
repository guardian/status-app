import sbt._
import sbt.Keys._
import play.Project._
import sbtbuildinfo.Plugin._
import PlayArtifact._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.Keys._

object StatusAppBuild extends Build {

  val awsSdk = "com.amazonaws" % "aws-java-sdk" % "1.7.2"

  val statusAppDependencies = Seq(
    awsSdk,
    "com.typesafe.akka" %% "akka-agent" % "2.2.4",
    cache
  )

  lazy val statusApp = play.Project("status-app", "1.0", statusAppDependencies, path = file("."))
    .settings(buildInfoSettings: _*)
    .settings(playArtifactDistSettings: _*)
    .settings(

    resolvers ++= Seq(Classpaths.typesafeReleases),
    scalacOptions ++= List("-feature"),

    scalaVersion := "2.10.3",

    sourceGenerators in Compile <+= buildInfo,
    buildInfoKeys := Seq[BuildInfoKey](
      libraryDependencies in Compile,
      name,
      version,
      BuildInfoKey.constant("buildNumber", Option(System.getenv("BUILD_NUMBER")) orElse
        Option(System.getenv("TRAVIS_BUILD_NUMBER")) getOrElse "DEV"),
      // so this next one is constant to avoid it always recompiling on dev machines.
      // we only really care about build time on teamcity, when a constant based on when
      // it was loaded is just fine
      BuildInfoKey.constant("buildTime", System.currentTimeMillis)
    ),

    magentaPackageName := "status-app",

    buildInfoPackage := "controllers"
  )
}
