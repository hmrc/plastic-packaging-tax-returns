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
import com.github.tomakehurst.wiremock.client.WireMock.{get, ok}
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.Mockito.reset
import org.mockito.MockitoSugar.when
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK, UNAUTHORIZED}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WSClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.ReturnWireMockServerSpec
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.exportcreditbalance.ExportCreditBalanceDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class ExportCreditBalanceControllerItSpec extends PlaySpec
  with GuiceOneServerPerSuite
  with ReturnWireMockServerSpec
  with AuthTestSupport
  with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]
  private lazy val sessionRepository = mock[SessionRepository]

  private val fromDate = LocalDate.of(2022, 4, 1)
  private val toDate=LocalDate.of(2022, 6,1)

  private val url = s"http://localhost:$port/credits/calculate/$pptReference"

  override lazy val app: Application = {
    server.start()
    SharedMetricRegistries.clear()
    GuiceApplicationBuilder()
      .configure(server.overrideConfig)
      .overrides(
        bind[AuthConnector].to(mockAuthConnector),
        bind[SessionRepository].to(sessionRepository)
      )
      .build()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector, sessionRepository)
  }

  "GET" should {
    "return 200" when {
      "total requested credit is 0 "in {
        withAuthorizedUser()
        stubGetBalanceResponse()
        when(sessionRepository.get(any))
          .thenReturn(Future.successful(Some(UserAnswers(pptReference, jsonObj))))

        val response = await(wsClient.url(url).get)

        response.status mustBe OK
      }

      "total requested credit is not 0" in {
        withAuthorizedUser()
        stubGetBalanceResponse()
        when(sessionRepository.get(any))
          .thenReturn(Future.successful(Some(UserAnswers(pptReference, jsonWitTotalRequestedCredit))))

        val response = await(wsClient.url(url).get)

        response.status mustBe OK
      }
    }

    "return json" in {
      withAuthorizedUser()
      stubGetBalanceResponse()

      when(sessionRepository.get(any))
        .thenReturn(Future.successful(Some(UserAnswers(pptReference, jsonWitTotalRequestedCredit))))

      val response = await(wsClient.url(url).get)

      response.json mustBe Json.parse( 
        """{"availableCreditInPounds":200,"totalRequestedCreditInPounds":3,"totalRequestedCreditInKilograms":15,"canBeClaimed":true,"taxRate":0.2}"""
      )
    }

    "return an error" when {
      "cannot find userAnswers" in {
        withAuthorizedUser()
        when(sessionRepository.get(any)).thenReturn(Future.successful(None))

        val response = await(wsClient.url(url).get)

        response.status mustBe INTERNAL_SERVER_ERROR
      }

      "user is not Authorized" in {
        withUnauthorizedUser(new IllegalAccessException())

        val response = await(wsClient.url(url).get)

        response.status mustBe UNAUTHORIZED
      }
    }
  }


  private def jsonObj = Json.parse(
    s"""{
       |"obligation": {
       | "fromDate": "${fromDate.toString}",
       | "toDate": "${toDate.toString}"
       | },
       | "convertedCredits": "0",
       | "exportedCredits": "0"
       |}""".stripMargin).as[JsObject]

  private def jsonWitTotalRequestedCredit = Json.parse(
    s"""{
       |"obligation": {
       | "fromDate": "${fromDate.toString}",
       |  "toDate": "${toDate.toString}"
       | },
       | "convertedCredits": { "yesNo": true, "weight": 10 },
       | "exportedCredits": { "yesNo": true, "weight": 5 }
       |}""".stripMargin).as[JsObject]


  val exportCreditBalanceDisplayResponse: ExportCreditBalanceDisplayResponse = ExportCreditBalanceDisplayResponse(
    processingDate = "2021-11-17T09:32:50.345Z",
    totalPPTCharges = BigDecimal(1000),
    totalExportCreditClaimed = BigDecimal(100),
    totalExportCreditAvailable = BigDecimal(200)
  )
  
  private def stubGetBalanceResponse() = {

    val date = fromDate.minusYears(2)
    val toDate = fromDate.minusDays(1)

    server.stubFor(
      get(s"/plastic-packaging-tax/export-credits/PPT/$pptReference?fromDate=$date&toDate=$toDate")
        .willReturn(
          ok().withBody(ExportCreditBalanceDisplayResponse.format.writes(exportCreditBalanceDisplayResponse).toString())
        )
    )
  }

}
