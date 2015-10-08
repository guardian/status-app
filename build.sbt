name := "status-app"

version := "1.0"

enablePlugins(PlayScala, SbtWeb, RiffRaffArtifact, BuildInfoPlugin)

scalaVersion := "2.11.6"
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

riffRaffPackageType := (dist in Universal).value

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
