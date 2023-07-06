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
import com.kenshoo.play.metrics.Metrics
import org.mockito.ArgumentMatchers.{any, matches}
import org.mockito.Mockito.{RETURNS_DEEP_STUBS, verifyNoInteractions}
import org.mockito.{ArgumentMatchers, MockitoSugar}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.wordspec.AnyWordSpec
import org.slf4j.{Logger => Slf4jLogger}
import play.api.Logger
import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND, OK}
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.concurrent.Futures
import play.api.libs.json.{JsString, Json}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.GetObligations
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise._
import uk.gov.hmrc.plasticpackagingtaxreturns.util.{EdgeOfSystem, EisHttpClient}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class ObligationsDataConnectorSpec extends AnyWordSpec with MockitoSugar with BeforeAndAfterEach {

  private val httpClient = mock[HttpClient]
  private val appConfig = mock[AppConfig]
  private val metrics = mock[Metrics](RETURNS_DEEP_STUBS)
  private val auditConnector = mock[AuditConnector]
  private val testLogger = new Logger(mock[Slf4jLogger])
  private val httpResponse = mock[HttpResponse]

  val emptyResponse = Json.parse(
    """
      |{
      |   "obligations": []
      |}
      |""".stripMargin)

  val jsonResponse = Json.parse(
    """
      |{
      |	"obligations": [{
      |		"identification": {
      |			"incomeSourceType": "ITR SA",
      |			"referenceNumber": "123",
      |			"referenceType": "PPT"
      |		},
      |		"obligationDetails": [{
      |			"status": "O",
      |			"periodKey": "#001",
      |			"inboundCorrespondenceFromDate": "2021-09-01",
      |			"inboundCorrespondenceToDate": "2021-11-01",
      |         "inboundCorrespondenceDateReceived": "2021-10-01",
      |			"inboundCorrespondenceDueDate": "2021-10-31"
      |		}]
      |	}]
      |}
      |""".stripMargin)
  val expectedResponse: ObligationDataResponse = ObligationDataResponse(obligations =
    Seq(
      Obligation(
        identification =
          Some(Identification(incomeSourceType = Some("ITR SA"), referenceNumber = "123", referenceType = "PPT")),
        obligationDetails = Seq(
          ObligationDetail(status = ObligationStatus.OPEN,
            inboundCorrespondenceFromDate = LocalDate.parse("2021-09-01"),
            inboundCorrespondenceToDate = LocalDate.parse("2021-11-01"),
            inboundCorrespondenceDateReceived = Some(LocalDate.parse("2021-10-01")),
            inboundCorrespondenceDueDate = LocalDate.parse("2021-10-31"),
            periodKey = "#001"
          )
        )
      )
    )
  )

  implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global
  implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  private def createConnector = {

    val edgeOfSystem = mock[EdgeOfSystem](RETURNS_DEEP_STUBS)
    val futures = mock[Futures]

    when(edgeOfSystem.createUuid.toString).thenReturn("123")
    when(futures.delay(any)).thenReturn(Future.successful(Done))

    val eisHttpClient = new EisHttpClient(httpClient, appConfig, edgeOfSystem, metrics, futures)

    new ObligationsDataConnector(
      eisHttpClient, appConfig, auditConnector) {
      protected override val logger: Logger = testLogger
    }
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(httpClient, appConfig, metrics, auditConnector, testLogger.logger)

    when(testLogger.logger.isInfoEnabled) thenReturn true
    when(testLogger.logger.isErrorEnabled) thenReturn true
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

      when(httpResponse.status).thenReturn(OK)
      when(httpResponse.json).thenReturn(Json.toJson(ObligationDataResponse.empty))
      when(appConfig.enterpriseObligationDataUrl(any())).thenReturn("/url")
      when(httpClient.GET[Any](any(), any(), any()) (any(), any(), any()))
        .thenReturn(Future.successful(httpResponse))

      await(createConnector.get("ref-id", "int-id", fromDate, toDate, status))

      verify(httpClient).GET(
        ArgumentMatchers.eq("/url"),
        ArgumentMatchers.eq(expectedParams),
        ArgumentMatchers.eq(createExpectedHeader))(any(), any(), any())
    }
    
    "log" when {
      
      "receiving successful 2xx response" in {
        when(httpResponse.status).thenReturn(OK)
        when(httpResponse.body).thenReturn(Json.toJson(ObligationDataResponse.empty).toString())
        when(httpClient.GET[Any](any(), any(), any()) (any(), any(), any())) thenReturn Future.successful(httpResponse)

        await(createConnector.get("ref-id", "int-id", None, None, None)) mustBe Right(ObligationDataResponse.empty)

        verify(testLogger.logger).info(matches("""Success on getting enterprise obligation data with correlationId \[.*\] """
          + """pptReference \[ref-id\] params \[List\(\)\]"""))
      }
      
      "receiving successful 404 no data response" in {
        when(httpResponse.status).thenReturn(NOT_FOUND)
        when(httpResponse.body).thenReturn(Json.parse("""{"code": "NOT_FOUND","message": "any message"}""").toString() )
        when(httpClient.GET[Any](any(), any(), any()) (any(), any(), any())) thenReturn Future.successful(httpResponse)
        await(createConnector.get("ref-id", "int-id", None, None, None)) mustBe Right(ObligationDataResponse.empty)

        verifyNoInteractions(testLogger.logger)
      }
      
      "receiving a http status 404" in {
        when(httpResponse.status).thenReturn(NOT_FOUND)
        when(httpResponse.body).thenReturn(Json.parse("""{"code": "ANY_CODE","message": "any message"}""").toString() )
        when(httpClient.GET[Any](any(), any(), any()) (any(), any(), any())) thenReturn Future.successful(httpResponse)
        await(createConnector.get("ref-id", "int-id", None, None, None)) mustBe Left(404)
        verify(testLogger.logger).error(matches("""Error returned when getting enterprise obligation data correlationId \[.*\] """
          + """and pptReference \[ref-id\], params \[List\(\)\], status:"""))
      }

      "receiving a 400 with no code" in {
        when(httpResponse.status).thenReturn(BAD_REQUEST)
        when(httpResponse.json).thenReturn(Json.parse("""{}""") )
        when(httpClient.GET[Any](any(), any(), any()) (any(), any(), any())) thenReturn Future.successful(httpResponse)
        await(createConnector.get("ref-id", "int-id", None, None, None)) mustBe Left(400)
        verify(testLogger.logger).error(matches("""Error returned when getting enterprise obligation data correlationId \[.*\] """
          + """and pptReference \[ref-id\], params \[List\(\)\], status:"""))
      }

      "receiving a 5xx failed response" in {
        when(httpResponse.status).thenReturn(INTERNAL_SERVER_ERROR)
        when(httpResponse.json).thenReturn(Json.parse("""{}""") )
        when(httpClient.GET[Any](any(), any(), any()) (any(), any(), any()))
          .thenReturn(Future.successful(httpResponse))
        await(createConnector.get("ref-id", "int-id", None, None, None)) mustBe Left(500)
        verify(testLogger.logger).error(matches("""Error returned when getting enterprise obligation data correlationId \[.*\] """
          + """and pptReference \[ref-id\], params \[List\(\)\], status:"""))
      }

    }

    "return adjust Obligation Date" in {
      when(httpResponse.status).thenReturn(OK)
      when(httpResponse.body).thenReturn(jsonResponse.toString())
      when(httpClient.GET[Any](any(), any(), any()) (any(), any(), any())) thenReturn Future.successful(httpResponse)

      await(createConnector.get("ref-id", "int-id", None, None, None)) mustBe Right(expectedResponse)
    }

    "send explicit audit" when {

      "receiving a successful 2xx response" in {
        when(httpResponse.status).thenReturn(OK)
        when(httpResponse.body).thenReturn(jsonResponse.toString())
        when(httpClient.GET[Any](any(), any(), any()) (any(), any(), any())).thenReturn(Future.successful(httpResponse))

        await(createConnector.get("ref-id", "int-id", None, None, None)) mustBe Right(expectedResponse)

        val expectedAudit = GetObligations("", "int-id", "ref-id", "Success", Some(expectedResponse), None)

        verify(auditConnector).sendExplicitAudit(GetObligations.eventType, expectedAudit)
      }

      "receiving a successful 404 response" in {
        when(httpResponse.status).thenReturn(NOT_FOUND)
        when(httpResponse.body).thenReturn(JsString("{}").toString())
        when(httpClient.GET[Any](any(), any(), any()) (any(), any(), any())).thenReturn(Future.successful(httpResponse))

        await(createConnector.get("ref-id", "int-id", None, None, None)) mustBe Left(404)

        val expectedAudit = GetObligations("", "int-id", "ref-id", "Failure", None, Some("""Error returned when getting enterprise obligation data correlationId [123] """
                      + """and pptReference [ref-id], params [List()], status: 404, body: "{}""""))
        verify(auditConnector).sendExplicitAudit(GetObligations.eventType, expectedAudit)

      }

      "receiving a 404 when obligation available" in {
        when(httpResponse.status).thenReturn(NOT_FOUND)
        when(httpResponse.body).thenReturn(Json.parse("""{"code": "NOT_FOUND","message": "any message"}""").toString())
        when(httpClient.GET[Any](any(), any(), any()) (any(), any(), any())).thenReturn(Future.successful(httpResponse))

        await(createConnector.get("ref-id", "int-id", None, None, None)) mustBe Right(ObligationDataResponse.empty)

        val expectedAudit = GetObligations("", "int-id", "ref-id", "Success", Some(ObligationDataResponse.empty), Some("""Success on retrieving enterprise obligation data correlationId [123] """
          + s"""and pptReference [ref-id], params [List()], status: 404, body: ${ObligationDataResponse.empty}"""))
        verify(auditConnector).sendExplicitAudit(GetObligations.eventType, expectedAudit)
      }

      "receiving a failed 4xx response" in {
        when(httpResponse.status).thenReturn(BAD_REQUEST)
        when(httpResponse.body).thenReturn(""""{}"""")
        when(httpClient.GET[Any](any(), any(), any()) (any(), any(), any())).thenReturn(Future.successful(httpResponse))

        await(createConnector.get("ref-id", "int-id", None, None, None)) mustBe Left(400)

        val expectedAudit = GetObligations("", "int-id", "ref-id", "Failure", None, Some("""Error returned when getting enterprise obligation data correlationId [123] """
          + """and pptReference [ref-id], params [List()], status: 400, body: "{}""""))

        verify(auditConnector).sendExplicitAudit(GetObligations.eventType, expectedAudit)
      }

      "receiving a failed 5xx response" in {
        when(httpResponse.status).thenReturn(INTERNAL_SERVER_ERROR)
        when(httpResponse.json).thenReturn(JsString("{}"))
        when(httpClient.GET[Any](any(), any(), any()) (any(), any(), any())).thenReturn(Future.successful(httpResponse))

        await(createConnector.get("ref-id", "int-id", None, None, None)) mustBe Left(500)

        val expectedAudit = GetObligations("", "int-id", "ref-id", "Failure", None, Some("""Error returned when getting enterprise obligation data correlationId [123] """
          + """and pptReference [ref-id], params [List()], status: 500, body: "{}""""))

        verify(auditConnector).sendExplicitAudit (GetObligations.eventType, expectedAudit)
      }

    }
    
    "capture all other exceptions too" in {
      when(httpResponse.status).thenReturn(OK)
      when(httpResponse.json).thenReturn(JsString("""{"code": "NOT_FOUND","message": "any message"}"""))
      // 1. http call is successful
      when(httpClient.GET[Any](any(), any(), any()) (any(), any(), any())) thenReturn Future.successful(ObligationDataResponse.empty)

      // 2. we then fail for some other reason
      //when(appConfig.adjustObligationDates) thenThrow new RuntimeException("rando exception")

      intercept[Exception] {
        await(
          createConnector.get("ref-id", "int-id", None, None, None)
        )
      }

      verify(testLogger.logger, never).error(matches("Failed when getting enterprise obligation data"), any[Throwable]())
      verifyNoInteractions(testLogger.logger)
      verifyNoInteractions(auditConnector)
    }
    
  }

  private def createExpectedHeader: Seq[(String, String)] =
    Seq(
      "Environment" -> "eis",
      HeaderNames.ACCEPT -> MimeTypes.JSON,
      "CorrelationId" -> "123",
      HeaderNames.AUTHORIZATION -> "desBearerToken",
    )
}
