name := "status-app"

version := "1.0"
import com.gu.riffraff.artifact.BuildInfo
import com.typesafe.sbt.packager.archetypes.systemloader.SystemdPlugin
import com.typesafe.sbt.packager.archetypes.systemloader.ServerLoader.Systemd

enablePlugins(PlayScala, SbtWeb, RiffRaffArtifact, BuildInfoPlugin, JDebPackaging, SystemdPlugin)

scalaVersion := "2.13.11"
scalacOptions ++= List("-feature", "-deprecation")

val jacksonVersion = "2.15.2"

// Until all dependencies are on scala-java8-compat v1.x, this avoids unnecessary fatal eviction errors
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-java8-compat" % VersionScheme.Always

libraryDependencies ++= Seq(
  specs2 % Test,
  ehcache,
  ws,
  "com.gu.play-googleauth" %% "play-v28" % "2.2.7",
  "org.webjars.bower" % "react" % "0.14.9",
  "org.webjars" % "bootstrap" % "5.3.0",
  "org.webjars" % "d3js" % "6.7.0",
  "org.webjars" % "zeroclipboard" % "2.2.0",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.15.2",
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor" % jacksonVersion,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % jacksonVersion,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion
) ++ Seq(
  "dynamodb", "ec2", "elasticloadbalancing", "s3", "autoscaling", "cloudwatch", "sqs"
).map(artifact => "com.amazonaws" % s"aws-java-sdk-$artifact" % "1.12.122")

maintainer := "Paul Brown <paul.brown@theguardian.com>"
packageSummary := "AWS status web-app"
packageDescription := """Web app that shows an overview of the status of autoscaling groups in an AWS account"""

Universal / javaOptions ++= Seq(
  "-Dpidfile.path=/dev/null",
  "-J-XX:MaxRAMPercentage=50",
  "-J-XX:InitialRAMPercentage=50",
  "-J-XX:MaxMetaspaceSize=500m",
  s"-J-Xloggc:/var/log/${packageName.value}/gc.log"
)
Debian / serverLoading := Some(Systemd)
riffRaffPackageType := (Debian / packageBin).value
riffRaffUploadArtifactBucket := Some("riffraff-artifact")
riffRaffUploadManifestBucket := Some("riffraff-builds")

buildInfoPackage := "controllers"
def env(key: String): Option[String] = Option(System.getenv(key))
buildInfoKeys := {
  lazy val buildInfo = BuildInfo(baseDirectory.value)
  Seq[BuildInfoKey](
    Compile / libraryDependencies,
    name,
    version,
    "buildNumber" -> buildInfo.buildIdentifier,
    "gitCommitId" -> buildInfo.revision,
    // so this next one is constant to avoid it always recompiling on dev machines.
    // we only really care about build time on teamcity, when a constant based on when
    // it was loaded is just fine
    "buildTime" -> System.currentTimeMillis
  )
}

testListeners += new JUnitXmlTestsListener(
  env("CI_REPORTS").getOrElse(s"${baseDirectory.value}/shippable/testresults"))

// see http://www.scala-sbt.org/0.13/docs/Cached-Resolution.html
updateOptions := updateOptions.value.withCachedResolution(true)
