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

package returns

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.client.WireMock.*
import org.mockito.Mockito.reset
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK, UNAUTHORIZED}
import play.api.http.{HeaderNames, Status}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.ReturnWireMockServerSpec
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.ReturnsController.ReturnWithTaxRate
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.NrsTestData
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

import scala.concurrent.ExecutionContext

class HipReturnsISpec
  extends PlaySpec with GuiceOneServerPerSuite with ReturnWireMockServerSpec with AuthTestSupport with NrsTestData with BeforeAndAfterEach {
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  lazy val appConfig                   = app.injector.instanceOf[AppConfig]
  val httpClient: DefaultHttpClient    = app.injector.instanceOf[DefaultHttpClient]
  lazy val wsClient: WSClient          = app.injector.instanceOf[WSClient]
  private val periodKey                = "22C2"
  private val DesUrl                   = s"/plastic-packaging-tax/returns/PPT/$pptReference/$periodKey"
  private val HipUrl                   = s"/etmp/RESTAdapter/plastic-packaging-tax/returns/PPT/$pptReference/$periodKey"
  private val validGetReturnDisplayUrl = s"http://localhost:$port/returns-submission/$pptReference/$periodKey"
  private val submitReturnUrl          = s"http://localhost:$port/returns-submission/$pptReference"
  private val obligationDesRequest     = s"/enterprise/obligation-data/zppt/$pptReference/PPT?status=O"
  private val balanceEISURL            = s"/plastic-packaging-tax/export-credits/PPT/$pptReference"
  private lazy val cacheRepository     = mock[SessionRepository]

  lazy val returnsConnectorUrl =
    if (appConfig.hipReturns) HipUrl
    else DesUrl

  lazy val configForcingHipEndpoints: Map[String, Any] = wireMock.overrideConfig + ("features.hip.returns" -> true)

  override lazy val app: Application = {
    wireMock.start()
    SharedMetricRegistries.clear()
    GuiceApplicationBuilder()
      .configure(configForcingHipEndpoints)
      .overrides(bind[AuthConnector].to(mockAuthConnector), bind[SessionRepository].to(cacheRepository))
      .build()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector, cacheRepository)
    wireMock.reset()
  }

  "return 200 when getting return details" in {
    withAuthorizedUser()
    stubReturnDisplayResponse(true)

    val response = await(wsClient.url(validGetReturnDisplayUrl).get())

    response.status mustBe OK
  }

  "return display details" in {
    withAuthorizedUser()
    stubReturnDisplayResponse(true)

    val response = await(wsClient.url(validGetReturnDisplayUrl).get())
    val expected = ReturnWithTaxRate(Json.parse(displayApiResponse), 0.2)

    response.json mustBe Json.toJson(expected)
  }

  "return an error if DES API fails when getting return" in {
    withAuthorizedUser()
    stubReturnDisplayErrorResponse()

    val response = await(wsClient.url(validGetReturnDisplayUrl).get())

    response.status mustBe INTERNAL_SERVER_ERROR
  }

  "return Unauthorized when getting return" in {
    withUnauthorizedUser(new RuntimeException)

    val response = await(wsClient.url(validGetReturnDisplayUrl).get())

    response.status mustBe UNAUTHORIZED
  }

  private def stubReturnDisplayResponse(withSuccess: Boolean): Unit = {
    val body = if(withSuccess) displayApiResponseWithSuccess(displayApiResponse) else displayApiResponse
    wireMock.stubFor(
      get(HipUrl)
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withHeader(HeaderNames.AUTHORIZATION, "Gino")
            .withBody(body)
        )
    )
  }

    private def stubReturnDisplayErrorResponse(): Unit =
      wireMock.stubFor(
        get(HipUrl)
          .willReturn(
            aResponse()
              .withStatus(Status.INTERNAL_SERVER_ERROR)
          )
      )

  def displayApiResponseWithSuccess(inner: String) = Json.obj("success" -> Json.parse(inner)).toString

  def displayApiResponse: String =
  """
    |{
    |  "processingDate": "2022-07-03T09:30:47Z",
    |  "idDetails": {
    |    "pptReferenceNumber": "XMPPT0000000003",
    |    "submissionId": "123456789012"
    |  },
    |  "chargeDetails": {
    |    "periodKey": "22C2",
    |    "chargeReference": "XY007000075425",
    |    "periodFrom": "2022-04-01",
    |    "periodTo": "2022-06-30",
    |    "receiptDate": "2022-09-03T09:30:47Z",
    |    "returnType": "Amend"
    |  },
    |  "returnDetails": {
    |    "manufacturedWeight": 250,
    |    "importedWeight": 150,
    |    "totalNotLiable": 180,
    |    "humanMedicines": 50,
    |    "directExports": 60,
    |    "recycledPlastic": 70,
    |    "creditForPeriod": 12.13,
    |    "debitForPeriod": 0,
    |    "totalWeight": 220,
    |    "taxDue": 44
    |  }
    |}
    """.stripMargin

}
