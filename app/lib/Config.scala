package lib

import java.io.File

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import play.api.{Configuration, Mode}
import com.typesafe.config.ConfigFactory
import com.amazonaws.ClientConfiguration
import com.gu.googleauth.{AntiForgeryChecker, GoogleAuthConfig}
import play.api.http.HttpConfiguration

import scala.jdk.CollectionConverters._

class DynamoConfig(mode: Mode, httpConfiguration: HttpConfiguration) {
  def get(key: String) = AWS.connection.dynamo.getItem(
    "StatusAppConfig-PROD", Map("key" -> new AttributeValue(key)).asJava).getItem.asScala


  lazy val googleAuthConfig: GoogleAuthConfig = {
    val oauthConfig = get("oauth")
    val host = if (mode == Mode.Dev) "status.local.dev-gutools.co.uk" else oauthConfig("host").getS
    val protocol = if (mode == Mode.Dev) "https" else oauthConfig.get("protocol").map(_.getS).getOrElse("https")

    val clientId = oauthConfig("clientId").getS
    val clientSecret = oauthConfig("clientSecret").getS
    val redirectUrl = s"$protocol://$host/oauth2callback"
    val antiForgeryChecker = AntiForgeryChecker.borrowSettingsFromPlay(httpConfiguration) // TODO add play secret rotation
     oauthConfig.get("allowedDomain").map(_.getS).map { domain =>
      GoogleAuthConfig(
        clientId = clientId,
        clientSecret = clientSecret,
        redirectUrl = redirectUrl,
        domains = List(domain), // Google App domain to restrict login,
        antiForgeryChecker = antiForgeryChecker
      )
    } getOrElse {
      GoogleAuthConfig.withNoDomainRestriction(
        clientId = clientId,
        clientSecret = clientSecret,
        redirectUrl = redirectUrl,
        antiForgeryChecker = antiForgeryChecker
      )
    }

  }
}

object Config {

  private lazy val localPropsFile = System.getProperty("user.home") + "/.gu/statusapp.conf"

  def configuration =
    Configuration(fileConfig(localPropsFile).withFallback(ConfigFactory.load()))

  lazy val proxyHost = configuration.getOptional[String]("proxyHost")
  lazy val proxyPort = configuration.getOptional[Int]("proxyPort")

  lazy val managementPort = configuration.getOptional[Int]("managementPort")

  def clientConfiguration() = {
    val client = new ClientConfiguration()
    proxyHost.foreach(client.setProxyHost)
    proxyPort.foreach(client.setProxyPort)
    client
  }

  def fileConfig(filePath: String) = {
    val file = new File(filePath)
    if (file.exists)
      ConfigFactory.parseFile(file)
    else
      ConfigFactory.empty()
  }


}
