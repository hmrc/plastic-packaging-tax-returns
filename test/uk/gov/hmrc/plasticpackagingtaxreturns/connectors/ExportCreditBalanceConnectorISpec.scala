/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors

import com.codahale.metrics.{Timer}
import org.apache.pekko.Done
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.scalatestplus.mockito.MockitoSugar.*
import org.mockito.Mockito.{verify, when, reset}
import org.mockito.ArgumentCaptor
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND}
import play.api.libs.concurrent.Futures
import play.api.libs.json.Json
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.GetExportCredits
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.exportcreditbalance.ExportCreditBalanceDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.util.{EdgeOfSystem, EisHttpClient}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.net.URL

class ExportCreditBalanceConnectorISpec extends PlaySpec with BeforeAndAfterEach {

  protected implicit val hc: HeaderCarrier = HeaderCarrier()
  val internalId: String                   = "someId"
  val pptReference: String                 = "XXPPTP103844123"
  val fromDate: LocalDate                  = LocalDate.parse("2021-10-01")
  val toDate: LocalDate                    = LocalDate.parse("2021-10-31")
  val auditUrl: String                     = "/write/audit"
  val implicitAuditUrl: String             = s"$auditUrl/merged"

  val exportCreditBalanceDisplayResponse: ExportCreditBalanceDisplayResponse = ExportCreditBalanceDisplayResponse(
    processingDate = "2021-11-17T09:32:50.345Z",
    totalPPTCharges = BigDecimal(1000),
    totalExportCreditClaimed = BigDecimal(100),
    totalExportCreditAvailable = BigDecimal(200)
  )

  private val timerContent   = mock[Timer.Context]
  private val timer          = mock[Timer]
  private val httpClient     = mock[HttpClientV2]
  private val config         = mock[AppConfig]
  private val metric         = mock[Metrics](RETURNS_DEEP_STUBS)
  private val auditConnector = mock[AuditConnector]
  private val edgeOfSystem   = mock[EdgeOfSystem](RETURNS_DEEP_STUBS)
  private val futures        = mock[Futures]

  private val eisHttpClient =
    new EisHttpClient(httpClient, config, edgeOfSystem, metric, futures)

  private val sut = new ExportCreditBalanceConnector(eisHttpClient, config, auditConnector)
  private val mockRequestBuilder = mock[RequestBuilder]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(httpClient, config, auditConnector, mockRequestBuilder)

    
    when(httpClient.get(any())(any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.setHeader(any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.transform(any())).thenReturn(mockRequestBuilder)

    when(metric.defaultRegistry.timer(any)).thenReturn(timer)
    when(timer.time()).thenReturn(timerContent)
    when(edgeOfSystem.createUuid.toString).thenReturn("123")
    when(futures.delay(any)).thenReturn(Future.successful(Done))
    when(config.exportCreditBalanceDisplayUrl(pptReference)).thenReturn("http://some-host:8080/balanceUrl")
  }

  "ExportCreditBalance connector" when {
    "requesting a balance" should {
      "call the api" in {
        when(mockRequestBuilder.execute[HttpResponse](any,any))
          .thenReturn(Future.successful(HttpResponse(200, Json.toJson(exportCreditBalanceDisplayResponse).toString())))

        await(sut.getBalance(pptReference, fromDate, toDate, internalId))
        val headerCaptor = ArgumentCaptor.forClass(classOf[(String, String)])
        verify(httpClient).get(eqTo(URL("http://some-host:8080/balanceUrl")))(any())


        verify(mockRequestBuilder).transform(any())
        verify(mockRequestBuilder).setHeader(headerCaptor.capture())

        withClue("stop the timer")(verify(timerContent).stop())

        withClue("have a correlation id in the header") {
        val allHeaders = headerCaptor.getAllValues.get(0).asInstanceOf[Seq[(String, String)]]
        
        val correlationId = allHeaders.filter(_._1 == "CorrelationId")
        correlationId must not be empty
        correlationId(0)._2.length must be > 0
      }
      }

      "store audit" in { 
        when(mockRequestBuilder.execute[HttpResponse](any,any))
          .thenReturn(Future.successful(HttpResponse(200, Json.toJson(exportCreditBalanceDisplayResponse).toString())))

        val res = await {
          sut.getBalance(pptReference, fromDate, toDate, internalId)
        }

        res mustBe Right(exportCreditBalanceDisplayResponse)
        verifyAuditIsSent(expectedExportCredits)
      }

      "handle error" when {
        "exception is returned when cannot parse json" in {
          val mockRequestBuilder = mock[RequestBuilder]
          when(httpClient.get(any())(any())).thenReturn(mockRequestBuilder)
          when(mockRequestBuilder.setHeader(any())).thenReturn(mockRequestBuilder)
          when(mockRequestBuilder.transform(any())).thenReturn(mockRequestBuilder) 
          when(mockRequestBuilder.execute[HttpResponse](any,any))
          .thenReturn(Future.successful(HttpResponse(
            200,
            "{oops}"
          )))

          val res = await {
            sut.getBalance(pptReference, fromDate, toDate, internalId)
          }

          res mustBe Left(INTERNAL_SERVER_ERROR)
          verifyAuditIsSent()
        }

        "when there is an upstream error response" in {
          val mockRequestBuilder = mock[RequestBuilder]
          when(httpClient.get(any())(any())).thenReturn(mockRequestBuilder)
          when(mockRequestBuilder.setHeader(any())).thenReturn(mockRequestBuilder)
          when(mockRequestBuilder.transform(any())).thenReturn(mockRequestBuilder) 
          when(mockRequestBuilder.execute[HttpResponse](any,any))
          .thenReturn(Future.successful(HttpResponse(
            NOT_FOUND,
            "error message"
          )))

          val res = await {
            sut.getBalance(pptReference, fromDate, toDate, internalId)
          }

          res mustBe Left(NOT_FOUND)
          verifyAuditIsSent(Some("error message"))
        }
      }
    }
  }

  private def verifyAuditIsSent(credits: GetExportCredits) =
    verify(auditConnector).sendExplicitAudit(eqTo(GetExportCredits.eventType), eqTo(credits))(any, any, any)

  private def verifyAuditIsSent(msg: Option[String] = None) = {

    val captor = ArgumentCaptor.forClass(classOf[GetExportCredits])
    verify(auditConnector).sendExplicitAudit(eqTo(GetExportCredits.eventType), captor.capture)(any, any, any)

    val exportedCredit = captor.getValue
    exportedCredit.internalId mustBe internalId
    exportedCredit.pptReference mustBe pptReference
    exportedCredit.fromDate mustBe fromDate
    exportedCredit.toDate mustBe toDate
    exportedCredit.result mustBe "Failure"
    msg.map(m => exportedCredit.error.value must include(m))
  }

  private def expectedExportCredits =
    GetExportCredits(
      internalId,
      pptReference,
      fromDate,
      toDate,
      "Success",
      Some(exportCreditBalanceDisplayResponse),
      None
    )

}
