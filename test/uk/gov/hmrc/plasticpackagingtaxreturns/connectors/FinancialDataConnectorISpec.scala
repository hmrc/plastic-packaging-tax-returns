/*
 * Copyright 2025 HM Revenue & Customs
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
import org.apache.pekko.Done
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.MockitoSugar.{mock, reset, verify, when}
import org.mockito.captor.ArgCaptor
import org.mockito.{Answers, ArgumentCaptor}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.Inspectors.forAll
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND}
import play.api.libs.concurrent.Futures
import play.api.libs.json.Json
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.GetPaymentStatement
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.FinancialDataResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.EnterpriseTestData
import uk.gov.hmrc.plasticpackagingtaxreturns.util.{EdgeOfSystem, EisHttpClient}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FinancialDataConnectorISpec extends PlaySpec with EnterpriseTestData with BeforeAndAfterEach {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val timer                               = mock[Timer]
  private val timerContext                        = mock[Timer.Context]
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
  private val edgeOfSystem   = mock[EdgeOfSystem](RETURNS_DEEP_STUBS)
  private val futures        = mock[Futures]

  private val eisHttpClient = new EisHttpClient(httpClient, appConfig, edgeOfSystem, metrics, futures)
  private val sut           = new FinancialDataConnector(eisHttpClient, appConfig, auditConnector, edgeOfSystem)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(auditConnector, metrics, httpClient, appConfig, edgeOfSystem)

    when(metrics.defaultRegistry.timer(any)).thenReturn(timer)
    when(timer.time()).thenReturn(timerContext)
    when(edgeOfSystem.createUuid.toString).thenReturn("123")
    when(futures.delay(any)).thenReturn(Future.successful(Done))
  }

  "FinancialData connector" when {

    "get financial data" should {

      "handle a 200 with financial data" in {
        when(httpClient.GET[Any](any, any, any)(any, any, any))
          .thenReturn(Future.successful(HttpResponse(200, Json.toJson(financialDataResponse).toString())))

        val res = await(getFinancialData)

        res mustBe Right(financialDataResponse)

        withClue("stop the timer") {
          verify(timerContext).stop()
        }

        withClue("write the audit") {
          verify(auditConnector).sendExplicitAudit(eqTo(GetPaymentStatement.eventType), eqTo(getAuditModel))(
            any,
            any,
            any
          )
        }
      }

      "call the financial api" in {
        when(appConfig.enterpriseFinancialDataUrl(any)).thenReturn("/foo")
        when(httpClient.GET[Any](any, any, any)(any, any, any))
          .thenReturn(Future.successful(HttpResponse(200, Json.toJson(financialDataResponse).toString())))

        await(getFinancialData)

        val captor: ArgumentCaptor[Seq[(String, String)]] = ArgumentCaptor.forClass(classOf[Seq[(String, String)]])

        verify(httpClient).GET(eqTo("/foo"), eqTo(expectedParams), captor.capture())(any, any, any)

        withClue("have a correlation id in the header") {
          val correlationId = captor.getValue.filter(o => o._1.equals("CorrelationId"))
          correlationId must not be empty
          correlationId(0)._2.length must be > 0
        }
      }

      "return an error" when {
        "is upstream error" in {
          when(httpClient.GET[Any](any, any, any)(any, any, any))
            .thenReturn(Future.successful(HttpResponse(500, "{}")))

          val res = await(getFinancialData)

          res mustBe Left(INTERNAL_SERVER_ERROR)

          verify(auditConnector).sendExplicitAudit(
            eqTo(GetPaymentStatement.eventType),
            eqTo(getExpectedAuditModelForFailure("{}"))
          )(any, any, any)
        }

        "return an exception" in {
          when(httpClient.GET[Any](any, any, any)(any, any, any))
            .thenReturn(Future.successful(HttpResponse(200, "{}")))

          val res = await(getFinancialData)

          res mustBe Left(INTERNAL_SERVER_ERROR)

          captureAndVerifyAuditConnector()
        }
      }
    }
  }

  private def getFinancialData =
    sut.get(
      pptReference,
      Some(fromDate),
      Some(toDate),
      onlyOpenItems,
      includeLocks,
      calculateAccruedInterest,
      customerPaymentInformation,
      internalId
    )

  "FinancialData connector for obligation data" should {

    forAll(Seq(400, 404, 422, 409, 500, 502, 503)) { statusCode =>
      "return " + statusCode when {

        s"$statusCode is returned from downstream service" in {
          val message = s"""{"code":"${statusCode}","reason":"fish fryer fire"}"""

          when(httpClient.GET[Any](any, any, any)(any, any, any))
            .thenReturn(Future.successful(HttpResponse(statusCode, message)))

          val res = await(getFinancialData)

          res mustBe Left(statusCode)
          verify(auditConnector).sendExplicitAudit(
            GetPaymentStatement.eventType,
            getExpectedAuditModelForFailure(message)
          )
        }
      }
    }

    "it" should {
      "map special DES 404s to a zero financial records results" in {
        val DESnotFoundResponse = """{"code": "NOT_FOUND", "reason": "fish fryer fire"}"""
        when(edgeOfSystem.localDateTimeNow).thenReturn(LocalDateTime.of(2022, 2, 22, 13, 1, 2, 3))
        when(httpClient.GET[Any](any, any, any)(any, any, any))
          .thenReturn(Future.successful(HttpResponse(NOT_FOUND, DESnotFoundResponse)))
        val result = await(getFinancialData)

        result mustBe Right(
          FinancialDataResponse(
            idType = Some("ZPPT"),
            idNumber = Some("XXPPTP103844123"),
            regimeType = Some("PPT"),
            processingDate = LocalDateTime.of(2022, 2, 22, 13, 1, 2, 3),
            financialTransactions = Seq()
          )
        )
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

  private def captureAndVerifyAuditConnector(): Unit = {
    val captor = ArgCaptor[GetPaymentStatement]

    verify(auditConnector).sendExplicitAudit[GetPaymentStatement](eqTo(GetPaymentStatement.eventType), captor)(
      any,
      any,
      any
    )

    val audit = captor.value
    audit.internalId mustBe internalId
    audit.pptReference mustBe pptReference
    audit.result mustBe "Failure"
    audit.response mustBe None
  }

}
