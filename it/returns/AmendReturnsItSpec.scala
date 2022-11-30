/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package returns

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, put, serverError}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status
import play.api.http.Status.{OK, UNAUTHORIZED}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WSClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.WiremockItServer
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.FinancialDataConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.builders.ReturnsSubmissionResponseBuilder
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.helpers.FinancialTransactionHelper
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.NrsTestData
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

import scala.concurrent.{ExecutionContext, Future}

class AmendReturnsItSpec extends PlaySpec
  with GuiceOneServerPerSuite
  with AuthTestSupport
  with ReturnsSubmissionResponseBuilder
  with NrsTestData
  with BeforeAndAfterAll
  with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  val httpClient: DefaultHttpClient          = app.injector.instanceOf[DefaultHttpClient]
  implicit lazy val server: WiremockItServer = WiremockItServer()
  lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]
  private val periodKey = "22C2"
  private val DesSubmitReturnUrl = s"/plastic-packaging-tax/returns/PPT/$pptReference"
  private val amendUrl = s"http://localhost:$port/returns-amend/$pptReference"
  private lazy val cacheRepository = mock[SessionRepository]
  private lazy val mockFinancialDataConnector = mock[FinancialDataConnector]

  override lazy val app: Application = {
    server.start()
    SharedMetricRegistries.clear()
    GuiceApplicationBuilder()
      .configure(server.overrideConfig)
      .overrides(
        bind[AuthConnector].to(mockAuthConnector),
        bind[SessionRepository].to(cacheRepository),
        bind[FinancialDataConnector].to(mockFinancialDataConnector)
      )
      .build()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(
      mockAuthConnector,
      cacheRepository,
      mockFinancialDataConnector
    )
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    server.start()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    server.stop()
  }

  private val userAnswersDataAmends: JsObject = Json.parse(

    s"""{
      |        "obligation" : {
      |            "periodKey" : "$periodKey"
      |        },
      |        "amendSelectedPeriodKey": "$periodKey",
      |        "returnDisplayApi" : {
      |            "idDetails" : {
      |                "pptReferenceNumber" : "pptref",
      |                "submissionId" : "submission12"
      |            },
      |            "returnDetails" : {
      |                "manufacturedWeight" : 250,
      |                "importedWeight" : 0,
      |                "totalNotLiable" : 0,
      |                "humanMedicines" : 10,
      |                "directExports" : 0,
      |                "recycledPlastic" : 5,
      |                "creditForPeriod" : 12.13,
      |                "debitForPeriod" : 0,
      |                "totalWeight" : 220,
      |                "taxDue" : 44
      |            }
      |        },
      |        "amend": {
      |            "amendManufacturedPlasticPackaging" : 100,
      |            "amendImportedPlasticPackaging" : 0,
      |            "amendDirectExportPlasticPackaging" : 0,
      |            "amendHumanMedicinePlasticPackaging" : 10,
      |            "amendRecycledPlasticPackaging" : 5
      |        }
      |    }""".stripMargin).asInstanceOf[JsObject]


  "amend" should {
    "return 200" in {
      withAuthorizedUser()
      mockAuthorization(NonRepudiationService.nonRepudiationIdentityRetrievals, testAuthRetrievals)
      createReturnAmendDesResponse
      setUpMockForAmend

      val response = await(wsClient.url(amendUrl).withHttpHeaders("Authorization" -> "TOKEN").post(pptReference))

      response.status mustBe OK
    }

    "return with NRS success response" in {

      withAuthorizedUser()
      mockAuthorization(NonRepudiationService.nonRepudiationIdentityRetrievals, testAuthRetrievals)
      createReturnAmendDesResponse
      stubNrsRequest
      setUpMockForAmend

      val response = await(wsClient.url(amendUrl).withHttpHeaders("Authorization" -> "TOKEN").post(pptReference))

      response.status mustBe OK
      response.json mustBe Json.toJson(aReturnWithNrs())
    }

    "return with NRS fail response" in {
      withAuthorizedUser()
      mockAuthorization(NonRepudiationService.nonRepudiationIdentityRetrievals, testAuthRetrievals)
      createReturnAmendDesResponse
      stubNrsFailingRequest
      setUpMockForAmend

      val response = await(wsClient.url(amendUrl).withHttpHeaders("Authorization" -> "TOKEN").post(pptReference))

      response.status mustBe OK
      response.json mustBe Json.toJson(aReturnWithNrsFailure().copy(nrsFailureReason = "exception"))
    }

    "return Unauthorized" in {
      withUnauthorizedUser(new RuntimeException)

      val response = await(wsClient.url(amendUrl).withHttpHeaders("Authorization" -> "TOKEN").post(pptReference))

      response.status mustBe UNAUTHORIZED
    }
  }


  private def setUpMockForAmend: Unit = {
    when(cacheRepository.get(any())).thenReturn(Future.successful(Option(UserAnswers("id").copy(data = userAnswersDataAmends))))
    when(cacheRepository.clear(any[String]())).thenReturn(Future.successful(true))
    when(mockFinancialDataConnector.get(any(),any(),any(),any(),any(),any(),any(),any())(any()))
      .thenReturn(Future.successful(
        Right(FinancialTransactionHelper.createFinancialResponseWithAmount(periodKey)))
      )
  }
  private def createReturnAmendDesResponse: Unit = {
    server.stubFor(
      put(DesSubmitReturnUrl)
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(Json.toJson(aReturn()).toString())
        )
    )
  }

  private def stubNrsRequest: Any = {
    server.stubFor(post(s"/submission")
      .willReturn(
        aResponse()
          .withStatus(Status.ACCEPTED)
          .withBody("""{"nrSubmissionId": "nrSubmissionId"}""")
      )
    )
  }

  private def stubNrsFailingRequest(): Any = {
    server.stubFor(post(s"/submission").willReturn(serverError().withBody("exception")))
  }
}
