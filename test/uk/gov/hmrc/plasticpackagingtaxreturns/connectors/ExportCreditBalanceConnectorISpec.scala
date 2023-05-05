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

import com.codahale.metrics.Timer
import com.kenshoo.play.metrics.Metrics
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{eq => meq}
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.Mockito.{mock => _, _}
import org.mockito.MockitoSugar.{verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar._
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND}
import play.api.test.Helpers.{SERVICE_UNAVAILABLE, await, defaultAwaitTimeout}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpException, UpstreamErrorResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.GetExportCredits
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.exportcreditbalance.ExportCreditBalanceDisplayResponse
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

  private val sut = new ExportCreditBalanceConnector(
    httpClient,
    config,
    metric,
    auditConnector
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(httpClient, config, auditConnector)

    when(metric.defaultRegistry.timer(any)).thenReturn(timer)
    when(timer.time()).thenReturn(timerContent)
  }

  "ExportCreditBalance connector" when {
    "requesting a balance" should {
      "call the api" in {
        when(httpClient.GET[Any](any, any, any)(any, any, any)).thenReturn(Future.successful(exportCreditBalanceDisplayResponse))
        when(config.exportCreditBalanceDisplayUrl(pptReference)).thenReturn("/balanceUrl")

        await {sut.getBalance(pptReference, fromDate, toDate, internalId)}

        val captor: ArgumentCaptor[Seq[(String, String)]] = ArgumentCaptor.forClass(classOf[Seq[(String, String)]])
        verify(httpClient).GET(
          meq("/balanceUrl"),
          meq(Seq("fromDate" -> DateFormat.isoFormat(fromDate), "toDate" -> DateFormat.isoFormat(toDate))),
          captor.capture()
        )(any, any, any)

        withClue("stop the timer") {verify(timerContent).stop()}

        withClue("have a correlation id in the header") {
          val correlationId = captor.getValue.filter(o => o._1.equals("CorrelationId"))
          correlationId must not be empty
          correlationId(0)._2.length must be > 0
        }
      }

      "store audit" in {
        when(httpClient.GET[Any](any, any, any)(any, any, any))
          .thenReturn(Future.successful(exportCreditBalanceDisplayResponse))

        val res = await {
          sut.getBalance(pptReference, fromDate, toDate, internalId)
        }

        res mustBe Right(exportCreditBalanceDisplayResponse)
        verifyAuditIsSent(expectedExportCredits)
      }

      "handle error" when {
        "exception is returned by the api" in {
          when(httpClient.GET[Any](any, any, any)(any, any, any)).
            thenReturn(Future.failed(new HttpException("oops", SERVICE_UNAVAILABLE)))

          val res = await {
            sut.getBalance(pptReference, fromDate, toDate, internalId)
          }

          res mustBe Left(INTERNAL_SERVER_ERROR)
          verifyAuditIsSent(expectedFailedExportCredits("oops"))
        }

        "when there is an upstream error response" in {
          when(httpClient.GET[Any](any, any, any)(any, any, any)).
            thenReturn(Future.failed(UpstreamErrorResponse("UpstreamError", NOT_FOUND, NOT_FOUND)))

          val res = await {
            sut.getBalance(pptReference, fromDate, toDate, internalId)
          }

          res mustBe Left(NOT_FOUND)
          verifyAuditIsSent(expectedFailedExportCredits("UpstreamError"))
        }
      }
    }
  }

  private def verifyAuditIsSent(credits: GetExportCredits) = {
    verify(auditConnector).sendExplicitAudit(
      meq(GetExportCredits.eventType),
      meq(credits)
    )(any, any, any)
  }

  private def expectedExportCredits =
    GetExportCredits(internalId, pptReference, fromDate, toDate, "Success", Some(exportCreditBalanceDisplayResponse), None)

  private def expectedFailedExportCredits(errMsg: String): GetExportCredits =
    GetExportCredits(internalId, pptReference, fromDate, toDate, "Failure", None, Some(errMsg))
}
