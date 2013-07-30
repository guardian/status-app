package lib

import java.io.File
import play.api.Configuration
import com.typesafe.config.ConfigFactory
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.ClientConfiguration

object Config {
  private lazy val localPropsFile = System.getProperty("user.home") + "/.gu/statusapp.conf"
  private lazy val configuration = fileConfig(localPropsFile)

  lazy val accessKey = configuration.getString("accessKey").get
  lazy val secretKey = configuration.getString("secretKey").get
  lazy val esHost = configuration.getString("elasticsearchHost").get
  lazy val proxyHost = configuration.getString("proxyHost")
  lazy val proxyPort = configuration.getInt("proxyPort")

  lazy val managementPort = configuration.getInt("managementPort")

  def clientConfiguration() = {
    val client = new ClientConfiguration()
    if (proxyHost.isDefined) client.setProxyHost(proxyHost.get)
    if (proxyPort.isDefined) client.setProxyPort(proxyPort.get)
    client
  }

  lazy val credentials = new BasicAWSCredentials(
    accessKey, secretKey
  )

  def fileConfig(filePath: String): Configuration = {
    val file = new File(filePath)
    if (!file.exists) throw new Error("Could not find %s" format (filePath))
    Configuration(ConfigFactory.parseFile(file))
  }

}

