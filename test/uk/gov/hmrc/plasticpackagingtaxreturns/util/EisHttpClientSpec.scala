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

import akka.Done
import com.codahale.metrics.Timer
import com.kenshoo.play.metrics.Metrics
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.MockitoSugar
import org.mockito.integrations.scalatest.ResetMocksAfterEachTest
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.libs.concurrent.Futures
import play.api.libs.json.{Json, OWrites}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.HttpReads.Implicits
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient => HmrcClient, HttpResponse => HmrcResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class EisHttpClientSpec extends PlaySpec with BeforeAndAfterEach with MockitoSugar with ResetMocksAfterEachTest {

  private val hmrcClient = mock[HmrcClient]
  private val appConfig = mock[AppConfig]
  private val edgeOfSystem = mock[EdgeOfSystem]
  private val metrics = mock[Metrics](RETURNS_DEEP_STUBS)
  private val futures = mock[Futures]

  private val eisHttpClient = new EisHttpClient(hmrcClient, appConfig, edgeOfSystem, metrics, futures)
  private implicit val headerCarrier: HeaderCarrier = mock[HeaderCarrier]
  private val timer = mock[Timer.Context]

  case class ExampleModel(vitalData: Int = 1)

  private val exampleModel = ExampleModel()
  private implicit val writes: OWrites[ExampleModel] = Json.writes[ExampleModel]
  
  override protected def beforeEach(): Unit = {
    super.beforeEach()
    
    when(hmrcClient.PUT[Any, Any](any, any, any) (any, any, any, any)) thenReturn Future.successful(HmrcResponse(200, "{}"))
    when(appConfig.eisEnvironment) thenReturn "space"
    when(appConfig.bearerToken) thenReturn "do-come-in"
    when(edgeOfSystem.createUuid) thenReturn new UUID(1, 2)
    when(metrics.defaultRegistry.timer(any).time()) thenReturn timer
    when(futures.delay(any)) thenReturn Future.successful(Done)
  }
  
  private def callPut = await {
    eisHttpClient.put("proto://some:port/endpoint", exampleModel, "tick.tick")
  }

  "put" should {

    "send a request" in {
      val response = callPut
      response mustBe EisHttpResponse(200, "{}", "00000000-0000-0001-0000-000000000002")
      verify(hmrcClient).PUT[ExampleModel, Any](eqTo("proto://some:port/endpoint"), eqTo(exampleModel), any)(any,
        any, any, any)

      withClue("with these headers") {
        val headers = Seq(
          "Environment" -> "space",
          "Accept" -> "application/json",
          "Authorization" -> "do-come-in",
          "CorrelationId" -> "00000000-0000-0001-0000-000000000002")
        verify(hmrcClient).PUT[Any, Any](any, any, eqTo(headers))(any, any, any, any)
      }

      withClue("using these implicits") {
        verify(hmrcClient).PUT[ExampleModel, HmrcResponse](any, any, any)(eqTo(writes), eqTo(Implicits.readRaw),
          eqTo(headerCarrier), eqTo(global))
      }
    }

    "handle responses" when {
      "status is 2xx" in {
        when(hmrcClient.PUT[Any, Any](any, any, any)(any, any, any, any)) thenReturn Future.successful(
          HmrcResponse(200, """{"a": "b"}"""))
        callPut mustBe EisHttpResponse(200, """{"a": "b"}""", "00000000-0000-0001-0000-000000000002")
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
    
  "retry" should {
    
    "try again if the first attempt fails" in {
      when(hmrcClient.PUT[Any, Any](any, any, any) (any, any, any, any)) thenReturn (
        Future.successful(HmrcResponse(500, "")),
        Future.successful(HmrcResponse(200, "")),
      )

      val response = callPut
      verify(hmrcClient, times(2)).PUT[ExampleModel, Any](eqTo("proto://some:port/endpoint"), eqTo(exampleModel), any) (any,
        any, any, any)
      response.status mustBe 200

      withClue("with a delay between attempt") {
        verify(futures).delay(30 milliseconds)
      }
    }
    
    "eventually give up" in {
      when(hmrcClient.PUT[Any, Any](any, any, any) (any, any, any, any)) thenReturn 
        Future.successful(HmrcResponse(500, ""))
      val response = callPut
      response.status mustBe 500
      
      withClue("after trying 3 times") {
        verify(hmrcClient, times(3)).PUT[ExampleModel, Any](any, any, any)(any, any, any, any)
        verify(futures, times(2)).delay(30 milliseconds)
      }
    }
    
    "use custom success criteria" in {
      when(hmrcClient.PUT[Any, Any](any, any, any) (any, any, any, any)) thenReturn
        Future.successful(HmrcResponse(422, ""))

      val response = await {
        val isSuccessful = (response: HmrcResponse) => response.status == 422
        eisHttpClient.put("url", exampleModel, "timer", isSuccessful)
      }

      verify(hmrcClient, times(1)).PUT[ExampleModel, Any](any, any, any)(any, any, any, any)
      verify(futures, times(0)).delay(30 milliseconds)
      response.status mustBe 422 
    }

  }

}