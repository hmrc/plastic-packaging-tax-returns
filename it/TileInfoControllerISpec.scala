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

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get}
import org.mockito.Mockito.reset
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK, UNAUTHORIZED}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise._
import uk.gov.hmrc.plasticpackagingtaxreturns.models.PPTObligations
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.TaxReturnRepository
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

import java.time.LocalDate

class TileInfoControllerISpec extends PlaySpec
  with GuiceOneServerPerSuite
  with AuthTestSupport
  with BeforeAndAfterAll
  with BeforeAndAfterEach {

  val httpClient: DefaultHttpClient = app.injector.instanceOf[DefaultHttpClient]
  implicit lazy val server: CustomWireMockTestServer = CustomWireMockTestServer()
  lazy val wsClient  = app.injector.instanceOf[WSClient]

  lazy val mockReturnsRepository = mock[TaxReturnRepository]

  val fromDate = LocalDate.of(2022, 4, 1)
  val toDate = LocalDate.now()
  val status = ObligationStatus.OPEN
  val DESUrl = s"/enterprise/obligation-data/zppt/$pptReference/PPT?fromDate=$fromDate&toDate=$toDate&status=${status.toString}"
  val PPTUrl = s"http://localhost:$port/obligations/open/$pptReference"

  val obligationResponse: ObligationDataResponse = ObligationDataResponse(obligations =
    Seq(
      Obligation(
        identification =
          Identification(incomeSourceType = "ITR SA", referenceNumber = pptReference, referenceType = "PPT"),
        obligationDetails = Seq.empty
      )
    )
  )

  override lazy val app: Application = {
    SharedMetricRegistries.clear()
    GuiceApplicationBuilder()
      .configure(server.overrideConfig)
      .overrides(
        bind[AuthConnector].to(mockAuthConnector),
        bind[TaxReturnRepository].toInstance(mockReturnsRepository))
      .build()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector, mockReturnsRepository)
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    server.start()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    server.stop()
  }



  "GET" must {
    "return 200" in {
      withAuthorizedUser()
      stubObligationDataRequest(obligationResponse)

      val response = await(wsClient.url(PPTUrl).get())

      response.status mustBe OK
      response.json mustBe Json.toJson(PPTObligations(None,None, 0, false, false))
    }

    "should return Unauthorised" in {
      withUnauthorizedUser(new RuntimeException)

      val response = await(wsClient.url(PPTUrl).get())

      response.status mustBe UNAUTHORIZED
    }

    "should return 500" in {
      withAuthorizedUser()
      stubInvalidObligationDataRequest()

      val response = await(wsClient.url(s"http://localhost:$port/obligations/open/$pptReference").get())

      response.status mustBe INTERNAL_SERVER_ERROR
    }
  }

  private def stubObligationDataRequest(response: ObligationDataResponse): Unit =
    server.stubFor(
      get(DESUrl)
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(ObligationDataResponse.format.writes(response).toString())
        )
    )

  private def stubInvalidObligationDataRequest(): Unit =
    server.stubFor(
      get(DESUrl)
        .willReturn(
          aResponse()
            .withStatus(Status.INTERNAL_SERVER_ERROR)
        )
    )
}
