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
    fileConfig(localPropsFile)

  lazy val accessKey = configuration.getString("accessKey")
  lazy val secretKey = configuration.getString("secretKey")
  lazy val esHost = configuration.getString("elasticsearchHost")
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

  def fileConfig(filePath: String): Configuration = {
    val file = new File(filePath)
    if (file.exists)
      Configuration(ConfigFactory.parseFile(file))
    else
      Configuration(ConfigFactory.empty())
  }

}

