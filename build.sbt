name := "status-app"

version := "1.0"

enablePlugins(PlayScala, SbtWeb, RiffRaffArtifact, BuildInfoPlugin, JDebPackaging)

scalaVersion := "2.11.7"
scalacOptions ++= List("-feature", "-deprecation")

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk" % "1.9.34",
  "com.typesafe.akka" %% "akka-agent" % "2.3.10",
  specs2 % Test,
  cache,
  ws,
  "com.gu" %% "play-googleauth" % "0.3.0",
  "org.webjars.bower" % "react" % "0.13.3",
  "org.webjars" % "bootstrap" % "3.3.4",
  "org.webjars" % "d3js" % "3.5.5",
  "org.webjars" % "zeroclipboard" % "2.2.0"
)

maintainer := "Phil Wills <philip.wills@theguardian.com>"
packageSummary := "AWS status web-app"
packageDescription := """Web app that shows an overview of the status of autoscaling groups in an AWS account"""
debianPackageDependencies := Seq("openjdk-8-jre-headless")

javaOptions in Universal ++= Seq(
  "-Dpidfile.path=/dev/null",
  "-J-XX:MaxRAMFraction=2",
  "-J-XX:InitialRAMFraction=2",
  "-J-XX:MaxMetaspaceSize=500m",
  "-J-XX:+PrintGCDetails",
  "-J-XX:+PrintGCDateStamps",
  s"-J-Xloggc:/var/log/${packageName.value}/gc.log"
)

import com.typesafe.sbt.packager.archetypes.ServerLoader.Systemd
serverLoading in Debian := Systemd
riffRaffPackageType := (packageBin in Debian).value

riffRaffBuildIdentifier := env("TRAVIS_BUILD_NUMBER").getOrElse("DEV")
riffRaffUploadArtifactBucket := "riffraff-artifact"
riffRaffUploadManifestBucket := "riffraff-builds"

buildInfoPackage := "controllers"
def env(key: String): Option[String] = Option(System.getenv(key))
buildInfoKeys := Seq[BuildInfoKey](
  libraryDependencies in Compile,
  name,
  version,
  BuildInfoKey.constant("buildNumber", env("BUILD_NUMBER") orElse env("TRAVIS_BUILD_NUMBER") getOrElse "DEV"),
  // so this next one is constant to avoid it always recompiling on dev machines.
  // we only really care about build time on teamcity, when a constant based on when
  // it was loaded is just fine
  BuildInfoKey.constant("buildTime", System.currentTimeMillis)
)

testListeners += new JUnitXmlTestsListener(
  env("CI_REPORTS").getOrElse(s"${baseDirectory.value}/shippable/testresults"))

// see http://www.scala-sbt.org/0.13/docs/Cached-Resolution.html
updateOptions := updateOptions.value.withCachedResolution(true)
