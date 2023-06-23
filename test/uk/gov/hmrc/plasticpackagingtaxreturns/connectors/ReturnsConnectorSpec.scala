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
import org.mockito.ArgumentMatchersSugar.{any, endsWith, eqTo, startsWith}
import org.mockito.Mockito.{RETURNS_DEEP_STUBS, never}
import org.mockito.MockitoSugar.{mock, reset, times, verify, when}
import org.mockito.captor.ArgCaptor
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.http.Status
import play.api.libs.json.{JsObject, JsString}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import play.api.{Logger, Logging}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient => HmrcClient, HttpResponse => HmrcResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.{GetReturn, SubmitReturn}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.{EisReturnDetails, IdDetails, Return, ReturnsSubmissionRequest}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.ReturnType.NEW
import uk.gov.hmrc.plasticpackagingtaxreturns.util.{EdgeOfSystem, EisHttpClient, EisHttpResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ReturnsConnectorSpec extends PlaySpec with BeforeAndAfterEach with Logging {

  private val hmrcClient = mock[HmrcClient]
  private val appConfig = mock[AppConfig]
  private val metrics = mock[Metrics](RETURNS_DEEP_STUBS)
  private val auditConnector = mock[AuditConnector]
  private val edgeOfSystem = mock[EdgeOfSystem]
  private val eisHttpClient = mock[EisHttpClient]
  private val headerCarrier = mock[HeaderCarrier]
  private val timer = mock[Timer.Context]
  private val testLogger = mock[Logger]

  private val connector = new ReturnsConnector(hmrcClient, appConfig, metrics, auditConnector, edgeOfSystem, eisHttpClient) {
    protected override val logger: Logger = testLogger
  }
  
  private val returnDetails = EisReturnDetails(1, 2, 3, 4, 5, 6, 7, 8, 9)
  private val returnSubmission = ReturnsSubmissionRequest(returnType = NEW, periodKey = "p-k", returnDetails = returnDetails)
  private val putResponse = Return("date", IdDetails("details-ref-no", "submission-id"), None, None, None)
  private val putResponseToJson = EisHttpResponse(200,
    """{
      |   "processingDate" : "date",
      |   "idDetails" : {
      |     "pptReferenceNumber" : "details-ref-no",
      |     "submissionId" : "submission-id"
      |   }
      |}""".stripMargin, 
    "")

  class RandoException extends Exception {
    override def getMessage: String = "went wrong"
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(hmrcClient, appConfig, metrics, auditConnector, edgeOfSystem, headerCarrier, timer, testLogger)

    when(metrics.defaultRegistry.timer(any).time()) thenReturn timer
    when(edgeOfSystem.createUuid) thenReturn new UUID(1, 2)

    when(hmrcClient.GET[Any](any, any, any) (any, any, any)) thenReturn Future.successful(HmrcResponse(412, ""))
    when(eisHttpClient.put[Any](any, any, any) (any, any)) thenReturn Future.successful(
      putResponseToJson)
  }

  private def callGet = await {
    connector.get("ppt-ref", "period-2", "internal-id-7")(headerCarrier)
  }

  private def callSubmit = await {
    connector.submitReturn("ppt-ref", returnSubmission, "internal-id-7")(headerCarrier)
  }

  "it" must {
    "use the correct timer" when {

      "getting a return" in {
        callGet
        verify(metrics.defaultRegistry).timer(eqTo("ppt.return.display.timer"))
        verify(metrics.defaultRegistry.timer(eqTo("ppt.return.display.timer"))).time()
        verify(timer).stop()
      }

      "submitting a return" in {
        // Timer responsibility moved to EisHttpClient
      }

    }
  }

  "get" must {

    "include a correlation id" in {
      callGet
      verify(edgeOfSystem).createUuid

      val headers = ArgCaptor[Seq[(String, String)]]
      verify(hmrcClient).GET[EisHttpResponse](any, any, headers)(any, any, any)
      headers.value must contain("CorrelationId" -> "00000000-0000-0001-0000-000000000002")
    }

    "call the correct url" in {
      when(appConfig.returnsDisplayUrl(any, any)) thenReturn "get-url"
      callGet
      verify(appConfig).returnsDisplayUrl("ppt-ref", "period-2")
      verify(hmrcClient).GET(eqTo("get-url"), any, any) (any, any, any)
    }

    "handle responses" when {

      "response code is 4xx" in {
        when(hmrcClient.GET[Any](any, any, any)(any, any, any)) thenReturn Future.successful(HmrcResponse(412, ""))
        callGet mustBe Left(412)

        withClue("sends failure to audit") {
          verify(auditConnector).sendExplicitAudit(eqTo("GetReturn"), eqTo(
            GetReturn("internal-id-7", "period-2", "Failure", None, Some(""))
          ))(any, any, any)
        }

        withClue("logs the failure") {
          verify(testLogger).warn(startsWith("Return Display API call for correlationId"))(any)
          verify(testLogger).warn(endsWith("pptReference [ppt-ref], periodKey [period-2]: status: 412"))(any)
        }
      }

      "response is 200" in {
        when(hmrcClient.GET[Any](any, any, any)(any, any, any)) thenReturn Future.successful(HmrcResponse(200, """{"a":"b"}"""))
        callGet mustBe Right(JsObject(Seq("a" -> JsString("b"))))

        withClue("sends success to audit") {
          verify(auditConnector).sendExplicitAudit(eqTo("GetReturn"), eqTo(
            GetReturn("internal-id-7", "period-2", "Success", Some(JsObject(Seq("a" -> JsString("b")))), None)
          ))(any, any, any)
        }

        withClue("logs the success") {
          verify(testLogger).warn(startsWith("Return Display API call for correlationId"))(any)
          verify(testLogger).warn(endsWith("pptReference [ppt-ref], periodKey [period-2]: status: 200"))(any)
        }
      }

      "http client fails" ignore { //todo: should we send an audit event?
        val anException = new RandoException
        when(hmrcClient.GET[Any](any, any, any)(any, any, any)) thenReturn Future.failed(anException)
        callGet mustBe Left(500)

        withClue("sends failure to audit") {
          verify(auditConnector).sendExplicitAudit(eqTo("GetReturn"), eqTo(
            GetReturn("internal-id-7", "period-2", "Failure", None, Some("went wrong"))
          ))(any, any, any)
        }

        withClue("logs the failure") {
          verify(testLogger).warn(startsWith("Return Display API call for correlationId"), eqTo(anException))(any)
          verify(testLogger).warn(endsWith("pptReference [ppt-ref], periodKey [period-2]: exception: went wrong"), any)(any)
        }
      }

      "our map clause fails" in {
        when(hmrcClient.GET[Any](any, any, any)(any, any, any)) thenReturn Future.successful(HmrcResponse(200, "{}"))
        when(auditConnector.sendExplicitAudit(any, any[SubmitReturn])(any, any, any)) thenThrow new RandoException
        a[RandoException] mustBe thrownBy {
          callGet
        }
        verify(auditConnector, times(1)).sendExplicitAudit(any, any[GetReturn])(any, any, any)
      }

      "response body is not json" in {
        when(hmrcClient.GET[Any](any, any, any)(any, any, any)) thenReturn Future.successful(HmrcResponse(200, "<html />"))
        callGet mustBe Left(Status.INTERNAL_SERVER_ERROR)

        val auditDetail = ArgCaptor[GetReturn]
        withClue("secure log the failure") {
          verify(auditConnector).sendExplicitAudit[GetReturn](any, auditDetail)(any, any, any)
        }

        withClue("secure log message contains response body and exception message") {
          auditDetail.value.error.value must include("<html />")
          auditDetail.value.error.value must include("Unexpected character ('<' (code 60))")
        }

      }
    }

  }

  "submit" must {

    "send a put request" in {
      when(appConfig.returnsSubmissionUrl(any)) thenReturn "put-url"
      callSubmit

      withClue("to the correct url") {
        verify(appConfig).returnsSubmissionUrl("ppt-ref")
        verify(eisHttpClient).put[ReturnsSubmissionRequest](eqTo("put-url"), any, any) (any, any)
      }

      withClue("including a correlation id") {
        // moved to EisHttpClient
      }

      withClue("with correct body") {
        verify(eisHttpClient).put[ReturnsSubmissionRequest](any, eqTo(returnSubmission), any) (any, any)
      }
    }

    "handle usual responses" when {

      "shouldn't log info when everything is alright" in {
        callSubmit
        verify(testLogger, never).info(any)(any)
      }
      
      "2xx response code" in {
        callSubmit mustBe Right(Return("date", IdDetails("details-ref-no", "submission-id"), None, None, None))
        verify(auditConnector).sendExplicitAudit(eqTo("SubmitReturn"), eqTo(SubmitReturn("internal-id-7", "ppt-ref",
          "Success", returnSubmission, Some(putResponse), None)))(any, any, any)
      }

      "4xx response code" in {
        val putResponse = EisHttpResponse(404, "{}", "")
        when(eisHttpClient.put[Any](any, any, any) (any, any)) thenReturn Future.successful(putResponse)
        callSubmit mustBe Left(404)
        verify(auditConnector).sendExplicitAudit(eqTo("SubmitReturn"), eqTo(SubmitReturn("internal-id-7", "ppt-ref",
          "Failure", returnSubmission, None, Some("{}")))) (any, any, any)
      }

      "5xx response code" in {
        val putResponse = EisHttpResponse(500, "{}", "")
        when(eisHttpClient.put[Any](any, any, any) (any, any)) thenReturn Future.successful(putResponse)
        callSubmit mustBe Left(500)
      }
    }

    "handle 422 UNPROCESSABLE_ENTITY responses" when {

      "Etmp already has return" in {
        val example422Body =
          """{
            |  "failures" : [ {
            |    "code" : "TAX_OBLIGATION_ALREADY_FULFILLED",
            |    "reason" : "The remote endpoint has indicated that Tax obligation already fulfilled."
            |  } ]
            |}""".stripMargin

        val putResponse = EisHttpResponse(Status.UNPROCESSABLE_ENTITY, example422Body, "")
        when(eisHttpClient.put[Any](any, any, any) (any, any)) thenReturn Future.successful(putResponse)

        val exampleReturn = Return("date", IdDetails("ppt-ref", "sub-id"), None, None, None) // TODO match thing in front-end
        callSubmit mustBe Left(ReturnsConnector.StatusCode.RETURN_ALREADY_SUBMITTED)

        withClue("log success as etmp did received our call ok") {
          verify(auditConnector).sendExplicitAudit(eqTo("SubmitReturn"), eqTo(SubmitReturn("internal-id-7", "ppt-ref",
            "Success", returnSubmission, Some(exampleReturn), None)))(any, any, any) // TODO what response body will we post to secure log
        }
      }

      "similar, but not the same has etmp saying it already has that return" in {
        val responseBody =
          """{
            |  "failures" : [ {
            |    "code" : "SOME_OTHER_REASON",
            |    "reason" : "Some other thing happened"
            |  } ]
            |}""".stripMargin
        val putResponse = EisHttpResponse(Status.UNPROCESSABLE_ENTITY, responseBody, "correlation-id")
        when(eisHttpClient.put[Any](any, any, any)(any, any)) thenReturn Future.successful(putResponse)
        callSubmit mustBe Left(Status.UNPROCESSABLE_ENTITY)

        withClue("log as a call failure") {
          // TODO what response body will we post to secure log, not a fake return?
          verify(auditConnector).sendExplicitAudit(eqTo("SubmitReturn"), eqTo(SubmitReturn("internal-id-7", "ppt-ref",
            "Failure", returnSubmission, None, Some(responseBody)))) (any, any, any)
        }
      }

      "422 response but not json, ie similar again but payload not json (seen in the wild)" in {
        val putResponse = EisHttpResponse(Status.UNPROCESSABLE_ENTITY, "<html />", "correlation-id")
        when(eisHttpClient.put[Any](any, any, any)(any, any)) thenReturn Future.successful(putResponse)
        callSubmit mustBe Left(Status.UNPROCESSABLE_ENTITY)

        withClue("log as a failure because we weren't sent json") {
          // TODO what response body will we post to secure log, not a fake return?
          verify(auditConnector).sendExplicitAudit(eqTo("SubmitReturn"), eqTo(SubmitReturn("internal-id-7", "ppt-ref",
            "Failure", returnSubmission, None, Some("<html />")))) (any, any, any)
        }
      }
      
      // todos
      "log summit when unhappy" in {}
    }

    "response body is not json" in {
      when(eisHttpClient.put[Any](any, any, any)(any, any)) thenReturn Future.successful(EisHttpResponse(200, "<html />", "correlation-id"))
      callSubmit mustBe Left(Status.INTERNAL_SERVER_ERROR)

      val auditDetail = ArgCaptor[SubmitReturn]
      withClue("secure log the failure") {
        verify(auditConnector).sendExplicitAudit[SubmitReturn](any, auditDetail)(any, any, any)
      }

      // TODO need to discuss what gets sent to secure log and kibana
      withClue("secure log message contains response body and exception message") {
//      auditDetail.value.error.value must include("<html />")
//      auditDetail.value.error.value must include("Unexpected character ('<' (code 60))")
        auditDetail.value.error.value must include("Response body could not be read as type uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.Return")
      }

    }
  }
}