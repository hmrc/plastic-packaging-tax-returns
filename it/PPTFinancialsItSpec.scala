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
import org.scalatest.Assertions.pending
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
import support.{AuthTestSupport, WiremockItServer}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{FinancialDataResponse, FinancialItem, FinancialTransaction}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.PPTFinancials
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.TaxReturnRepository
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

import java.time.{LocalDate, LocalDateTime}

class PPTFinancialsItSpec extends PlaySpec
  with GuiceOneServerPerSuite
  with AuthTestSupport
  with BeforeAndAfterAll
  with BeforeAndAfterEach {

  val httpClient: DefaultHttpClient          = app.injector.instanceOf[DefaultHttpClient]
  implicit lazy val server: WiremockItServer = WiremockItServer()
  lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]

  val dateFrom = LocalDate.of(2022, 4, 1)
  val DESUrl =
    s"/enterprise/financial-data/ZPPT/$pptReference/PPT?" +
      s"dateFrom=$dateFrom&dateTo=${LocalDate.now}&onlyOpenItems=true" +
      "&includeLocks=true&calculateAccruedInterest=true&customerPaymentInformation=true"

  val Url = s"http://localhost:$port/financials/open/$pptReference"

  lazy val mockReturnsRepository: TaxReturnRepository = mock[TaxReturnRepository]
  override lazy val app: Application = {
    SharedMetricRegistries.clear()
    GuiceApplicationBuilder()
      .configure(server.overrideConfig)
      .overrides(bind[AuthConnector].to(mockAuthConnector), bind[TaxReturnRepository].toInstance(mockReturnsRepository))
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

  "GET" should {
    "return 200" in {
      withAuthorizedUser()
      stubFinancialResponse(createFinancialResponseWithAmount())

      val response = await(wsClient.url(Url).get())

      response.status mustBe OK
    }

    "return financial details" in {
      withAuthorizedUser()

      val financialResponse = createFinancialResponseWithAmount(1.0)
      stubFinancialResponse(financialResponse)

      val response = await(wsClient.url(Url).get())

      response.status mustBe OK
      response.json mustBe Json.toJson(PPTFinancials(creditAmount = None, debitAmount = None, overdueAmount = Some(1.0)))
    }

    "return Unauthorized" ignore {
      withUnauthorizedUser(new RuntimeException)

      val response = await(wsClient.url(Url).get())

      response.status mustBe UNAUTHORIZED
    }

    "return server error" in {
      withAuthorizedUser()
      stubFinancialErrorResponse

      val response = await(wsClient.url(Url).get())

      response.status mustBe INTERNAL_SERVER_ERROR
    }
  }

  private def stubFinancialResponse(response: FinancialDataResponse): Unit =
    server.stubFor(
      get(DESUrl)
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(
              FinancialDataResponse.format.writes(response).toString()
            )
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

  private def createFinancialResponseWithAmount(amount: BigDecimal = BigDecimal(0.0)) = {
    FinancialDataResponse(
      idType = None,
      idNumber = None,
      regimeType = None,
      processingDate = LocalDateTime.now(),
      financialTransactions = Seq(
        FinancialTransaction(
          chargeType = None,
          mainType = None,
          periodKey = None,
          periodKeyDescription = None,
          taxPeriodFrom = None,
          taxPeriodTo = None,
          items = Seq(
            FinancialItem(
              subItem = None,
              dueDate = None,
              amount = Some(amount),
              clearingDate = None,
              clearingReason = None
            )
          )
        )
      )
    )
  }
}

