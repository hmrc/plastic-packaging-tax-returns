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

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.MockitoSugar.{reset, when}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.{HeaderNames, Status}
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK, UNAUTHORIZED}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.WiremockItServer
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.FinancialDataResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.helpers.FinancialTransactionHelper
import uk.gov.hmrc.plasticpackagingtaxreturns.models.{Charge, DirectDebitDetails, PPTFinancials}
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EdgeOfSystem
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

import java.time.{LocalDate, LocalDateTime}

class PPTFinancialsItSpec extends PlaySpec with GuiceOneServerPerSuite with AuthTestSupport with BeforeAndAfterAll with BeforeAndAfterEach {

  val httpClient: DefaultHttpClient          = app.injector.instanceOf[DefaultHttpClient]
  implicit lazy val server: WiremockItServer = WiremockItServer()
  lazy val wsClient: WSClient                = app.injector.instanceOf[WSClient]
  private lazy val sessionRepository         = mock[SessionRepository]
  private lazy val edgeOfSystem              = mock[EdgeOfSystem](RETURNS_DEEP_STUBS)
  private val DESnotFoundResponse            = """{"code": "NOT_FOUND", "reason": "reason"}"""

  val dateFrom = LocalDate.of(2022, 4, 1)

  val DESUrl = s"/enterprise/financial-data/ZPPT/$pptReference/PPT?onlyOpenItems=true" +
    s"&includeLocks=true&calculateAccruedInterest=true"

  val Url = s"http://localhost:$port/financials/open/$pptReference"

  override lazy val app: Application = {
    server.start()
    SharedMetricRegistries.clear()
    GuiceApplicationBuilder()
      .configure(server.overrideConfig)
      .overrides(bind[AuthConnector].to(mockAuthConnector), bind[SessionRepository].to(sessionRepository), bind[EdgeOfSystem].to(edgeOfSystem))
      .build()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector, edgeOfSystem)
    server.reset()

    val localDateTime = LocalDateTime.of(2022, 5, 1, 0, 0)
    when(edgeOfSystem.localDateTimeNow).thenReturn(localDateTime)
    when(edgeOfSystem.today).thenReturn(localDateTime.toLocalDate)
    when(edgeOfSystem.createUuid.toString).thenReturn("123")
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    server.start()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    server.stop()
  }

  "GET" should {
    "return 200" in {
      withAuthorizedUser()
      stubFinancialResponse(FinancialTransactionHelper.createFinancialResponseWithAmount())

      val response = await(wsClient.url(Url).get())

      response.status mustBe OK
    }

    "return financial details" in {
      withAuthorizedUser()

      val financialResponse = FinancialTransactionHelper.createFinancialResponseWithAmount(amount = 1.0)
      stubFinancialResponse(financialResponse)

      val response = await(wsClient.url(Url).get())

      response.status mustBe OK
      response.json mustBe Json.toJson(PPTFinancials(creditAmount = None, debitAmount = Some(Charge(1.0, LocalDate.now())), overdueAmount = None))
    }

    "return Unauthorized" in {
      withUnauthorizedUser(new RuntimeException)

      val response = await(wsClient.url(Url).get())

      response.status mustBe UNAUTHORIZED
    }

    "return server error" in {
      withAuthorizedUser()
      stubFinancialErrorResponse()

      val response = await(wsClient.url(Url).get())

      response.status mustBe INTERNAL_SERVER_ERROR
    }

    "handle magic 404" in {
      withAuthorizedUser()
      stubNotFound(DESnotFoundResponse)

      val response = await(wsClient.url(Url).get())

      response.status mustBe OK
      response.json mustBe Json.toJson(PPTFinancials(None, None, None))
    }

    "handle normal 404" in {
      withAuthorizedUser()
      stubNotFound("""{"code": "Anything", "reason": "reason"}""")

      val response = await(wsClient.url(Url).get())

      response.status mustBe INTERNAL_SERVER_ERROR
    }

    "handle exception" in {
      withAuthorizedUser()
      stubWrongJson()

      val response = await(wsClient.url(Url).get())

      response.status mustBe INTERNAL_SERVER_ERROR
    }

    "retry 3 times if api call fails" in {
      withAuthorizedUser()
      server.stubFor(get(DESUrl).willReturn(serverError()))

      await(wsClient.url(Url).get())
      server.verify(
        3,
        getRequestedFor(urlEqualTo(DESUrl))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo("Bearer des-test123456"))
      )
    }

    "not retry if api call is a 200" in {
      withAuthorizedUser()
      stubFinancialResponse(FinancialTransactionHelper.createFinancialResponseWithAmount())

      await(wsClient.url(Url).get())
      server.verify(1, getRequestedFor(urlEqualTo(DESUrl)))
    }

    "not retry if api call is a magic 404" in {
      withAuthorizedUser()
      stubNotFound(DESnotFoundResponse)

      await(wsClient.url(Url).get())
      server.verify(1, getRequestedFor(urlEqualTo(DESUrl)))
    }

  }

  "isDdInProgress" should {
    val ddInProgressUrl = s"http://localhost:$port/financials/dd-in-progress/$pptReference/c22"

    "return 200" in {
      withAuthorizedUser()
      stubFinancialResponse(FinancialTransactionHelper.createResponseWithDdInProgressFlag("c22"))

      val response = await(wsClient.url(ddInProgressUrl).get())

      response.status mustBe OK
      response.json mustBe Json.toJson(DirectDebitDetails(pptReference, "c22", true))
    }

    "return Unauthorized" in {
      withUnauthorizedUser(new RuntimeException)

      val response = await(wsClient.url(ddInProgressUrl).get())

      response.status mustBe UNAUTHORIZED
    }

    "return server error" in {
      withAuthorizedUser()
      stubFinancialErrorResponse()

      val response = await(wsClient.url(ddInProgressUrl).get())

      response.status mustBe INTERNAL_SERVER_ERROR
    }

    "handle magic 404" in {
      withAuthorizedUser()
      stubNotFound(DESnotFoundResponse)

      val response = await(wsClient.url(ddInProgressUrl).get())

      response.status mustBe OK
      response.json mustBe Json.toJson(DirectDebitDetails(pptReference, "c22", false))
    }

    "handle normal 404" in {
      withAuthorizedUser()
      stubNotFound("""{"code": "Anything", "reason": "reason"}""")

      val response = await(wsClient.url(ddInProgressUrl).get())

      response.status mustBe INTERNAL_SERVER_ERROR
    }

    "handle exception" in {
      withAuthorizedUser()
      stubWrongJson()

      val response = await(wsClient.url(ddInProgressUrl).get())

      response.status mustBe INTERNAL_SERVER_ERROR
    }
  }

  private def stubFinancialResponse(response: FinancialDataResponse): Unit =
    server.stubFor(
      get(DESUrl)
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(FinancialDataResponse.format.writes(response).toString())
        )
    )

  private def stubFinancialErrorResponse(): Unit =
    server.stubFor(
      get(DESUrl)
        .willReturn(
          aResponse()
            .withStatus(Status.BAD_REQUEST)
        )
    )

  private def stubNotFound(body: String): Unit =
    server.stubFor(
      get(DESUrl)
        .willReturn(notFound().withBody(body))
    )

  private def stubWrongJson(): Unit =
    server.stubFor(
      get(DESUrl)
        .willReturn(ok().withBody("{}"))
    )

}
