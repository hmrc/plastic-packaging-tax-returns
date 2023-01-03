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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it

import com.codahale.metrics.{MetricFilter, SharedMetricRegistries, Timer}
import com.github.tomakehurst.wiremock.client.{VerificationException, WireMock}
import com.github.tomakehurst.wiremock.client.WireMock.{equalToJson, postRequestedFor, urlEqualTo, verify}
import com.kenshoo.play.metrics.Metrics
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.DefaultAwaitTimeout
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

import scala.concurrent.ExecutionContext

class ConnectorISpec extends WiremockTestServer with GuiceOneAppPerSuite with DefaultAwaitTimeout {

  protected lazy val httpClient: DefaultHttpClient = app.injector.instanceOf[DefaultHttpClient]
  protected lazy val metrics: Metrics              = app.injector.instanceOf[Metrics]

  protected implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  protected implicit val hc: HeaderCarrier    = HeaderCarrier()

  override def fakeApplication(): Application = {
    SharedMetricRegistries.clear()
    new GuiceApplicationBuilder().configure(overrideConfig).build()
  }

  def overrideConfig: Map[String, Any] =
    Map("microservice.services.eis.host" -> wireHost,
        "microservice.services.eis.port" -> wirePort,
        "microservice.services.nrs.host" -> wireHost,
        "microservice.services.nrs.port" -> wirePort,
        "microservice.services.des.host" -> wireHost,
        "microservice.services.des.port" -> wirePort,
        "auditing.consumer.baseUri.port" -> wirePort,
        "auditing.enabled" -> false
    )

  def getTimer(name: String): Timer =
    SharedMetricRegistries
      .getOrCreate("plastic-packaging-tax-returns")
      .getTimers(MetricFilter.startsWith(name))
      .get(name)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    WireMock.configureFor(wireHost, wirePort)
    wiremock.start()
  }

  override protected def beforeEach(): Unit =
    SharedMetricRegistries.clear()

  override protected def afterAll(): Unit = {
    super.afterAll()
    wiremock.stop()
  }

  protected def eventSendToAudit(url: String, eventType: String, body: String): Boolean =
    try {
      verify(
        postRequestedFor(urlEqualTo(url))
          .withRequestBody(equalToJson(s"""{
                                          |                  "auditSource": "plastic-packaging-tax-returns",
                                          |                  "auditType": "$eventType",
                                          |                  "eventId": "$${json-unit.any-string}",
                                          |                  "tags": {
                                          |                    "clientIP": "-",
                                          |                    "path": "-",
                                          |                    "X-Session-ID": "-",
                                          |                    "Akamai-Reputation": "-",
                                          |                    "X-Request-ID": "-",
                                          |                    "deviceID": "-",
                                          |                    "clientPort": "-"
                                          |                  },
                                          |                  "detail": $body,
                                          |                  "generatedAt": "$${json-unit.any-string}"
                                          |                }""".stripMargin, true, true))
      )
      true
    } catch {
      case _: VerificationException => false
    }

}
