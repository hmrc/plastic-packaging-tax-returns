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

package uk.gov.hmrc.plasticpackagingtaxreturns.util

import com.codahale.metrics.Timer
import com.kenshoo.play.metrics.Metrics
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.MockitoSugar.{mock, reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{Json, OWrites}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.HttpReads.Implicits
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient => HmrcClient, HttpResponse => HmrcResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EisHttpClientSpec extends PlaySpec with BeforeAndAfterEach {

  private val hmrcClient = mock[HmrcClient]
  private val appConfig = mock[AppConfig]
  private val edgeOfSystem = mock[EdgeOfSystem]
  private val metrics = mock[Metrics](RETURNS_DEEP_STUBS)

  private val eisHttpClient = new EisHttpClient(hmrcClient, appConfig, edgeOfSystem, metrics)
  private implicit val headerCarrier: HeaderCarrier = mock[HeaderCarrier]
  private val timer = mock[Timer.Context]

  case class ExampleModel(vitalData: Int = 1)

  private val exampleModel = ExampleModel()
  private implicit val writes: OWrites[ExampleModel] = Json.writes[ExampleModel]
  
  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(hmrcClient, appConfig, edgeOfSystem, metrics, headerCarrier, timer)
    
    when(hmrcClient.PUT[Any, Any](any, any, any) (any, any, any, any)) thenReturn Future.successful(HmrcResponse(200, "{}"))
    when(appConfig.eisEnvironment) thenReturn "space"
    when(appConfig.bearerToken) thenReturn "do-come-in"
    when(appConfig.bearerToken) thenReturn "do-come-in"
    when(edgeOfSystem.createUuid) thenReturn new UUID(1, 2)
    when(metrics.defaultRegistry.timer(any).time()) thenReturn timer
  }
  
  private def callPut = await {
    eisHttpClient.put("proto://some:port/endpoint", exampleModel, "tick.tick")
  }

  "put" should {
    
    "send a request" in {
      val response = callPut
      response mustBe HttpResponse(200, "{}", "00000000-0000-0001-0000-000000000002")
      verify(hmrcClient).PUT[ExampleModel, Any](eqTo("proto://some:port/endpoint"), eqTo(exampleModel), any) (any, 
        any, any, any)

      withClue("with these headers") {
        val headers = Seq(
          "Environment" -> "space", 
          "Accept" -> "application/json", 
          "Authorization" -> "do-come-in",
          "CorrelationId" -> "00000000-0000-0001-0000-000000000002")
        verify(hmrcClient).PUT[Any, Any](any, any, eqTo(headers)) (any, any, any, any)
      }
      
      withClue("using these implicits") {
        verify(hmrcClient).PUT[ExampleModel, HmrcResponse](any, any, any) (eqTo(writes), eqTo(Implicits.readRaw),
          eqTo(headerCarrier), eqTo(global))
      }
    }
    
    "handle responses" when {
      "status is 2xx" in {
        when(hmrcClient.PUT[Any, Any](any, any, any) (any, any, any, any)) thenReturn Future.successful(
          HmrcResponse(200, """{"a": "b"}"""))
        await { eisHttpClient.put("", exampleModel, "") } mustBe HttpResponse(200, """{"a": "b"}""", "00000000-0000-0001-0000-000000000002")
      }
      // All responses the same right now
    }
    
    "time the request-response transaction" in {
      callPut
      verify(metrics.defaultRegistry).timer(eqTo("tick.tick"))
      verify(metrics.defaultRegistry.timer(eqTo("tick.tick"))).time()
      verify(timer).stop()
    }
    
    "return the correlation id" in {
      callPut.correlationId mustBe "00000000-0000-0001-0000-000000000002"
      
    }

  }

}
