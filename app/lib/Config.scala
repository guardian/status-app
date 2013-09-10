package lib

import java.io.File
import play.api.{Application, Configuration}
import com.typesafe.config.ConfigFactory
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.ClientConfiguration

object Config {
  import play.api.Play.current

  private lazy val localPropsFile = System.getProperty("user.home") + "/.gu/statusapp.conf"

  def configuration(implicit app: Application) =
    Configuration(fileConfig(localPropsFile).withFallback(ConfigFactory.load()))

  lazy val accessKey = configuration.getString("accessKey")
  lazy val secretKey = configuration.getString("secretKey")

  lazy val proxyHost = configuration.getString("proxyHost")
  lazy val proxyPort = configuration.getInt("proxyPort")

  lazy val managementPort = configuration.getInt("managementPort")

  def clientConfiguration() = {
    val client = new ClientConfiguration()
    if (proxyHost.isDefined) client.setProxyHost(proxyHost.get)
    if (proxyPort.isDefined) client.setProxyPort(proxyPort.get)
    client
  }

  lazy val credentials = for {
    a <- accessKey
    s <- secretKey
  } yield new BasicAWSCredentials(a, s)

  def fileConfig(filePath: String) = {
    val file = new File(filePath)
    if (file.exists)
      ConfigFactory.parseFile(file)
    else
      ConfigFactory.empty()
  }

}

