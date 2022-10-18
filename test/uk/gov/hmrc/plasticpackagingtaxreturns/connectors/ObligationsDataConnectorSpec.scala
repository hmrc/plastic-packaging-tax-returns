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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors

import com.kenshoo.play.metrics.Metrics

import java.time.LocalDate
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.mockito.ArgumentMatchers.{any, matches}
import org.mockito.Mockito.{RETURNS_DEEP_STUBS, framework, reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar._
import org.slf4j.{Logger => Slf4jLogger}
import play.api.Logger
import play.api.http.{HeaderNames, MimeTypes}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.GetObligations
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{ObligationDataResponse, ObligationStatus}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.{ExecutionContext, Future}

class ObligationsDataConnectorSpec extends AnyWordSpec with BeforeAndAfterEach {

  private val httpClient = mock[HttpClient]
  private val appConfig = mock[AppConfig]
  private val metrics = mock[Metrics](RETURNS_DEEP_STUBS)
  private val auditConnector = mock[AuditConnector]
  private val testLogger = new Logger(mock[Slf4jLogger])
  private val uuidGenerator = mock[UUIDGenerator]

  implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global
  implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  private def createConnector = {
    new ObligationsDataConnector(
      httpClient, appConfig, metrics, auditConnector, uuidGenerator) {
      protected override val logger: Logger = testLogger
    }
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(httpClient, appConfig, metrics, auditConnector, testLogger.logger)

    when(testLogger.logger.isInfoEnabled) thenReturn true
    when(testLogger.logger.isErrorEnabled) thenReturn true
    when(uuidGenerator.randomUUID) thenReturn "123"
    when(appConfig.eisEnvironment).thenReturn("eis")
    when(appConfig.desBearerToken).thenReturn("desBearerToken")
  }

  "ObligationData connector" should {

    "call the obligation api" in {
      val fromDate = Some(LocalDate.of(2022, 12, 1))
      val toDate = Some(LocalDate.of(2023, 1, 2))
      val status = Some(ObligationStatus.OPEN)
      val expectedParams: Seq[(String, String)] =
        Seq(("from","2022-12-01"), ("to","2023-01-02"), ("status",ObligationStatus.OPEN.toString))


      val expectedHeader = Seq(("Environment", "eis"), (HeaderNames.ACCEPT, MimeTypes.JSON), (HeaderNames.AUTHORIZATION, "desBearerToken"), ("CorrelationId", "123"))
      when(appConfig.enterpriseObligationDataUrl(any())).thenReturn("/url")
      when(httpClient.GET[ObligationDataResponse](any(), any(), any()) (any(), any(), any())) thenReturn
        Future.successful(ObligationDataResponse.empty)
      await(createConnector.get("ref-id", "int-id", fromDate, toDate, status))
      verify(httpClient).GET(
        ArgumentMatchers.eq("/url"),
        ArgumentMatchers.eq(expectedParams),
        ArgumentMatchers.eq(expectedHeader))(any(), any(), any())
    }
    
    "log" when {
      
      "receiving successful 2xx response" in {
        when(httpClient.GET[Any](any(), any(), any()) (any(), any(), any())) thenReturn Future.successful(ObligationDataResponse.empty)
        await(createConnector.get("ref-id", "int-id", None, None, None)) mustBe Right(ObligationDataResponse.empty)
        verify(testLogger.logger).info(matches("""Get enterprise obligation data with correlationId \[.*\] """ 
          + """pptReference \[ref-id\] params \[List\(\)\]"""))
      }
      
      "receiving successful 404 no data response" in {
        when(httpClient.GET[Any](any(), any(), any()) (any(), any(), any())) thenReturn Future.failed(Upstream4xxResponse(
          "The remote endpoint has indicated that no associated data found", 404, 404))
        await(createConnector.get("ref-id", "int-id", None, None, None)) mustBe Right(ObligationDataResponse.empty)

        // TODO - wrong
        verify(testLogger.logger).error(matches("""Error returned when getting enterprise obligation data correlationId \[.*\] """
          + """and pptReference \[ref-id\], params \[List\(\)\], status: 404, body: The remote endpoint has indicated that no associated data found"""))
      }
      
      "receiving a 4xx failed response" in {
        when(httpClient.GET[Any](any(), any(), any()) (any(), any(), any())) thenReturn Future.failed(Upstream4xxResponse(
          "", 400, 400))
        await(createConnector.get("ref-id", "int-id", None, None, None)) mustBe Left(400)
        verify(testLogger.logger).error(matches("""Error returned when getting enterprise obligation data correlationId \[.*\] """
          + """and pptReference \[ref-id\], params \[List\(\)\], status:"""))
      }

      "receiving a 5xx failed response" in {
        when(httpClient.GET[Any](any(), any(), any()) (any(), any(), any())) thenReturn Future.failed(Upstream5xxResponse(
          "message",500, 500))
        await(createConnector.get("ref-id", "int-id", None, None, None)) mustBe Left(500)
        verify(testLogger.logger).error(matches("""Error returned when getting enterprise obligation data correlationId \[.*\] """
          + """and pptReference \[ref-id\], params \[List\(\)\], status:"""))
      }

    }

    "send explicit audit" when {

      "receiving a successful 2xx response" in {
        when(httpClient.GET[Any](any(), any(), any()) (any(), any(), any())) thenReturn Future.successful(ObligationDataResponse.empty)
        await(createConnector.get("ref-id", "int-id", None, None, None)) mustBe Right(ObligationDataResponse.empty)
        verify(auditConnector).sendExplicitAudit(GetObligations.eventType ,
          GetObligations("", "int-id", "ref-id", "Success", Some(ObligationDataResponse.empty), None))
      }

      "receiving a successful 404 response" in {
        when(httpClient.GET[Any](any(), any(), any()) (any(), any(), any())) thenReturn Future.failed(Upstream4xxResponse(
          "The remote endpoint has indicated that no associated data found", 404, 404))
        await(createConnector.get("ref-id", "int-id", None, None, None)) mustBe Right(ObligationDataResponse.empty)
        verify(auditConnector).sendExplicitAudit(GetObligations.eventType ,
          GetObligations("", "int-id", "ref-id", "Success", Some(ObligationDataResponse.empty), None))
      }

      "receiving a failed 4xx response" in {
        when(httpClient.GET[Any](any(), any(), any()) (any(), any(), any())) thenReturn Future.failed(Upstream4xxResponse(
          "", 400, 400))
        await(createConnector.get("ref-id", "int-id", None, None, None)) mustBe Left(400)
        verify(auditConnector).sendExplicitAudit(GetObligations.eventType ,
          GetObligations("", "int-id", "ref-id", "Failure", None, Some("")))
      }

      "receiving a failed 5xx response" in {
        when(httpClient.GET[Any](any(), any(), any()) (any(), any(), any())) thenReturn Future.failed(Upstream5xxResponse(
          "message",500, 500))
        await(createConnector.get("ref-id", "int-id", None, None, None)) mustBe Left(500)
        verify(auditConnector).sendExplicitAudit (GetObligations.eventType ,
          GetObligations("", "int-id", "ref-id", "Failure", None, Some("message")))
      }

    }
    
    "capture all other exceptions too" in {
      // 1. http call is successful
      when(httpClient.GET[Any](any(), any(), any()) (any(), any(), any())) thenReturn Future.successful(ObligationDataResponse.empty)

      // 2. we then fail for some other reason
      when(appConfig.adjustObligationDates) thenThrow new RuntimeException("rando exception")

      await(createConnector.get("ref-id", "int-id", None, None, None)) mustBe Left(500)
      verify(testLogger.logger).info(matches("Get enterprise obligation data"))
      verify(testLogger.logger).error(matches("Failed when getting enterprise obligation data"), any[Throwable]())
      verify(auditConnector).sendExplicitAudit[Any](any(), any())(any(), any(), any())
    }
    
  }
}
