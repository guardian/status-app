package lib

import java.io.File
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.gu.googleauth.GoogleAuthConfig
import play.api.{Mode, Play, Application, Configuration}
import com.typesafe.config.ConfigFactory
import com.amazonaws.ClientConfiguration
import collection.convert.decorateAll._

object Config {
  import play.api.Play.current

  private lazy val localPropsFile = System.getProperty("user.home") + "/.gu/statusapp.conf"

  def configuration(implicit app: Application) =
    Configuration(fileConfig(localPropsFile).withFallback(ConfigFactory.load()))

  lazy val proxyHost = configuration.getString("proxyHost")
  lazy val proxyPort = configuration.getInt("proxyPort")

  lazy val managementPort = configuration.getInt("managementPort")

  def clientConfiguration() = {
    val client = new ClientConfiguration()
    if (proxyHost.isDefined) client.setProxyHost(proxyHost.get)
    if (proxyPort.isDefined) client.setProxyPort(proxyPort.get)
    client
  }

  def fileConfig(filePath: String) = {
    val file = new File(filePath)
    if (file.exists)
      ConfigFactory.parseFile(file)
    else
      ConfigFactory.empty()
  }

  def dynamoConfig(key: String) = AWS.connection.dynamo.getItem(
    "StatusAppConfig", Map("key" -> new AttributeValue(key)).asJava).getItem.asScala

  lazy val googleAuthConfig = {
    val oauthConfig = dynamoConfig("oauth")
    val host = if (Play.mode == Mode.Dev) "status.thegulocal.com" else oauthConfig("host").getS
    val protocol = if (Play.mode == Mode.Dev) "https" else oauthConfig.get("protocol").map(_.getS).getOrElse("http")
    GoogleAuthConfig(
      clientId = oauthConfig("clientId").getS,
      clientSecret = oauthConfig("clientSecret").getS,
      redirectUrl =  s"$protocol://$host/oauth2callback",
      domain = oauthConfig.get("allowedDomain").map(_.getS) // Google App domain to restrict login
    )
  }
}

