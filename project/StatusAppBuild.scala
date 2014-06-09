import sbt._
import sbt.Keys._
import sbtbuildinfo.Plugin._
import PlayArtifact._
import play.Play.autoImport._
import PlayKeys._
import com.typesafe.sbt.web._

object StatusAppBuild extends Build {

  val awsSdk = "com.amazonaws" % "aws-java-sdk" % "1.7.6"

  val statusAppDependencies = Seq(
    awsSdk,
    "com.typesafe.akka" %% "akka-agent" % "2.3.2",
    cache,
    ws,
    "org.webjars" % "react" % "0.10.0",
    "org.webjars" % "bootstrap" % "3.1.1",
    "org.webjars" % "d3js" % "3.4.4-1",
    "org.webjars" % "zeroclipboard" % "1.3.5"
  )

  lazy val statusApp = Project("status-app", file(".")).enablePlugins(play.PlayScala).enablePlugins(SbtWeb)
    .settings(buildInfoSettings: _*)
    .settings(playArtifactDistSettings: _*)
    .settings(

    libraryDependencies ++= statusAppDependencies,

    resolvers ++= Seq(Classpaths.typesafeReleases),

    scalaVersion := "2.11.0",
    scalacOptions ++= List("-feature", "-deprecation"),

    // see https://groups.google.com/forum/#!topic/sbt-dev/YqDzRZohZ_k
    // this enables a better way of tracking dependencies available in sbt 0.13.2 which should mean
    // that the incremental compiler does a better job of just compiling chanages that
    // impact other things
    incOptions := incOptions.value.withNameHashing(nameHashing = true),

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
