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
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{reset, verify, when}
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.Logger
import play.api.libs.json.{Json, OFormat}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse => HmrcResponse, StringContextOps}
import uk.gov.hmrc.http.client.{HttpClientV2 => HmrcClient, RequestBuilder}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Headers.buildEisHeader
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.net.URL
import org.mockito.ArgumentCaptor
import play.api.libs.json.JsValue

class EisHttpClientSpec extends PlaySpec with BeforeAndAfterEach with MockitoSugar {

  private val hmrcClient   = mock[HmrcClient]
  val mockRequestBuilder   = mock[RequestBuilder]
  private val appConfig    = mock[AppConfig]
  private val edgeOfSystem = mock[EdgeOfSystem]
  private val metrics      = mock[Metrics](RETURNS_DEEP_STUBS)
  private val timer        = mock[Timer.Context]

  private implicit val headerCarrier: HeaderCarrier = mock[HeaderCarrier]
  private val capturingLogger                       = new CapturingLogger

  private val eisHttpClient = new EisHttpClient(hmrcClient, appConfig, edgeOfSystem, metrics) {
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

    capturingLogger.clear()
  }

  override protected def afterEach(): Unit = {
    reset(hmrcClient, appConfig, edgeOfSystem, metrics, timer, headerCarrier, mockRequestBuilder)
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
        headerCaptor.getValue.asInstanceOf[Seq[(String, String)]] must contain allOf (
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
        headerCaptor.getValue.asInstanceOf[Seq[(String, String)]] must contain allOf (
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

  }

}
