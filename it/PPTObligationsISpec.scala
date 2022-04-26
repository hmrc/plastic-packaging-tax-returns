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
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get}
import org.mockito.Mockito.reset
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK, UNAUTHORIZED}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsArray, JsFalse, JsNumber, JsObject, JsString, JsValue, Json, OWrites}
import play.api.libs.ws.WSClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.{AuthTestSupport, WiremockItServer}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.ObligationStatus.ObligationStatus
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{ObligationStatus, _}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.PPTObligations
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

import java.time.LocalDate
import scala.language.implicitConversions

class PPTObligationsISpec
    extends PlaySpec with GuiceOneServerPerSuite with AuthTestSupport with BeforeAndAfterAll with BeforeAndAfterEach {

  implicit def toJsString(s: String): JsString = JsString(s)
  def jsObject(t: (String, JsValue)*): JsObject = JsObject(t)

  val httpClient: DefaultHttpClient          = app.injector.instanceOf[DefaultHttpClient]
  implicit lazy val server: WiremockItServer = WiremockItServer()
  lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]

  val fromDate: LocalDate = LocalDate.of(2022, 4, 1)
  val toDate: LocalDate = LocalDate.now()
  val open: ObligationStatus.Value = ObligationStatus.OPEN
  val fulfilled: ObligationStatus.Value = ObligationStatus.FULFILLED
  def obligationDetail(status: ObligationStatus): ObligationDetail = ObligationDetail(
    status = status,
    inboundCorrespondenceFromDate = fromDate,
    inboundCorrespondenceToDate = LocalDate.of(2022,6,30),
    inboundCorrespondenceDateReceived = LocalDate.of(2022,6,30),
    inboundCorrespondenceDueDate = LocalDate.of(2022,7,29),
    periodKey = "22C2"
  )

  def DESUrl(status: ObligationStatus) =
    s"/enterprise/obligation-data/zppt/$pptReference/PPT?fromDate=$fromDate&toDate=$toDate&status=${status.toString}"

  val pptOpenUrl = s"http://localhost:$port/obligations/open/$pptReference"
  val pptFulfilledUrl = s"http://localhost:$port/obligations/fulfilled/$pptReference"

  val obligationResponse: ObligationDataResponse = ObligationDataResponse(obligations =
    Seq(
      Obligation(identification =
                   Identification(incomeSourceType = "ITR SA", referenceNumber = pptReference, referenceType = "PPT"),
                 obligationDetails = Seq.empty
      )
    )
  )

  def obligationResponseWithObligationDetails(status: ObligationStatus): ObligationDataResponse = ObligationDataResponse(obligations =
    Seq(
      Obligation(identification =
        Identification(incomeSourceType = "ITR SA", referenceNumber = pptReference, referenceType = "PPT"),
        obligationDetails = Seq(obligationDetail(status))
      )
    )
  )

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
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    server.start()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    server.stop()
  }

  "GET /obligations/open/:pptReference" must {
    "return 200" in {
      withAuthorizedUser()
      stubObligationDataRequest(open, obligationResponse)

      val response = await(wsClient.url(pptOpenUrl).get())

      response.status mustBe OK
      response.json mustBe Json.toJson(PPTObligations(None, None, 0, isNextObligationDue = false, displaySubmitReturnsLink = false))
    }

    "return 200 with obligationDetails" in {
      withAuthorizedUser()
      stubObligationDataRequest(open, obligationResponseWithObligationDetails(open))

      val response = await(wsClient.url(pptOpenUrl).get())

      response.status mustBe OK
      response.json mustBe jsObject(
        "nextObligation" -> jsObject(
          "periodKey" -> "22C2",
          "fromDate" -> "2022-04-01",
          "toDate" -> "2022-06-30",
          "dueDate" -> "2022-07-29"
        ),
        "overdueObligationCount" -> JsNumber(0),
        "isNextObligationDue" -> JsFalse,
        "displaySubmitReturnsLink" -> JsFalse
      )
    }

    "should return Unauthorised" in {
      withUnauthorizedUser(new RuntimeException)

      val response = await(wsClient.url(pptOpenUrl).get())

      response.status mustBe UNAUTHORIZED
    }

    "should return 500" in {
      withAuthorizedUser()
      stubInvalidObligationDataRequest(open)

      val response = await(wsClient.url(pptOpenUrl).get())

      response.status mustBe INTERNAL_SERVER_ERROR
    }
  }

  "GET /obligations/fulfilled/:pptReference" must {
    "return 200" in {
      withAuthorizedUser()
      stubObligationDataRequest(fulfilled, obligationResponse)

      val response = await(wsClient.url(pptFulfilledUrl).get())

      response.status mustBe OK
      response.json mustBe Json.toJson(Seq.empty[ObligationDetail])
    }

    "return 200 with obligationDetails" in {
      withAuthorizedUser()
      stubObligationDataRequest(fulfilled, obligationResponseWithObligationDetails(fulfilled))

      val response = await(wsClient.url(pptFulfilledUrl).get())

      response.status mustBe OK
      response.json mustBe JsArray(Seq(
        jsObject(
          "periodKey" -> "22C2",
          "fromDate" -> "2022-04-01",
          "toDate" -> "2022-06-30",
          "dueDate" -> "2022-07-29"
        )
      ))
    }

    "should return Unauthorised" in {
      withUnauthorizedUser(new RuntimeException)

      val response = await(wsClient.url(pptFulfilledUrl).get())

      response.status mustBe UNAUTHORIZED
    }

    "should return 500" in {
      withAuthorizedUser()
      stubInvalidObligationDataRequest(fulfilled)

      val response = await(wsClient.url(pptFulfilledUrl).get())

      response.status mustBe INTERNAL_SERVER_ERROR
    }
  }

  private def stubObligationDataRequest(status: ObligationStatus, response: ObligationDataResponse): Unit = {
    implicit val odWrites: OWrites[ObligationDetail] = Json.writes[ObligationDetail]
    implicit val oWrites: OWrites[Obligation] = Json.writes[Obligation]
    val writes: OWrites[ObligationDataResponse] = Json.writes[ObligationDataResponse]
    server.stubFor(
      get(DESUrl(status))
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(Json.toJson(response)(writes).toString())
        )
    )
  }

  private def stubInvalidObligationDataRequest(status: ObligationStatus): Unit =
    server.stubFor(
      get(DESUrl(status))
        .willReturn(
          aResponse()
            .withStatus(Status.INTERNAL_SERVER_ERROR)
        )
    )

}
