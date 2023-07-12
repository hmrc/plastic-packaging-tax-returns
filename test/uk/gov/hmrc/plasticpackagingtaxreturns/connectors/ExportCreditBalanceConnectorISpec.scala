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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors

import akka.Done
import com.codahale.metrics.Timer
import com.kenshoo.play.metrics.Metrics
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.MockitoSugar.{mock, reset, verify, when}
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.captor.ArgCaptor
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers.include
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND}
import play.api.libs.concurrent.Futures
import play.api.libs.json.Json
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.GetExportCredits
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.exportcreditbalance.ExportCreditBalanceDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.util.{EdgeOfSystem, EisHttpClient}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ExportCreditBalanceConnectorISpec extends PlaySpec with BeforeAndAfterEach {

  protected implicit val hc: HeaderCarrier    = HeaderCarrier()
  val internalId: String       = "someId"
  val pptReference: String     = "XXPPTP103844123"
  val fromDate: LocalDate      = LocalDate.parse("2021-10-01")
  val toDate: LocalDate        = LocalDate.parse("2021-10-31")
  val auditUrl: String         = "/write/audit"
  val implicitAuditUrl: String = s"$auditUrl/merged"

  val exportCreditBalanceDisplayResponse: ExportCreditBalanceDisplayResponse = ExportCreditBalanceDisplayResponse(
    processingDate = "2021-11-17T09:32:50.345Z",
    totalPPTCharges = BigDecimal(1000),
    totalExportCreditClaimed = BigDecimal(100),
    totalExportCreditAvailable = BigDecimal(200)
  )

  private val timerContent = mock[Timer.Context]
  private val timer = mock[Timer]
  private val httpClient = mock[HttpClient]
  private val config = mock[AppConfig]
  private val metric = mock[Metrics](RETURNS_DEEP_STUBS)
  private val auditConnector = mock[AuditConnector]
  private val edgeOfSystem = mock[EdgeOfSystem](RETURNS_DEEP_STUBS)
  private val futures = mock[Futures]


  private val eisHttpClient = {
    new EisHttpClient(httpClient, config, edgeOfSystem, metric, futures)
  }
  private val sut = new ExportCreditBalanceConnector(
    eisHttpClient,
    config,
    auditConnector
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(httpClient, config, auditConnector)

    when(metric.defaultRegistry.timer(any)).thenReturn(timer)
    when(timer.time()).thenReturn(timerContent)
    when(edgeOfSystem.createUuid.toString).thenReturn("123")
    when(futures.delay(any)).thenReturn(Future.successful(Done))
  }

  "ExportCreditBalance connector" when {
    "requesting a balance" should {
      "call the api" in {
        when(httpClient.GET[Any](any, any, any)(any, any, any))
          .thenReturn(Future.successful(HttpResponse(200, Json.toJson(exportCreditBalanceDisplayResponse).toString())))
        when(config.exportCreditBalanceDisplayUrl(pptReference)).thenReturn("/balanceUrl")

        await {sut.getBalance(pptReference, fromDate, toDate, internalId)}

        val captor = ArgCaptor[Seq[(String, String)]]
        verify(httpClient).GET(
          eqTo("/balanceUrl"),
          eqTo(Seq("fromDate" -> DateFormat.isoFormat(fromDate), "toDate" -> DateFormat.isoFormat(toDate))),
          captor.capture
        )(any, any, any)

        withClue("stop the timer") {verify(timerContent).stop()}

        withClue("have a correlation id in the header") {
          val correlationId = captor.value.filter(o => o._1.equals("CorrelationId"))
          correlationId must not be empty
          correlationId(0)._2.length must be > 0
        }
      }

      "store audit" in {
        when(httpClient.GET[Any](any, any, any)(any, any, any))
          .thenReturn(Future.successful(HttpResponse(200, Json.toJson(exportCreditBalanceDisplayResponse).toString())))

        val res = await {
          sut.getBalance(pptReference, fromDate, toDate, internalId)
        }

        res mustBe Right(exportCreditBalanceDisplayResponse)
        verifyAuditIsSent(expectedExportCredits)
      }

      "handle error" when {
        "exception is returned when cannot parse json" in {
          when(httpClient.GET[Any](any, any, any)(any, any, any)).
            thenReturn(Future.successful(HttpResponse(200, "{oops}")))

          val res = await {
            sut.getBalance(pptReference, fromDate, toDate, internalId)
          }

          res mustBe Left(INTERNAL_SERVER_ERROR)
          verifyAuditIsSent()
        }

        "when there is an upstream error response" in {
          when(httpClient.GET[Any](any, any, any)(any, any, any)).
            thenReturn(Future.successful(HttpResponse(NOT_FOUND, "error message")))

          val res = await {
            sut.getBalance(pptReference, fromDate, toDate, internalId)
          }

          res mustBe Left(NOT_FOUND)
          verifyAuditIsSent(Some("error message"))
        }
      }
    }
  }

  private def verifyAuditIsSent(credits: GetExportCredits) = {
    verify(auditConnector).sendExplicitAudit(
      eqTo(GetExportCredits.eventType),
      eqTo(credits)
    )(any, any, any)
  }

  private def verifyAuditIsSent(msg: Option[String] = None) = {

    val captor = ArgCaptor[GetExportCredits]
    verify(auditConnector).sendExplicitAudit(
      eqTo(GetExportCredits.eventType),
      captor.capture
    )(any, any, any)

    val exportedCredit = captor.value
    exportedCredit.internalId mustBe internalId
    exportedCredit.pptReference mustBe pptReference
    exportedCredit.fromDate mustBe fromDate
    exportedCredit.toDate mustBe toDate
    exportedCredit.result mustBe  "Failure"
    msg.map(m => exportedCredit.error.value must include(m))
  }

  private def expectedExportCredits =
    GetExportCredits(internalId, pptReference, fromDate, toDate, "Success", Some(exportCreditBalanceDisplayResponse), None)

}
