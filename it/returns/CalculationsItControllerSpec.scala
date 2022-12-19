package returns

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.Mockito.reset
import org.mockito.MockitoSugar.when
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status.OK
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WSClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.WiremockItServer
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.exportcreditbalance.ExportCreditBalanceDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class CalculationsItControllerSpec
  extends PlaySpec
    with GuiceOneServerPerSuite
    with AuthTestSupport
    with BeforeAndAfterEach
    with BeforeAndAfterAll {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  implicit lazy val server: WiremockItServer = WiremockItServer()
  private val httpClient: DefaultHttpClient          = app.injector.instanceOf[DefaultHttpClient]
  lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]
  private lazy val sessionRepository = mock[SessionRepository]

  override lazy val app: Application = {
    server.start()
    SharedMetricRegistries.clear()
    GuiceApplicationBuilder()
      .configure(server.overrideConfig)
      .overrides(
        bind[AuthConnector].to(mockAuthConnector),
        bind[SessionRepository].toInstance(sessionRepository)
      )
      .build()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector)

    when(sessionRepository.get(any)).thenReturn(Future.successful(Some(UserAnswers(pptReference, userAnswersDataReturns))))
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    server.start()
  }

  override def afterAll(): Unit = {
    super.beforeAll()
    server.stop()
  }
  "CalculationsItController" should {
    "calculate new return when total request credit is 0" in {
      withAuthorizedUser()

      val result = await(wsClient.url(s"http://localhost:$port/returns-calculate/$pptReference").get)

      result.status mustBe OK
    }

    "calculate new return when credit available" in {
      withAuthorizedUser()
      stubGetBalanceRequest

      val result = await(wsClient.url(s"http://localhost:$port/returns-calculate/$pptReference").get)

      result.status mustBe OK
    }
  }

  private def stubGetBalanceRequest = {
    server.stubFor(get(s"/plastic-packaging-tax/export-credits/PPT/$pptReference")
      .willReturn(
        ok().withBody(Json.toJson(createCreditBalanceDisplayResponse).toString())
      )
    )
  }

  private def createCreditBalanceDisplayResponse = {
    ExportCreditBalanceDisplayResponse(
      processingDate = "2021-11-17T09:32:50.345Z",
      totalPPTCharges = BigDecimal(1000),
      totalExportCreditClaimed = BigDecimal(100),
      totalExportCreditAvailable = BigDecimal(200)
    )
  }

  private val userAnswersDataReturns: JsObject = Json.parse(
    s"""{
       |        "obligation" : {
       |            "periodKey" : "22C2",
       |            "fromDate" : "${LocalDate.now.minusMonths(1)}",
       |            "toDate" : "${LocalDate.now}"
       |        },
       |        "amendSelectedPeriodKey": "22C2",
       |        "manufacturedPlasticPackagingWeight" : 100,
       |        "importedPlasticPackagingWeight" : 0,
       |        "exportedPlasticPackagingWeight" : 0,
       |        "nonExportedHumanMedicinesPlasticPackagingWeight" : 10,
       |        "nonExportRecycledPlasticPackagingWeight" : 5
       |    }""".stripMargin).asInstanceOf[JsObject]

  private val userAnswersWithCredits: JsObject = Json.parse(
    s"""{
       |        "obligation" : {
       |            "periodKey" : "22C2",
       |            "fromDate" : "${LocalDate.now.minusMonths(1)}",
       |            "toDate" : "${LocalDate.now}"
       |        },
       |        "amendSelectedPeriodKey": "22C2",
       |        "manufacturedPlasticPackagingWeight" : 100,
       |        "importedPlasticPackagingWeight" : 0,
       |        "exportedPlasticPackagingWeight" : 0,
       |        "nonExportedHumanMedicinesPlasticPackagingWeight" : 10,
       |        "nonExportRecycledPlasticPackagingWeight" : 5,
       |        "convertedCredits": {
       |          "weight": "200"
       |        }
       |    }""".stripMargin).asInstanceOf[JsObject]
}
