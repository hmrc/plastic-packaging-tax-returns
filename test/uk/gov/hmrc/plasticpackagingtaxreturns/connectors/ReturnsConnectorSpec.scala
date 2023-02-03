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
import play.api.libs.json.{JsObject, JsString}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import play.api.{Logger, Logging}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.{GetReturn, SubmitReturn}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.{EisReturnDetails, IdDetails, Return, ReturnsSubmissionRequest}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.ReturnType.NEW
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EdgeOfSystem
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ReturnsConnectorSpec extends PlaySpec with BeforeAndAfterEach with Logging {

  private val httpClient = mock[HttpClient]
  private val appConfig = mock[AppConfig]
  private val metrics = mock[Metrics](RETURNS_DEEP_STUBS)
  private val auditConnector = mock[AuditConnector]
  private val edgeOfSystem = mock[EdgeOfSystem]
  private val headerCarrier = mock[HeaderCarrier]
  private val timer = mock[Timer.Context]
  private val shhLogger = mock[Logger]
  
  private val connector = new ReturnsConnector(httpClient, appConfig, metrics, auditConnector, edgeOfSystem) {
    protected override val logger: Logger = shhLogger
  }
  private val returnDetails = EisReturnDetails(1, 2, 3, 4, 5, 6, 7, 8, 9)
  private val returnSubmission = ReturnsSubmissionRequest(returnType = NEW, periodKey = "p-k", returnDetails = returnDetails)

  class RandoException extends Exception {
    override def getMessage: String = "went wrong"
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(httpClient, appConfig, metrics, auditConnector, edgeOfSystem, headerCarrier, timer, shhLogger)
    
    when(metrics.defaultRegistry.timer(any).time()) thenReturn timer
    when(edgeOfSystem.createUuid) thenReturn new UUID(1, 2)

    when(httpClient.GET[Any](any, any, any) (any, any, any)) thenReturn Future.successful(HttpResponse(412, ""))
    when(httpClient.PUT[Any, Any](any, any, any) (any, any, any, any)) thenReturn Future.successful(
      Return("date", IdDetails("details-ref-no", "submission-id"), None, None, None))
  }

  private def callGet = await {
    connector.get("ppt-ref", "period-2", "internal-id-7") (headerCarrier)
  }

  private def callSubmit = await {
   connector.submitReturn("ppt-ref", returnSubmission, "internal-id-7") (headerCarrier)
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
        callSubmit
        verify(metrics.defaultRegistry).timer(eqTo("ppt.return.create.timer"))
        verify(metrics.defaultRegistry.timer(eqTo("ppt.return.create.timer"))).time()
        verify(timer).stop()
      }
      
    }
  }
  
  "get" must {
    
    "include a correlation id" in {
      callGet
      verify(edgeOfSystem).createUuid
      
      val headers = ArgCaptor[Seq[(String, String)]]
      verify(httpClient).GET[HttpResponse](any, any, headers) (any, any, any)
      headers.value must contain ("CorrelationId", "00000000-0000-0001-0000-000000000002")
    }
    
    "call the correct url" in {
      when(appConfig.returnsDisplayUrl(any, any)) thenReturn "get-url"
      callGet
      verify(appConfig).returnsDisplayUrl("ppt-ref", "period-2")
      verify(httpClient).GET(eqTo("get-url"), any, any) (any, any, any)
    }

    "handle responses" when {
      
      "response code is 4xx" in {
        when(httpClient.GET[Any](any, any, any)(any, any, any)) thenReturn Future.successful(HttpResponse(412, """{"a":"b"}"""))
        callGet mustBe Left(412)
        
        withClue("sends failure to audit") {
          verify(auditConnector).sendExplicitAudit(eqTo("GetReturn"), eqTo(
            GetReturn("internal-id-7", "period-2", "Failure", None, Some("""{"a":"b"}"""))
          ))(any, any, any)
        }
        
        withClue("logs the failure") {
          verify(shhLogger).warn(startsWith("Return Display API call for correlationId")) (any)
          verify(shhLogger).warn(endsWith("pptReference [ppt-ref], periodKey [period-2]: status: 412")) (any)
        }
      }
      
      "response is 200" in {
        when(httpClient.GET[Any](any, any, any)(any, any, any)) thenReturn Future.successful(HttpResponse(200, """{"a":"b"}"""))
        callGet mustBe Right(JsObject(Seq("a" -> JsString("b"))))

        withClue("sends success to audit") {
          verify(auditConnector).sendExplicitAudit(eqTo("GetReturn"), eqTo(
            GetReturn("internal-id-7", "period-2", "Success", Some(JsObject(Seq("a" -> JsString("b")))), None)
          ))(any, any, any)
        }

        withClue("logs the success") {
          verify(shhLogger).warn(startsWith("Return Display API call for correlationId"))(any)
          verify(shhLogger).warn(endsWith("pptReference [ppt-ref], periodKey [period-2]: status: 200"))(any)
        }
      }
      
      "http client fails" in {
        val anException = new RandoException
        when(httpClient.GET[Any](any, any, any)(any, any, any)) thenReturn Future.failed(anException)
        callGet mustBe Left(500)

        withClue("sends failure to audit") {
          verify(auditConnector).sendExplicitAudit(eqTo("GetReturn"), eqTo(
            GetReturn("internal-id-7", "period-2", "Failure", None, Some("went wrong"))
          ))(any, any, any)
        }

        withClue("logs the failure") {
          verify(shhLogger).warn(startsWith("Return Display API call for correlationId"), eqTo(anException)) (any)
          verify(shhLogger).warn(endsWith("pptReference [ppt-ref], periodKey [period-2]: exception: went wrong"), any) (any)
        }
      }
      
      "our map clause fails" ignore { // todo currently this fail is caught and handled as if http-client failed
        when(httpClient.GET[Any](any, any, any)(any, any, any)) thenReturn Future.successful(HttpResponse(200, "{}"))
        when(auditConnector.sendExplicitAudit(any, any[SubmitReturn]) (any, any, any)) thenThrow new RandoException
        a [RandoException] mustBe thrownBy { callGet }
        verify(auditConnector, times(1)).sendExplicitAudit(any, any[GetReturn]) (any, any, any)
      }
    }
    
  }
  
  "submit" must {
    
    "send a put request" in {
      when(appConfig.returnsSubmissionUrl(any)) thenReturn "put-url"
      callSubmit

      withClue("to the correct url") {
        verify(appConfig).returnsSubmissionUrl("ppt-ref")
        verify(httpClient).PUT[ReturnsSubmissionRequest, Return](eqTo("put-url"), any, any) (any, any, any, any)
      }

      withClue("including a correlation id") {
        verify(edgeOfSystem).createUuid
        val headers = ArgCaptor[Seq[(String, String)]]
        verify(httpClient).PUT[ReturnsSubmissionRequest, Return](any, any, headers) (any, any, any, any)
        headers.value must contain ("CorrelationId", "00000000-0000-0001-0000-000000000002")
      }

      withClue("with correct body") {
        verify(httpClient).PUT[ReturnsSubmissionRequest, Return](any, eqTo(returnSubmission), any) (any, any, any, any)
      }
    }

    "handle responses" when {

      "shouldn't log info when everything is alright" ignore { //todo: should logger be removed?
        callSubmit
        verify(shhLogger, never).info(any)(any)
      }
      "2xx response code" in {
        callSubmit mustBe Right(Return("date", IdDetails("details-ref-no", "submission-id"), None, None, None))
        verify(auditConnector).sendExplicitAudit(any, any[SubmitReturn])(any, any, any)
      }
    }

  }

}
