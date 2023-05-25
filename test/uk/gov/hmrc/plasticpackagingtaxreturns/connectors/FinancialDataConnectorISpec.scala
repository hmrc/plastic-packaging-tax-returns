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
import org.mockito.{Answers, ArgumentCaptor}
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.MockitoSugar.{mock, reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.Inspectors.forAll
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND}
import play.api.libs.json.Json
import play.api.test.Helpers.{SERVICE_UNAVAILABLE, await, defaultAwaitTimeout}
import uk.gov.hmrc.http.HttpReads.upstreamResponseMessage
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpException, UpstreamErrorResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.GetPaymentStatement
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.FinancialDataResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.{EISError, EnterpriseTestData}
import uk.gov.hmrc.plasticpackagingtaxreturns.util.{DateAndTime, EdgeOfSystem}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FinancialDataConnectorISpec extends PlaySpec with EnterpriseTestData with BeforeAndAfterEach {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val timer                               = mock[Timer]
  private val timerContext = mock[Timer.Context]
  val internalId: String                          = "someId"
  val pptReference: String                        = "XXPPTP103844123"
  val fromDate: LocalDate                         = LocalDate.parse("2021-10-01")
  val toDate: LocalDate                           = LocalDate.parse("2021-10-31")
  val onlyOpenItems: Option[Boolean]              = Some(true)
  val includeLocks: Option[Boolean]               = Some(false)
  val calculateAccruedInterest: Option[Boolean]   = Some(true)
  val customerPaymentInformation: Option[Boolean] = Some(false)

  private val httpClient     = mock[HttpClient]
  private val appConfig      = mock[AppConfig]
  private val metrics        = mock[Metrics](Answers.RETURNS_DEEP_STUBS)
  private val auditConnector = mock[AuditConnector]
  private val dateAndTime    = mock[DateAndTime]

  private val edgeOfSystem = mock[EdgeOfSystem]

  private val sut = new FinancialDataConnector(httpClient, appConfig, metrics, auditConnector, edgeOfSystem)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(auditConnector, metrics, httpClient, appConfig, edgeOfSystem)

    when(metrics.defaultRegistry.timer(any)).thenReturn(timer)
    when(timer.time()).thenReturn(timerContext)
  }

  "FinancialData connector" when {

    "get financial data" should {

      "handle a 200 with financial data" in {
        when(httpClient.GET[Any](any, any, any)(any, any, any)).thenReturn(Future.successful(financialDataResponse))

        val res = await(getFinancialData)

        res mustBe Right(financialDataResponse)

        withClue("stop the timer") {
          verify(timerContext).stop()
        }

        withClue("write the audit") {
          verify(auditConnector).sendExplicitAudit(eqTo(GetPaymentStatement.eventType), eqTo(getAuditModel))(any, any, any)
        }
      }

      "call the financial api" in {
        when(appConfig.enterpriseFinancialDataUrl(any)).thenReturn("/foo")
        when(httpClient.GET[Any](any, any, any)(any, any, any)).thenReturn(Future.successful(financialDataResponse))

        await(getFinancialData)

        val captor: ArgumentCaptor[Seq[(String, String)]] = ArgumentCaptor.forClass(classOf[Seq[(String, String)]])

        verify(httpClient).GET(eqTo("/foo"), eqTo(expectedParams), captor.capture())(any, any, any)

        withClue("have a correlation id in the header") {
          val correlationId = captor.getValue.filter(o => o._1.equals("CorrelationId"))
          correlationId must not be empty
          correlationId(0)._2.length must be > 0
        }
      }

      "return empty financial transaction when there are none" in {}

      "return an error" when {
        "is upstream error" in {
          when(httpClient.GET[Any](any, any, any)(any, any, any))
            .thenReturn(Future.failed(UpstreamErrorResponse("upstream error", NOT_FOUND, NOT_FOUND)))

          val res = await(getFinancialData)

          res mustBe Left(NOT_FOUND)
          verify(auditConnector).sendExplicitAudit(eqTo(GetPaymentStatement.eventType), eqTo(getExpectedAuditModelForFailure("upstream error")))(
            any,
            any,
            any
          )
        }

        "return an exception" in {
          when(httpClient.GET[Any](any, any, any)(any, any, any))
            .thenReturn(Future.failed(new HttpException("error", SERVICE_UNAVAILABLE)))

          val res = await(getFinancialData)

          res mustBe Left(INTERNAL_SERVER_ERROR)
          verify(auditConnector).sendExplicitAudit(eqTo(GetPaymentStatement.eventType), eqTo(getExpectedAuditModelForFailure("error")))(any, any, any)
        }
      }
    }
  }

  private def getFinancialData =
    sut.get(pptReference, Some(fromDate), Some(toDate), onlyOpenItems, includeLocks, calculateAccruedInterest, customerPaymentInformation, internalId)

  "FinancialData connector for obligation data" should {

    forAll(Seq(400, 404, 422, 409, 500, 502, 503)) { statusCode =>
      "return " + statusCode when {

        statusCode + " is returned from downstream service" in {

          val message  = createUpstreamMessageError(statusCode)

          when(httpClient.GET[Any](any, any, any)(any, any, any))
            .thenReturn(Future.failed(UpstreamErrorResponse(message, statusCode)))

          val res = await(getFinancialData)

          res mustBe Left(statusCode)
          verify(auditConnector).sendExplicitAudit(GetPaymentStatement.eventType, getExpectedAuditModelForFailure(message))
        }
      }
    }

    "it" should {
      "map special DES 404s to a zero financial records results" in {
        when(edgeOfSystem.localDateTimeNow).thenReturn(LocalDateTime.of(2022, 2, 22, 13, 1, 2, 3))
        when(httpClient.GET[Any](any, any, any)(any, any, any))
          .thenReturn(Future.failed(UpstreamErrorResponse(createUpstreamMessage, NOT_FOUND)))

        val result = await(getFinancialData)

        result mustBe Right(FinancialDataResponse(
          idType = Some("ZPPT"),
          idNumber = Some("XXPPTP103844123"),
          regimeType = Some("PPT"),
          processingDate = LocalDateTime.of(2022, 2, 22, 13, 1, 2, 3),
          financialTransactions = Seq()
        ))
      }
    }
  }

  private def expectedParams =
    Seq(
      "dateFrom"                   -> DateFormat.isoFormat(fromDate),
      "dateTo"                     -> DateFormat.isoFormat(toDate),
      "onlyOpenItems"              -> "true",
      "includeLocks"               -> "false",
      "calculateAccruedInterest"   -> "true",
      "customerPaymentInformation" -> "false"
    )

  private def getAuditModel =
    GetPaymentStatement(internalId, pptReference, "Success", Some(financialDataResponse), None)

  private def getExpectedAuditModelForFailure(message: String) =
    GetPaymentStatement(internalId, pptReference, "Failure", None, Some(message))

  private def createUpstreamMessage =
    upstreamResponseMessage(
      "GET",
      "/url",
      NOT_FOUND,
      Json.obj("code" -> "NOT_FOUND", "reason" -> "fish fryer fire").toString
    )

  private def createUpstreamMessageError(status: Int) =
    upstreamResponseMessage(
      "GET",
      "/url",
      status,
      Json.obj("failures" -> Seq(EISError("Error Code", "Error Reason"))).toString
    )


}
