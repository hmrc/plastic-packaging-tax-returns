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
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND, OK, UNAUTHORIZED}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.{AuthTestSupport, WiremockItServer}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise._
import uk.gov.hmrc.plasticpackagingtaxreturns.models.PPTObligations
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

import java.time.LocalDate
import scala.language.implicitConversions

class PPTObligationsISpec extends PlaySpec with GuiceOneServerPerSuite with AuthTestSupport with BeforeAndAfterAll with BeforeAndAfterEach {

  implicit def toJsString(s: String): JsString  = JsString(s)
  def jsObject(t: (String, JsValue)*): JsObject = JsObject(t)

  val httpClient: DefaultHttpClient          = app.injector.instanceOf[DefaultHttpClient]
  implicit lazy val server: WiremockItServer = WiremockItServer()

  lazy val wsClient: WSClient                = app.injector.instanceOf[WSClient]

  val open: ObligationStatus.Value      = ObligationStatus.OPEN
  val fulfilled: ObligationStatus.Value = ObligationStatus.FULFILLED

  val pptOpenUrl      = s"http://localhost:$port/obligations/open/$pptReference"
  val pptFulfilledUrl = s"http://localhost:$port/obligations/fulfilled/$pptReference"


  private val DESnotFoundResponse = """{"code": "NOT_FOUND", "reason": "The remote endpoint has indicated that no associated data found."}"""

  private val noObligations = ObligationDataResponse(obligations =
    Seq(
      Obligation(
        identification = Some(Identification(incomeSourceType = Some("ITR SA"), referenceNumber = pptReference, referenceType = "PPT")),
        obligationDetails = Seq.empty
      )
    )
  )

  val oneObligation: ObligationDataResponse = ObligationDataResponse(obligations =
    Seq(
      Obligation(
        identification =
          Some(Identification(incomeSourceType = Some("ITR SA"), referenceNumber = pptReference, referenceType = "PPT")),
        obligationDetails = Seq(
          ObligationDetail(
            status = ObligationStatus.UNKNOWN,                       // Don't care about this here
            inboundCorrespondenceDateReceived = Some(LocalDate.MIN), // Don't care about this here
            inboundCorrespondenceFromDate = LocalDate.of(2022, 4, 1),
            inboundCorrespondenceToDate = LocalDate.of(2022, 6, 30),
            inboundCorrespondenceDueDate = LocalDate.of(2022, 7, 29),
            periodKey = "22C2"
          )
        )
      )
    )
  )

  override lazy val app: Application = {
    server.start()
    SharedMetricRegistries.clear()
    GuiceApplicationBuilder()
      .configure(server.overrideConfig)
      .overrides(bind[AuthConnector].to(mockAuthConnector))
      .build()
  }


  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector)
    server.reset()
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

    "not send dates when fetching open obligations" in {
      withAuthorizedUser()
      stubWillReturn(noObligations)

      await(wsClient.url(pptOpenUrl).get())

      server.wireMockServer.verify(getRequestedFor(urlEqualTo(s"/enterprise/obligation-data/zppt/$pptReference/PPT?status=O")))
    }

    "return 200 with no obligations" in {
      withAuthorizedUser()
      stubWillReturn(noObligations)

      val response = await(wsClient.url(pptOpenUrl).get())

      response.status mustBe OK
      response.json mustBe Json.toJson(PPTObligations(None, None, 0, isNextObligationDue = false, displaySubmitReturnsLink = false))
    }

    "return 200 with obligationDetails" in {
      withAuthorizedUser()
      stubWillReturn(oneObligation)

      val response = await(wsClient.url(pptOpenUrl).get())

      response.status mustBe OK
      response.json mustBe jsObject(
        "nextObligation"           -> jsObject("periodKey" -> "22C2", "fromDate" -> "2022-04-01", "toDate" -> "2022-06-30", "dueDate" -> "2022-07-29"),
        "overdueObligationCount"   -> JsNumber(0),
        "isNextObligationDue"      -> JsFalse,
        "displaySubmitReturnsLink" -> JsFalse
      )
    }

    "should return 200 with empty data" in {
      withAuthorizedUser()
      stubNotFound(DESnotFoundResponse)

      val response = await(wsClient.url(pptOpenUrl).get())

      response.status mustBe OK
      response.json mustBe Json.toJson(PPTObligations(None, None, 0, false, false))
    }

    "should return 404 if not Found" in {
      withAuthorizedUser()
      stubNotFound("{}")

      val response = await(wsClient.url(pptOpenUrl).get())

      response.status mustBe NOT_FOUND
    }


    "should return 404 if not Found with wrong body" in {
      withAuthorizedUser()
      stubNotFound("""{"code": "PAN", "reason": "ANDY"}""")

      val response = await(wsClient.url(pptOpenUrl).get())

      response.status mustBe NOT_FOUND
    }

    "should return Unauthorised" in {
      withUnauthorizedUser(new RuntimeException)

      val response = await(wsClient.url(pptOpenUrl).get())

      response.status mustBe UNAUTHORIZED
    }

    "should return 500" in {
      withAuthorizedUser()
      server.stubFor(get(anyUrl()).willReturn(serverError()))

      val response = await(wsClient.url(pptOpenUrl).get())
      response.status mustBe INTERNAL_SERVER_ERROR
    }
  }

  "GET /obligations/fulfilled/:pptReference" must {

    "call the Get Obligations api" in {
      withAuthorizedUser()
      stubWillReturn(noObligations)
      await(wsClient.url(pptFulfilledUrl).get())

      val today       = LocalDate.now()
      val expectedUrl = s"/enterprise/obligation-data/zppt/$pptReference/PPT?from=2022-04-01&to=$today&status=F"
      server.wireMockServer.verify(getRequestedFor(urlEqualTo(expectedUrl)))
    }

    "return 200 with no obligations" in {
      withAuthorizedUser()
      stubWillReturn(noObligations)

      val response = await(wsClient.url(pptFulfilledUrl).get())

      response.status mustBe OK
      response.json mustBe Json.toJson(Seq.empty[ObligationDetail])
    }

    "return 200 with obligationDetails" in {
      withAuthorizedUser()
      stubWillReturn(oneObligation)

      val response = await(wsClient.url(pptFulfilledUrl).get())

      response.status mustBe OK
      response.json mustBe JsArray(
        Seq(jsObject("periodKey" -> "22C2", "fromDate" -> "2022-04-01", "toDate" -> "2022-06-30", "dueDate" -> "2022-07-29"))
      )
    }

    "should return 200 with empty data" in {
      withAuthorizedUser()
      stubNotFound(DESnotFoundResponse)

      val response = await(wsClient.url(pptFulfilledUrl).get())

      response.status mustBe OK
      response.json mustBe Json.toJson(Seq.empty[ObligationDetail])
    }

    "should return Unauthorised" in {
      withUnauthorizedUser(new RuntimeException)

      val response = await(wsClient.url(pptFulfilledUrl).get())

      response.status mustBe UNAUTHORIZED
    }
    "should return Not Found" in {
      withAuthorizedUser()
      stubNotFound("{}")

      val response = await(wsClient.url(pptFulfilledUrl).get())

      response.status mustBe NOT_FOUND
    }

    "should return 500" in {
      withAuthorizedUser()
      server.stubFor(get(anyUrl()).willReturn(serverError()))

      val response = await(wsClient.url(pptFulfilledUrl).get())

      response.status mustBe INTERNAL_SERVER_ERROR
    }
  }

  private def stubNotFound(body: String): Any =
    server.stubFor(
      get(anyUrl()).willReturn(notFound().withBody(body))
    )

  private def stubWillReturn(response: ObligationDataResponse): Unit = {
    implicit val odWrites: OWrites[ObligationDetail] = Json.writes[ObligationDetail]
    implicit val oWrites: OWrites[Obligation]        = Json.writes[Obligation]
    val writes: OWrites[ObligationDataResponse]      = Json.writes[ObligationDataResponse]
    val jsonString                                   = Json.toJson(response)(writes).toString()
    server.stubFor(get(anyUrl()).willReturn(ok().withBody(jsonString)))
  }

}
