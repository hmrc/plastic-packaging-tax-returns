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
import org.mockito.ArgumentMatchersSugar.{any, argMatching, eqTo}
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.MockitoSugar.{mock, reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import play.api.{Logger, Logging}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ReturnsConnectorSpec extends PlaySpec with BeforeAndAfterEach with Logging {

  private val httpClient = mock[HttpClient]
  private val appConfig = mock[AppConfig]
  private val metrics = mock[Metrics](RETURNS_DEEP_STUBS)
  private val auditConnector = mock[AuditConnector]
  private val headerCarrier = mock[HeaderCarrier]
  private val timer = mock[Timer.Context]
  private val shhLogger = mock[Logger]
  
  private val connector = new ReturnsConnector(httpClient, appConfig, metrics, auditConnector) {
    protected override val logger: Logger = shhLogger
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(httpClient, appConfig, metrics, auditConnector, headerCarrier, timer)
    
    when(metrics.defaultRegistry.timer(any).time()) thenReturn timer
    when(httpClient.GET[Any](any, any, any)(any, any, any)) thenReturn Future.successful(HttpResponse(412, ""))
  }

  private def callGet = await {
    connector.get("ppt-ref", "period-2", "internal-id-7") (headerCarrier)
  }
  
  "it" must {
    "use the correct timer" when {
      "getting a return" in {
        callGet
        verify(metrics.defaultRegistry).timer(eqTo("ppt.return.display.timer"))
        verify(metrics.defaultRegistry.timer(eqTo("ppt.return.display.timer"))).time()
        verify(timer).stop()
      }
    }
  }
  
  "get" must {
    
    "handle 4xx status codes" in {
      callGet mustBe Left(412)
    }

    "include a correlation id" in {
      callGet
      verify(httpClient).GET[HttpResponse](any, any, argMatching {
        case headers: List[(String, String)] if headers.toMap.keySet.contains("CorrelationId") =>
      }) (any, any, any)
    }
    
    "call the correct url" in {
      when(appConfig.returnsDisplayUrl(any, any)) thenReturn "get-url"
      callGet
      verify(appConfig).returnsDisplayUrl("ppt-ref", "period-2")
      verify(httpClient).GET(eqTo("get-url"), any, any) (any, any, any)
    }
    
  }

}
