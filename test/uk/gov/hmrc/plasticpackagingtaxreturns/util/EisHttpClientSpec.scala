/*
 * Copyright 2026 HM Revenue & Customs
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
import org.apache.pekko.Done
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{times, verify, when, verifyNoMoreInteractions, reset}
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.Logger
import play.api.libs.concurrent.Futures
import play.api.libs.json.{Json, OFormat}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.{GatewayTimeoutException, HeaderCarrier, HttpResponse => HmrcResponse, StringContextOps}
import uk.gov.hmrc.http.client.{HttpClientV2 => HmrcClient, RequestBuilder }
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EisHttpClient.retryDelayInMillisecond
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Headers.buildEisHeader
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import java.net.URL
import org.mockito.ArgumentCaptor
import play.api.libs.json.JsValue

class EisHttpClientSpec extends PlaySpec with BeforeAndAfterEach with MockitoSugar {

  private val hmrcClient                            = mock[HmrcClient]
  val mockRequestBuilder                            = mock[RequestBuilder]
  private val appConfig                             = mock[AppConfig]
  private val edgeOfSystem                          = mock[EdgeOfSystem]
  private val metrics                               = mock[Metrics](RETURNS_DEEP_STUBS)
  private val futures                               = mock[Futures]
  private val timer                                 = mock[Timer.Context]

  private implicit val headerCarrier: HeaderCarrier = mock[HeaderCarrier]
  private val capturingLogger                       = new CapturingLogger

  private val eisHttpClient = new EisHttpClient(hmrcClient, appConfig, edgeOfSystem, metrics, futures) {
    protected override val logger: Logger = capturingLogger
  }

  case class ExampleModel(vitalData: Int = 1)

  private val exampleModel                            = ExampleModel()
  private implicit val formats: OFormat[ExampleModel] = Json.format[ExampleModel]

  private val headerFn = (correlationId: String, _: AppConfig) =>
    Seq(
      "Environment"   -> "space",
      "Accept"        -> "application/json",
      "Authorization" -> "do-come-in",
      "CorrelationId" -> correlationId
    )

  override protected def beforeEach(): Unit = {
    super.beforeEach()


    when(hmrcClient.put(any())(any())).thenReturn(mockRequestBuilder)
    when(hmrcClient.get(any())(any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.transform(any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.setHeader(any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.execute[HmrcResponse](any(), any())).thenReturn(Future.successful(HmrcResponse(
      200,
      "{}"
    )))
    when(appConfig.eisEnvironment) thenReturn "space"
    when(appConfig.bearerToken) thenReturn "do-come-in"
    when(edgeOfSystem.createUuid).thenReturn(
      UUID.fromString("00000000-0000-0001-0000-000000000001"),
      UUID.fromString("00000000-0000-0001-0000-000000000002"),
      UUID.fromString("00000000-0000-0001-0000-000000000003")
    )
    when(metrics.defaultRegistry.timer(any).time()) thenReturn timer
    when(futures.delay(any)) thenReturn Future.successful(Done)

    capturingLogger.clear()
  }

  override protected def afterEach(): Unit = {
    reset(hmrcClient, appConfig, edgeOfSystem, metrics, futures, timer, headerCarrier, mockRequestBuilder)
    super.afterEach()
  }

  private def callPut =
    await {
      eisHttpClient.put("http://some-host:8080/endpoint", exampleModel, "tick.tick", buildEisHeader)
    }

  "put" should {

    "send a request" in {
      val response = callPut
      response mustBe EisHttpResponse(200, "{}", "00000000-0000-0001-0000-000000000001")

      val urlCaptor = ArgumentCaptor.forClass(classOf[URL])
      verify(hmrcClient).put(urlCaptor.capture())(any())

      verify(hmrcClient).put(eqTo(url"http://some-host:8080/endpoint"))(any())
      val bodyCaptor = ArgumentCaptor.forClass(classOf[JsValue])
      verify(mockRequestBuilder).withBody(bodyCaptor.capture())(any(), any(), any())
      bodyCaptor.getValue mustBe Json.toJson(exampleModel)  

      withClue("with these headers") {
        val headerCaptor = ArgumentCaptor.forClass(classOf[(String, String)])
        verify(mockRequestBuilder).setHeader(headerCaptor.capture())
        headerCaptor.getValue.asInstanceOf[Seq[(String, String)]] must contain allOf(
          "Environment"   -> "space",
          "Accept"        -> "application/json",
          "CorrelationId" -> "00000000-0000-0001-0000-000000000001",
          "Authorization" -> "do-come-in"
        )
      }

      withClue("using these implicits") {
        verify(hmrcClient).put(any())(eqTo(headerCarrier))
        verify(mockRequestBuilder).withBody(any())(any(), any(), any()) // Writes is hard to eqTo
        verify(mockRequestBuilder).execute[HmrcResponse](any(), eqTo(global))
      }
    }

    "handle responses" when {
      "status is 2xx" in {
      
        when(mockRequestBuilder.execute[HmrcResponse](any(), any())) thenReturn Future.successful(HmrcResponse(
          200,
          """{"a": "b"}"""
        ))
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

      eisHttpClient.get("http://some-host:8080/any/url", Seq("a" -> "b"), "timer-name", headerFn)

      verify(hmrcClient).get(eqTo(URL("http://some-host:8080/any/url")))(any())
      verify(mockRequestBuilder).transform(any())

      val headerCaptor = ArgumentCaptor.forClass(classOf[(String, String)])
      verify(mockRequestBuilder).setHeader(headerCaptor.capture())
      withClue("with these headers") {
        headerCaptor.getValue.asInstanceOf[Seq[(String, String)]] must contain allOf(
          "Environment"   -> "space",
          "Accept"        -> "application/json",
          "Authorization" -> "do-come-in",
          "CorrelationId" -> "00000000-0000-0001-0000-000000000001"
        )
      } 
    }

    "return an EisHttpResponse" in {
      when(mockRequestBuilder.execute[HmrcResponse](any(), any()))
        .thenReturn(Future.successful(HmrcResponse(200, """{"a": "b"}""")))
      val result = await(eisHttpClient.get("http://some-host:8080/any/url", Seq.empty, "timer-name", headerFn))

      result mustBe EisHttpResponse(200, """{"a": "b"}""", "00000000-0000-0001-0000-000000000001")
    }

    "time the transaction" in {
      when(mockRequestBuilder.execute[HmrcResponse](any(), any()))
        .thenReturn(Future.successful(HmrcResponse(200, """{"a": "b"}""")))

      await(eisHttpClient.get("http://some-host:8080/any/url", Seq.empty, "timer-name", headerFn))
      verify(metrics.defaultRegistry.timer(eqTo("tick.tick"))).time()
      verify(timer).stop()
    }

    "retry again if the first attempt fails" in {
      when(mockRequestBuilder.execute[HmrcResponse](any(), any())).thenReturn(
        Future.successful(HmrcResponse(500, "")),
        Future.successful(HmrcResponse(200, ""))
      )

      val response = await(eisHttpClient.get("http://some-host:8080/any/url", Seq.empty, "timer-name", headerFn))

      response.status mustBe 200
      verify(hmrcClient, times(2)).get(eqTo(url"http://some-host:8080/any/url"))(any())
      verify(mockRequestBuilder, times(2)).execute[HmrcResponse](any(), any())

      withClue("with a delay between attempt") {
        verify(futures).delay(retryDelayInMillisecond milliseconds)
      }
    }

    "retry eventually give up" in {
      when(mockRequestBuilder.execute[HmrcResponse](any(), any())).thenReturn(Future.successful(HmrcResponse(500, "")))
      val response = await(eisHttpClient.get("http://some-host:8080/any/url", Seq.empty, "timer-name", headerFn))
      response.status mustBe 500

      withClue("after trying 3 times") {
        verify(hmrcClient, times(3)).get(eqTo(url"http://some-host:8080/any/url"))(any())
        verify(mockRequestBuilder, times(3)).execute[HmrcResponse](any(), any())
        verify(futures, times(1)).delay(2000 milliseconds)
        verify(futures, times(1)).delay(4000 milliseconds)
      }
    }

    "retry use custom success criteria" in {
      when(hmrcClient.get(any())(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.setHeader(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[HmrcResponse](any(), any())).thenReturn(Future.successful(HmrcResponse(422, "")))

      val response = await {
        val isSuccessful = (response: EisHttpResponse) => response.status == 422
        eisHttpClient.get("http://some-host:8080/any/url", Seq.empty, "timer-name", headerFn, isSuccessful)
      }

      verify(hmrcClient, times(1)).get(any())(any())
      verify(mockRequestBuilder, times(1)).execute[HmrcResponse](any(), any())
      verifyNoMoreInteractions(futures)
      response.status mustBe 422
    }
  }

  "retry" should {

    "try again if the first attempt fails" in {
      val mockRequestBuilder = mock[RequestBuilder]
      when(hmrcClient.put(any())(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.setHeader(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[HmrcResponse](any(), any())).thenReturn(
        Future.successful(HmrcResponse(500, "")),
        Future.successful(HmrcResponse(200, ""))
      )

      val response = callPut
      verify(hmrcClient, times(2)).put(eqTo(url"http://some-host:8080/endpoint"))(any())
      val bodyCaptor = ArgumentCaptor.forClass(classOf[JsValue])
      verify(mockRequestBuilder, times(2)).withBody(bodyCaptor.capture())(any(), any(), any())
      bodyCaptor.getValue mustBe Json.toJson(exampleModel)  

      response.status mustBe 200

      withClue("with a delay between attempt") {
        verify(futures).delay(2000 milliseconds)
      }
    }

    "eventually give up" in {
      val mockRequestBuilder = mock[RequestBuilder]
      when(hmrcClient.put(any())(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.setHeader(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[HmrcResponse](any(), any())) thenReturn
        Future.successful(HmrcResponse(500, ""))
      val response = callPut
      response.status mustBe 500

      withClue("after trying 3 times") {
        verify(hmrcClient, times(3)).put(any())(any())
        verify(mockRequestBuilder, times(3)).execute[HmrcResponse](any(), any())
        verify(futures, times(1)).delay(2000 milliseconds)
        verify(futures, times(1)).delay(4000 milliseconds)
      }
    }

    "use custom success criteria" in {
      val mockRequestBuilder = mock[RequestBuilder]
      when(hmrcClient.put(any())(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.setHeader(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[HmrcResponse](any(), any())).thenReturn(Future.successful(HmrcResponse(
        422,
        ""
      )))

      val response = await {
        val isSuccessful = (response: EisHttpResponse) => response.status == 422
        eisHttpClient.put("http://some-host:8080/endpoint", exampleModel, "timer", buildEisHeader, isSuccessful)
      }

      verify(hmrcClient, times(1)).put(any())(any())
      verify(mockRequestBuilder, times(1)).execute[HmrcResponse](any(), any())
      verifyNoMoreInteractions(futures)
      response.status mustBe 422
    }

    "log a retry and its eventual success" in {
      val mockRequestBuilder = mock[RequestBuilder]
      when(hmrcClient.put(any())(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.setHeader(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[HmrcResponse](any(), any())).thenReturn(
        Future.successful(HmrcResponse(500, "")),
        Future.successful(HmrcResponse(200, ""))
      )

      callPut

      capturingLogger.warnings.head must include("PPT_RETRY retrying: url http://some-host:8080/endpoint status 500 correlation-id 00000000-0000-0001-0000-000000000001")
      capturingLogger.warnings(1) must include("PPT_RETRY successful: url http://some-host:8080/endpoint correlation-id 00000000-0000-0001-0000-000000000002")
      capturingLogger.warnings must have size 2
    }

    "not log if successful first time" in {
      callPut
      capturingLogger.warnings mustBe empty
    }

    "log when giving up" in {
      val mockRequestBuilder = mock[RequestBuilder]
      when(hmrcClient.put(any())(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.setHeader(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[HmrcResponse](any(), any())).thenReturn(Future.successful(HmrcResponse(
        500,
        ""
      )))

      callPut
      capturingLogger.warnings.head must include("PPT_RETRY retrying: url http://some-host:8080/endpoint status 500 correlation-id 00000000-0000-0001-0000-000000000001")
      capturingLogger.warnings(1) must include("PPT_RETRY retrying: url http://some-host:8080/endpoint status 500 correlation-id 00000000-0000-0001-0000-000000000002")
      capturingLogger.warnings(2) must include("PPT_RETRY gave up: url http://some-host:8080/endpoint status 500 correlation-id 00000000-0000-0001-0000-000000000003")
      capturingLogger.warnings must have size 3
    }

    "retry after an exception" in {
      val mockRequestBuilder = mock[RequestBuilder]
      when(hmrcClient.put(any())(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.setHeader(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[HmrcResponse](any(), any())) thenReturn Future.failed(
        new GatewayTimeoutException("exception-message")
      )
      the[Exception] thrownBy callPut must have message "exception-message"

      verify(hmrcClient, times(3)).put(any())(any())
      verify(mockRequestBuilder, times(3)).execute[HmrcResponse](any(), any())

      withClue("log each retry") {
        capturingLogger.warnings.count(_.startsWith("PPT_RETRY retrying: url http://some-host:8080/endpoint exception uk.gov.hmrc.http.GatewayTimeoutException: exception-message")) mustBe 2
      }

      withClue("log when it gives up") {
        capturingLogger.warnings.count(_.startsWith("PPT_RETRY gave up: url http://some-host:8080/endpoint exception uk.gov.hmrc.http.GatewayTimeoutException: exception-message")) mustBe 1 
      }
    }

    "stop retrying if successful after an exception" in {
      val mockRequestBuilder = mock[RequestBuilder]
      when(hmrcClient.put(any())(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.setHeader(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[HmrcResponse](any(), any())).thenReturn(
        Future.failed(new GatewayTimeoutException("exception-message")),
        Future.successful(HmrcResponse(200, ""))
      )
      callPut.status mustBe 200
      verify(hmrcClient, times(2)).put(any())(any())
      verify(mockRequestBuilder, times(2)).execute[HmrcResponse](any(), any())

      withClue("log when it succeeds") {

        capturingLogger.warnings.count(_.startsWith("PPT_RETRY successful: url http://some-host:8080/endpoint correlation-id 00000000-0000-0001-0000-000000000002")) mustBe 1
      }
    }

  }

}
