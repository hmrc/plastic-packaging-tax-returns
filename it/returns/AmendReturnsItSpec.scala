/*
 * Copyright 2023 HM Revenue & Customs
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
import com.github.tomakehurst.wiremock.client.WireMock.{equalTo, matchingJsonPath, putRequestedFor, urlEqualTo}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.MockitoSugar.reset
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status.{OK, UNAUTHORIZED}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.ReturnWireMockServerSpec
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.FinancialDataConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.helpers.FinancialTransactionHelper
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.NrsTestData
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService
import uk.gov.hmrc.plasticpackagingtaxreturns.support.AmendTestHelper
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

import scala.concurrent.{ExecutionContext, Future}

class AmendReturnsItSpec extends PlaySpec
  with GuiceOneServerPerSuite
  with ReturnWireMockServerSpec
  with AuthTestSupport
  with NrsTestData
  with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  val httpClient: DefaultHttpClient          = app.injector.instanceOf[DefaultHttpClient]
  lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]
  private val periodKey = "22C2"
  private val amendUrl = s"http://localhost:$port/returns-amend/$pptReference"
  private lazy val cacheRepository = mock[SessionRepository]
  private lazy val mockFinancialDataConnector = mock[FinancialDataConnector]

  override lazy val app: Application = {
    wireMock.start()
    SharedMetricRegistries.clear()
    GuiceApplicationBuilder()
      .configure(wireMock.overrideConfig)
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

  "amend" should {
    "return 200" in {
      withAuthorizedUser()
      mockAuthorization(NonRepudiationService.nonRepudiationIdentityRetrievals, testAuthRetrievals)
      stubSubmitReturnEISRequest(pptReference)
      setUpMockForAmend()

      val response = await(wsClient.url(amendUrl).withHttpHeaders("Authorization" -> "TOKEN").post(pptReference))

      response.status mustBe OK
    }

    "return with NRS success response" in {

      withAuthorizedUser()
      mockAuthorization(NonRepudiationService.nonRepudiationIdentityRetrievals, testAuthRetrievals)
      stubSubmitReturnEISRequest(pptReference)
      stubNrsRequest
      setUpMockForAmend()

      val response = await {
        wsClient.url(amendUrl).withHttpHeaders("Authorization" -> "TOKEN").post(pptReference)
      }
      response.status mustBe OK
      response.json mustBe Json.toJson(aReturnWithNrs())

      withClue("amend requests must include the original submission id") {
        wireMock.wireMockServer.verify(
          putRequestedFor(urlEqualTo(s"/plastic-packaging-tax/returns/PPT/7777777"))
            .withRequestBody(matchingJsonPath("$.submissionId", equalTo("submission12")))
        )
      }
    }

    "return with NRS fail response" in {
      withAuthorizedUser()
      mockAuthorization(NonRepudiationService.nonRepudiationIdentityRetrievals, testAuthRetrievals)
      stubSubmitReturnEISRequest(pptReference)
      stubNrsFailingRequest
      setUpMockForAmend()

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


  private def setUpMockForAmend(): Unit = {
    when(cacheRepository.get(any()))
      .thenReturn(Future.successful(Option(UserAnswers("id").copy(data = AmendTestHelper.userAnswersDataAmends))))
    when(cacheRepository.clear(any[String]())).thenReturn(Future.successful(true))
    when(mockFinancialDataConnector.get(any(),any(),any(),any(),any(),any(),any(),any())(any()))
      .thenReturn(Future.successful(
        Right(FinancialTransactionHelper.createFinancialResponseWithAmount(periodKey)))
      )
  }
}
