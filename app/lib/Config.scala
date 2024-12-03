package lib

import com.amazonaws.ClientConfiguration
import com.gu.googleauth.{AntiForgeryChecker, GoogleAuthConfig}
import com.typesafe.config.ConfigFactory
import play.api.{Configuration, Mode}
import play.api.http.HttpConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest

import java.io.File

class ParameterStoreConfig(mode: Mode, httpConfiguration: HttpConfiguration) {
  private val ssmClient = SsmClient.builder()
    .region(Region.EU_WEST_1)
    .build()

  private def getParameter(parameterName: String): String = {
    val request = GetParameterRequest.builder()
      .name(parameterName)
      .withDecryption(true)
      .build()
    ssmClient.getParameter(request).parameter().value()
  }

  lazy val googleAuthConfig: GoogleAuthConfig = {
    val host = if (mode == Mode.Dev) "status.local.dev-gutools.co.uk" else getParameter("/status-app/oauth/host")
    val protocol = if (mode == Mode.Dev) "https" else getParameter("/status-app/oauth/protocol")

    val clientId = getParameter("/status-app/oauth/clientId")
    val clientSecret = getParameter("/status-app/oauth/clientSecret")
    val redirectUrl = s"$protocol://$host/oauth2callback"
    val antiForgeryChecker = AntiForgeryChecker.borrowSettingsFromPlay(httpConfiguration)

    Option(getParameter("/status-app/oauth/allowedDomain")).map { domain =>
      GoogleAuthConfig(
        clientId = clientId,
        clientSecret = clientSecret,
        redirectUrl = redirectUrl,
        domains = List(domain),
        antiForgeryChecker = antiForgeryChecker
      )
    }.getOrElse {
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

  private def fileConfig(filePath: String) = {
    val file = new File(filePath)
    if (file.exists) ConfigFactory.parseFile(file)
    else ConfigFactory.empty()
  }
}
