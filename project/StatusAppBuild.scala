import com.typesafe.sbt.web.SbtWeb
import sbt._
import sbt.Keys._
import play.Project._
import sbtbuildinfo.Plugin._
import PlayArtifact._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.Keys._

object StatusAppBuild extends Build {

  val awsSdk = "com.amazonaws" % "aws-java-sdk" % "1.7.6"

  val statusAppDependencies = Seq(
    awsSdk,
    "com.typesafe.akka" %% "akka-agent" % "2.2.4",
    cache,
    ws
  )

  lazy val statusApp = Project("status-app", file(".")).addPlugins(play.PlayScala).addPlugins(SbtWeb)
    .settings(buildInfoSettings: _*)
    .settings(playArtifactDistSettings: _*)
    .settings(

    libraryDependencies ++= statusAppDependencies,

    resolvers ++= Seq(Classpaths.typesafeReleases),

    scalaVersion := "2.10.4",
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
