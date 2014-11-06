import com.typesafe.sbt.packager.Keys._

name := "status-app"

version := "1.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala).enablePlugins(SbtWeb)

scalaVersion := "2.11.2"

scalacOptions ++= List("-feature", "-deprecation")

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk" % "1.8.10.2",
  "com.typesafe.akka" %% "akka-agent" % "2.3.6",
  cache,
  ws,
  "com.gu" %% "play-googleauth" % "0.1.5",
  "org.webjars" % "react" % "0.11.1",
  "org.webjars" % "bootstrap" % "3.2.0",
  "org.webjars" % "d3js" % "3.4.11",
  "org.webjars" % "zeroclipboard" % "2.1.6"
)

riffRaffPackageType := (dist in config("universal")).value

addCommandAlias("play-artifact", "riffRaffArtifact")

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoPackage := "controllers"

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
)

testOptions += Tests.Argument("-o", "-u", s"${(target in Test).value}/test-reports")