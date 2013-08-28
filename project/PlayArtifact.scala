import sbt._
import sbt.Keys._
import play.Project._

object PlayArtifact extends Plugin {

  val playArtifact = TaskKey[File]("play-artifact", "Builds a deployable zip file for magenta")
  val playArtifactResources = TaskKey[Seq[(File, String)]]("play-artifact-resources", "Files that will be collected by the deployment-artifact task")
  val playArtifactFile = SettingKey[String]("play-artifact-file", "Filename of the artifact built by deployment-artifact")

  val magentaPackageName = SettingKey[String]("magenta-package-name", "Name of the magenta package")

  lazy val playArtifactDistSettings = Seq(

    playArtifactResources <<= (dist, magentaPackageName, baseDirectory) map {
      (artifact, packageName, baseDirectory) =>
        Seq(
          // upstart config file
          baseDirectory / (packageName + ".conf") -> ("packages/" + packageName + "/" + packageName + ".conf"),

          // the uberjar
          artifact -> "packages/%s/%s".format(packageName, artifact.getName),

          // and the riff raff deploy instructions
          baseDirectory / "conf" / "deploy.json" -> "deploy.json"
        )
    },

    playArtifactFile := "artifacts.zip",
    playArtifact <<= buildDeployArtifact tag Tags.Disk
  )

  private def buildDeployArtifact = (streams, target, playArtifactResources, playArtifactFile, magentaPackageName) map {
    (s, target, resources, artifactFileName, magentaPackageName) =>
      val distFile = target / artifactFileName
      s.log.info("Disting " + distFile)

      if (distFile.exists()) {
        distFile.delete()
      }
      IO.zip(resources, distFile)

      // Tells TeamCity to publish the artifact => leave this println in here
      println("##teamcity[publishArtifacts '%s']".format(distFile))

      s.log.info("Done disting.")
      distFile
  }
}
