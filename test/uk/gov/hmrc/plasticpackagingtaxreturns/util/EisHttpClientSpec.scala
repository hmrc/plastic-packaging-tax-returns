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
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.MockitoSugar
import org.mockito.scalatest.ResetMocksAfterEachTest
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.Logger
import play.api.libs.concurrent.Futures
import play.api.libs.json.{Json, OFormat}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.HttpReads.Implicits
import uk.gov.hmrc.http.{GatewayTimeoutException, HeaderCarrier, HttpClient => HmrcClient, HttpResponse => HmrcResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EisHttpClient.retryDelayInMillisecond
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Headers.buildEisHeader

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
  private val timer = mock[Timer.Context]
  private val testLogger = mock[Logger]
  private implicit val headerCarrier: HeaderCarrier = mock[HeaderCarrier]

  private val eisHttpClient = new EisHttpClient(hmrcClient, appConfig, edgeOfSystem, metrics, futures) {
    protected override val logger: Logger = testLogger
  }

  case class ExampleModel(vitalData: Int = 1)

  private val exampleModel = ExampleModel()
  private implicit val formats: OFormat[ExampleModel] = Json.format[ExampleModel]

  private val headerFn = (correlationId: String, _: AppConfig) => Seq(
    "Environment" -> "space",
    "Accept" -> "application/json",
    "Authorization" -> "do-come-in",
    "CorrelationId" -> correlationId
  )
  
  override protected def beforeEach(): Unit = {
    super.beforeEach()
    
    when(hmrcClient.PUT[Any, Any](any, any, any) (any, any, any, any)).thenReturn(Future.successful(HmrcResponse(200, "{}")))
    when(appConfig.eisEnvironment) thenReturn "space"
    when(appConfig.bearerToken) thenReturn "do-come-in"
    when(edgeOfSystem.createUuid).thenReturn(
      UUID.fromString("00000000-0000-0001-0000-000000000001"),
      UUID.fromString("00000000-0000-0001-0000-000000000002"),
      UUID.fromString("00000000-0000-0001-0000-000000000003")

    )
    when(metrics.defaultRegistry.timer(any).time()) thenReturn timer
    when(futures.delay(any)) thenReturn Future.successful(Done)
  }
  
  private def callPut = await {
    eisHttpClient.put("proto://some:port/endpoint", exampleModel, "tick.tick", buildEisHeader)
  }

  "put" should {

    "send a request" in {
      val response = callPut
      response mustBe EisHttpResponse(200, "{}", "00000000-0000-0001-0000-000000000001")
      verify(hmrcClient).PUT[ExampleModel, Any](eqTo("proto://some:port/endpoint"), eqTo(exampleModel), any)(any,
        any, any, any)

      withClue("with these headers") {
        val headers = Seq(
          "Environment" -> "space",
          "Accept" -> "application/json",
          "CorrelationId" -> "00000000-0000-0001-0000-000000000001",
          "Authorization" -> "do-come-in")
        verify(hmrcClient).PUT[Any, Any](any, any, eqTo(headers))(any, any, any, any)
      }

      withClue("using these implicits") {
        verify(hmrcClient).PUT[ExampleModel, HmrcResponse](any, any, any)(eqTo(formats), eqTo(Implicits.readRaw),
          eqTo(headerCarrier), eqTo(global))
      }
    }

    "handle responses" when {
      "status is 2xx" in {
        when(hmrcClient.PUT[Any, Any](any, any, any)(any, any, any, any)) thenReturn Future.successful(
          HmrcResponse(200, """{"a": "b"}"""))
        callPut mustBe EisHttpResponse(200, """{"a": "b"}""", "00000000-0000-0001-0000-000000000001")
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
      callPut.correlationId mustBe "00000000-0000-0001-0000-000000000001"
    }

  }

  "get" should {
    "send a request" in {

      when(appConfig.desBearerToken).thenReturn("do-come-in")
      when(hmrcClient.GET[Any](any, any, any)(any, any, any))
        .thenReturn(Future.successful(HmrcResponse(200, """{"a": "b"}""")))
      eisHttpClient.get("/any/url", Seq("a" -> "b"), "timer-name", headerFn)

      verify(hmrcClient).GET(eqTo("/any/url"), eqTo(Seq("a" -> "b")), any)(any, any,any)

      withClue("with these headers") {
        val headers = Seq(
          "Environment" -> "space",
          "Accept" -> "application/json",
          "Authorization" -> "do-come-in",
          "CorrelationId" -> "00000000-0000-0001-0000-000000000001")
        verify(hmrcClient).GET[Any](any, any, eqTo(headers))(any, any, any)
      }
    }

    "return an EisHttpResponse" in {
      when(hmrcClient.GET[Any](any, any, any)(any, any, any))
        .thenReturn(Future.successful(HmrcResponse(200, """{"a": "b"}""")))
      val result = await(eisHttpClient.get("/any/url", Seq.empty, "timer-name",headerFn))

      result mustBe EisHttpResponse(200, """{"a": "b"}""", "00000000-0000-0001-0000-000000000001")
    }

    "time the transaction" in {
      when(hmrcClient.GET[Any](any, any, any)(any, any, any))
        .thenReturn(Future.successful(HmrcResponse(200, """{"a": "b"}""")))

      await(eisHttpClient.get("/any/url", Seq.empty, "timer-name", headerFn))
      verify(metrics.defaultRegistry.timer(eqTo("tick.tick"))).time()
      verify(timer).stop()
    }

    "retry again if the first attempt fails" in {
      when(hmrcClient.GET[Any](any, any, any) (any, any, any)).thenReturn(
        Future.successful(HmrcResponse(500, "")),
        Future.successful(HmrcResponse(200, "")),
      )

      val response = await(eisHttpClient.get("/any/url", Seq.empty, "timer-name", headerFn))

      response.status mustBe 200
      verify(hmrcClient, times(2)).GET[Any](eqTo("/any/url"), eqTo(Seq.empty), any) (any,
        any, any)

      withClue("with a delay between attempt") {
        verify(futures).delay(retryDelayInMillisecond milliseconds)
      }
    }

    "retry eventually give up" in {
      when(hmrcClient.GET[Any](any, any, any) (any, any, any)).thenReturn(
        Future.successful(HmrcResponse(500, ""))
      )
      val response = await(eisHttpClient.get("/any/url", Seq.empty, "timer-name", headerFn))
      response.status mustBe 500

      withClue("after trying 3 times") {
        verify(hmrcClient, times(3)).GET[Any](any, any, any) (any,
          any, any)
        verify(futures, times(2)).delay(1000 milliseconds)
      }
    }

    "retry use custom success criteria" in {
      when(hmrcClient.GET[Any](any, any, any) (any, any, any)).thenReturn(
        Future.successful(HmrcResponse(422, ""))
      )

      val response = await {
        val isSuccessful = (response: EisHttpResponse) => response.status == 422
        eisHttpClient.get("url", Seq.empty, "timer-name", headerFn, isSuccessful)
      }

      verify(hmrcClient, times(1)).GET[Any](any, any, any)(any, any, any)
      verifyNoMoreInteractions(futures)
      response.status mustBe 422
    }
  }
    
  "retry" should {
    
    "try again if the first attempt fails" in {
      when(hmrcClient.PUT[ExampleModel, Any](any, any, any)(any, any, any, any)).thenReturn(
        Future.successful(HmrcResponse(500, "")),
        Future.successful(HmrcResponse(200, "")),
      )

      val response = callPut
      verify(hmrcClient, times(2)).PUT[ExampleModel, Any](
        eqTo("proto://some:port/endpoint"),
        eqTo(exampleModel),
        any
      ) (any, any, any, any)

      response.status mustBe 200

      withClue("with a delay between attempt") {
        verify(futures).delay(1000 milliseconds)
      }
    }
    
    "eventually give up" in {
      when(hmrcClient.PUT[Any, Any](any, any, any) (any, any, any, any)) thenReturn 
        Future.successful(HmrcResponse(500, ""))
      val response = callPut
      response.status mustBe 500
      
      withClue("after trying 3 times") {
        verify(hmrcClient, times(3)).PUT[ExampleModel, Any](any, any, any)(any, any, any, any)
        verify(futures, times(2)).delay(1000 milliseconds)
      }
    }
    
    "use custom success criteria" in {
      when(hmrcClient.PUT[Any, Any](any, any, any) (any, any, any, any)).thenReturn(
        Future.successful(HmrcResponse(422, ""))
      )

      val response = await {
        val isSuccessful = (response: EisHttpResponse) => response.status == 422
        eisHttpClient.put("url", exampleModel, "timer", buildEisHeader, isSuccessful)
      }

      verify(hmrcClient, times(1)).PUT[ExampleModel, Any](any, any, any)(any, any, any, any)
      verifyNoMoreInteractions(futures)
      response.status mustBe 422 
    }
    
    "log a retry and its eventual success" in {
      when(hmrcClient.PUT[Any, Any](anyString(), any, any)(any, any, any, any)).thenReturn(
        Future.successful(HmrcResponse(500, "")),
        Future.successful(HmrcResponse(200, ""))
      )

      callPut
      verify(testLogger, times(1)).warn(
        eqTo("PPT_RETRY retrying: url proto://some:port/endpoint status 500 correlation-id 00000000-0000-0001-0000-000000000001")
      )(any)

      verify(testLogger, times(1)).warn(
        eqTo("PPT_RETRY successful: url proto://some:port/endpoint correlation-id 00000000-0000-0001-0000-000000000002")
      )(any)
    }

    "not log if successful first time" in {
      callPut
      verify(testLogger, times(0)).warn(any)(any)
    }
    
    "log when giving up" in {
      when(hmrcClient.PUT[Any, Any](any, any, any)(any, any, any, any)).thenReturn(Future.successful(HmrcResponse(500, "")))

      callPut
      verify(testLogger, times(1)).warn(
        eqTo("PPT_RETRY retrying: url proto://some:port/endpoint status 500 correlation-id 00000000-0000-0001-0000-000000000001")
      )(any)

      verify(testLogger, times(1)).warn(
        eqTo("PPT_RETRY retrying: url proto://some:port/endpoint status 500 correlation-id 00000000-0000-0001-0000-000000000002")
      )(any)
      
      verify(testLogger, times(1)).warn(
        eqTo("PPT_RETRY gave up: url proto://some:port/endpoint status 500 correlation-id 00000000-0000-0001-0000-000000000003")
      )(any)
    }

    "retry after an exception" in {
      when(hmrcClient.PUT[Any, Any](any, any, any)(any, any, any, any)) thenReturn Future.failed(new GatewayTimeoutException("exception-message"))
      the [Exception] thrownBy callPut must have message "exception-message"
      verify(hmrcClient, times(3)).PUT[Any, Any](any, any, any) (any, any, any, any)

      withClue("log each retry") {
        verify(testLogger, times(2)).warn(
          eqTo("PPT_RETRY retrying: url proto://some:port/endpoint exception uk.gov.hmrc.http.GatewayTimeoutException: exception-message")
        )(any)
      }

      withClue("log when it gives up") {
        verify(testLogger, times(1)).warn(
          eqTo("PPT_RETRY gave up: url proto://some:port/endpoint exception uk.gov.hmrc.http.GatewayTimeoutException: exception-message")
        )(any)
      }
    }

    "stop retrying if successful after an exception" in {
      when(hmrcClient.PUT[Any, Any](any, any, any)(any, any, any, any)).thenReturn (
        Future.failed(new GatewayTimeoutException("exception-message")),
        Future.successful(HmrcResponse(200, ""))
      )
      callPut.status mustBe 200
      verify(hmrcClient, times(2)).PUT[Any, Any](any, any, any) (any, any, any, any)

      withClue("log when it succeeds") {
        verify(testLogger, times(1)).warn(
          eqTo("PPT_RETRY successful: url proto://some:port/endpoint correlation-id 00000000-0000-0001-0000-000000000002")
        )(any)
      }
    }

  }

}
