/*
 * Copyright 2022 HM Revenue & Customs
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

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.Mockito.reset
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.{AuthTestSupport, WiremockItServer}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.DirectDebitResponse
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

class DirectDebitsItSpec extends PlaySpec
  with GuiceOneServerPerSuite
  with AuthTestSupport
  with BeforeAndAfterAll
  with BeforeAndAfterEach {

  implicit lazy val server: WiremockItServer = WiremockItServer()
  lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]
  val httpClient: DefaultHttpClient = app.injector.instanceOf[DefaultHttpClient]

  override lazy val app: Application = {
    SharedMetricRegistries.clear()
    GuiceApplicationBuilder()
      .configure(server.overrideConfig)
      .overrides(bind[AuthConnector].to(mockAuthConnector))
      .build()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector)
    server.server.resetAll()
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    server.start()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    server.stop()
  }

  private val noDirectDebit = Json.toJson(
    DirectDebitResponse(directDebitMandateFound = false, directDebitDetails = Seq())
  ).toString()

  "get" should {

    "return 200" in {
      withAuthorizedUser()
      server.server.stubFor(get(anyUrl()).willReturn(ok().withBody(noDirectDebit)))
      val url = s"http://localhost:$port/direct-debits/7777777"
      val response = await(wsClient.url(url).get())
      response.status mustBe 200
      server.server.verify(getRequestedFor(urlEqualTo("/cross-regime/direct-debits/PPT/ZPPT/7777777")))
    }

  }
}
