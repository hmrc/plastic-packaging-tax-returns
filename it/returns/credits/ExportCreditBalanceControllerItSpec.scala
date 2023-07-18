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

package returns.credits

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.Mockito.reset
import org.mockito.MockitoSugar.when
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.HeaderNames
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK, UNAUTHORIZED, UNPROCESSABLE_ENTITY}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.json.Json.obj
import play.api.libs.ws.WSClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.ReturnWireMockServerSpec
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.exportcreditbalance.ExportCreditBalanceDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository

import scala.concurrent.{ExecutionContext, Future}

class ExportCreditBalanceControllerItSpec extends PlaySpec
  with GuiceOneServerPerSuite
  with ReturnWireMockServerSpec
  with AuthTestSupport
  with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]
  private lazy val sessionRepository = mock[SessionRepository]

  private val url = s"http://localhost:$port/credits/calculate/$pptReference"

  private val userAnswerWithCredit = UserAnswers("user-answers-id", obj(
    "obligation" -> obj(
      "fromDate" -> "2023-04-01",
      "toDate" -> "2023-06-30"
    ),
    "whatDoYouWantToDo" -> true,
    "credit" -> obj(
      "2022-04-01-2023-03-31" -> obj(
        "toDate" -> "2023-03-31",
        "convertedCredits" -> obj("yesNo" -> true, "weight" -> 10),
        "exportedCredits" -> obj("yesNo" -> true, "weight" -> 5),
    ))
  ))

  private val exportCreditBalanceDisplayResponse = ExportCreditBalanceDisplayResponse(
    processingDate = "2021-11-17T09:32:50.345Z",
    totalPPTCharges = BigDecimal(1000),
    totalExportCreditClaimed = BigDecimal(100),
    totalExportCreditAvailable = BigDecimal(200)
  )

  private def stubGetBalanceResponse(status: Int, body: String) = {
    wireMock.stubFor(get(urlPathMatching("/plastic-packaging-tax/export-credits/PPT/.*"))
      .willReturn(
        aResponse()
          .withStatus(status)
          .withBody(body)
      )
    )
  }

  override lazy val app: Application = {
    wireMock.start()
    SharedMetricRegistries.clear()
    GuiceApplicationBuilder()
      .configure(wireMock.overrideConfig)
      .overrides(
        bind[AuthConnector].to(mockAuthConnector),
        bind[SessionRepository].to(sessionRepository)
      )
      .build()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector, sessionRepository)
    wireMock.reset()
  }


  "get" should {
    "return the credit calculations" in {
      withAuthorizedUser()
      stubGetBalanceResponse(200, Json.toJson(exportCreditBalanceDisplayResponse).toString())
      when(sessionRepository.get(any)) thenReturn Future.successful(Some(userAnswerWithCredit))
      val response = await(wsClient.url(url).get)

      withClue("Check call to EIS to query credit balance") {
        wireMock.wireMockServer.verify(getRequestedFor(urlEqualTo(
          "/plastic-packaging-tax/export-credits/PPT/7777777?fromDate=2021-04-01&toDate=2023-03-31"
        )))
      }

      withClue("Response with correct values") {
        response.status mustBe OK
        response.json mustBe obj(
          "availableCreditInPounds" -> 200,
          "totalRequestedCreditInPounds" -> 3,
          "totalRequestedCreditInKilograms" -> 15,
          "canBeClaimed" -> true,
          "credit" -> obj(
            "2022-04-01-2023-03-31" -> obj(
              "weight" -> 15,
              "moneyInPounds" -> 3,
              "taxRate" -> 0.2
        )))
      }
    }

    "return an error" when {
      "cannot find userAnswers" in {
        withAuthorizedUser()
        when(sessionRepository.get(any)).thenReturn(Future.successful(None))
        val response = await(wsClient.url(url).get)
        response.status mustBe UNPROCESSABLE_ENTITY
        response.body mustBe "No user answers found"
      }

      "user is not authorized" in {
        withUnauthorizedUser(new IllegalAccessException())
        val response = await(wsClient.url(url).get)
        response.status mustBe UNAUTHORIZED
      }

      "credit balance return an error" in {
        withAuthorizedUser()
        when(sessionRepository.get(any)) thenReturn Future.successful(Some(userAnswerWithCredit))
        stubGetBalanceResponse(404, Json.toJson(exportCreditBalanceDisplayResponse).toString())
        val response = await(wsClient.url(url).get)
        response.status mustBe INTERNAL_SERVER_ERROR
        response.json mustBe obj("statusCode" -> 500, "message" -> "Error calling EIS export credit, status: 404")
      }

      "credit balance return invalid json" in {
        withAuthorizedUser()
        when(sessionRepository.get(any)) thenReturn Future.successful(Some(userAnswerWithCredit))
        stubGetBalanceResponse(200, "booo")
        val response = await(wsClient.url(url).get)
        response.status mustBe INTERNAL_SERVER_ERROR
        response.json mustBe obj("statusCode" -> 500, "message" -> "Error calling EIS export credit, status: 500")
      }
    }

    "retry the credit balance API 3 time" in {
      withAuthorizedUser()
      when(sessionRepository.get(any)) thenReturn Future.successful(Some(userAnswerWithCredit))
      stubGetBalanceResponse(404, Json.toJson(exportCreditBalanceDisplayResponse).toString())
      val response = await(wsClient.url(url).get)

      wireMock.verify(3, getRequestedFor(urlPathMatching("/plastic-packaging-tax/export-credits/PPT/.*"))
      .withHeader(HeaderNames.AUTHORIZATION, equalTo("Bearer eis-test123456")))
    }

    "retry the credit balance API 1 time" in {
      withAuthorizedUser()
      when(sessionRepository.get(any)) thenReturn Future.successful(Some(userAnswerWithCredit))
      stubGetBalanceResponse(200, Json.toJson(exportCreditBalanceDisplayResponse).toString())
      val response = await(wsClient.url(url).get)

      wireMock.verify(1, getRequestedFor(urlPathMatching("/plastic-packaging-tax/export-credits/PPT/.*"))
        .withHeader(HeaderNames.AUTHORIZATION, equalTo("Bearer eis-test123456")))
    }

  }
}
