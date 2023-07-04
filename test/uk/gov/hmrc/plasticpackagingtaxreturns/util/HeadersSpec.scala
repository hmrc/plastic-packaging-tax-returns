package uk.gov.hmrc.plasticpackagingtaxreturns.util

import org.mockito.MockitoSugar.{mock, reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.http.{HeaderNames, MimeTypes}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Headers.{buildEisHeader, correlationIdHeaderName, environmentHeaderName}

class HeadersSpec extends PlaySpec with BeforeAndAfterEach {

  private val appConfig = mock[AppConfig]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(appConfig)
    when(appConfig.eisEnvironment).thenReturn("eis")
  }

  "buildEisHeader" should {
    "build a header for EIS" in {
      when(appConfig.bearerToken).thenReturn("EIS_TOKEN")

      buildEisHeader("123", appConfig) mustBe Seq(
        "Environment" -> "eis",
        "Accept" -> "application/json",
        "CorrelationId" -> "123",
        "Authorization" -> "EIS_TOKEN"
      )
    }
  }

  "buildDesHeader" should {
    "build a header for EIS" in {
      when(appConfig.bearerToken).thenReturn("DES_TOKEN")

      buildEisHeader("123", appConfig) mustBe Seq(
        "Environment" -> "eis",
        "Accept" -> "application/json",
        "CorrelationId" -> "123",
        "Authorization" -> "DES_TOKEN"
      )
    }
  }

}
