package lib

import java.io.File
import play.api.Configuration
import com.typesafe.config.ConfigFactory
import com.amazonaws.auth.BasicAWSCredentials

object Config {
  private lazy val localPropsFile = System.getProperty("user.home") + "/.gu/statusapp.conf"
  private lazy val awsConfig = fileConfig(localPropsFile)

  lazy val accessKey = awsConfig.getString("accessKey").get
  lazy val secretKey = awsConfig.getString("secretKey").get
  lazy val esHost = awsConfig.getString("elasticsearchHost").get

  lazy val credentials = new BasicAWSCredentials(
    accessKey, secretKey
  )

  def fileConfig(filePath: String): Configuration = {
    val file = new File(filePath)
    if (!file.exists) throw new Error("Could not find %s" format (filePath))
    Configuration(ConfigFactory.parseFile(file))
  }

}

