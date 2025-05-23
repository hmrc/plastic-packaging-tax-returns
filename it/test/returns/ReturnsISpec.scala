/*
 * Copyright 2025 HM Revenue & Customs
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
import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.MockitoSugar.reset
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK, UNAUTHORIZED}
import play.api.http.{HeaderNames, Status}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json.obj
import play.api.libs.json.{Json, OWrites}
import play.api.libs.ws.WSClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.{ObligationSpecHelper, ReturnWireMockServerSpec}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise._
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.ReturnsController.ReturnWithTaxRate
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.NrsTestData
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService
import uk.gov.hmrc.plasticpackagingtaxreturns.support.ReturnTestHelper
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

import scala.concurrent.{ExecutionContext, Future}

class ReturnsISpec
    extends PlaySpec with GuiceOneServerPerSuite with ReturnWireMockServerSpec with AuthTestSupport with NrsTestData with BeforeAndAfterEach {
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  lazy val appConfig                   = app.injector.instanceOf[AppConfig]
  val httpClient: DefaultHttpClient    = app.injector.instanceOf[DefaultHttpClient]
  lazy val wsClient: WSClient          = app.injector.instanceOf[WSClient]
  private val periodKey                = "22C2"
  private val DesUrl                   = s"/plastic-packaging-tax/returns/PPT/$pptReference/$periodKey"
  private val validGetReturnDisplayUrl = s"http://localhost:$port/returns-submission/$pptReference/$periodKey"
  private val submitReturnUrl          = s"http://localhost:$port/returns-submission/$pptReference"
  private val obligationDesRequest     = s"/enterprise/obligation-data/zppt/$pptReference/PPT?status=O"
  private val balanceEISURL            = s"/plastic-packaging-tax/export-credits/PPT/$pptReference"
  private lazy val cacheRepository     = mock[SessionRepository]

  override lazy val app: Application = {
    wireMock.start()
    SharedMetricRegistries.clear()
    GuiceApplicationBuilder()
      .configure(wireMock.overrideConfig)
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
    stubReturnDisplayResponse()

    val response = await(wsClient.url(validGetReturnDisplayUrl).get())

    response.status mustBe OK
  }

  "return display details" in {
    withAuthorizedUser()
    stubReturnDisplayResponse()

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

  "return 200 when submitting return" in {
    withAuthorizedUser()
    mockAuthorization(NonRepudiationService.nonRepudiationIdentityRetrievals, testAuthRetrievals)
    setUpStub()
    setUpMocks()

    val response = await(wsClient.url(submitReturnUrl).withHttpHeaders("Authorization" -> "TOKEN").post(pptReference))

    response.status mustBe OK
  }

  "success return submit with nrs success" in {
    withAuthorizedUser()
    mockAuthorization(NonRepudiationService.nonRepudiationIdentityRetrievals, testAuthRetrievals)
    setUpStub()
    setUpMocks()

    val response = await(wsClient.url(submitReturnUrl).withHttpHeaders("Authorization" -> "TOKEN").post(pptReference))

    response.status mustBe OK
    response.json mustBe Json.toJson(aReturnWithNrs())
  }

  "success return submit with nrs failure" in {
    withAuthorizedUser()
    mockAuthorization(NonRepudiationService.nonRepudiationIdentityRetrievals, testAuthRetrievals)
    setUpStub()
    setUpMocks()
    stubNrsFailingRequest

    val response = await(wsClient.url(submitReturnUrl).withHttpHeaders("Authorization" -> "TOKEN").post(pptReference))

    response.status mustBe OK
    response.json mustBe Json.toJson(aReturnWithNrsFailure().copy(nrsFailureReason = "exception"))
  }

  "return an error when submitting return" in {
    withAuthorizedUser()
    mockAuthorization(NonRepudiationService.nonRepudiationIdentityRetrievals, testAuthRetrievals)
    stubObligationDesRequest(INTERNAL_SERVER_ERROR)
    when(cacheRepository.get(any())).thenReturn(Future.successful(Option(UserAnswers("id").copy(data = ReturnTestHelper.returnWithCreditsDataJson))))

    val response = await(wsClient.url(submitReturnUrl).withHttpHeaders("Authorization" -> "TOKEN").post(pptReference))

    response.status mustBe INTERNAL_SERVER_ERROR
  }

  "return unauthorised when submitting return" in {
    withUnauthorizedUser(new RuntimeException)

    val response = await(wsClient.url(submitReturnUrl).withHttpHeaders("Authorization" -> "TOKEN").post(pptReference))

    response.status mustBe UNAUTHORIZED
  }

  "handle ETMP saying return was already received" in {
    withAuthorizedUser()
    mockAuthorization(NonRepudiationService.nonRepudiationIdentityRetrievals, testAuthRetrievals)
    setUpStub()
    setUpMocks()

    // Taken from lIve - response when ETMP has already seen
    wireMock.stubFor(
      put("/plastic-packaging-tax/returns/PPT/7777777").willReturn(
        status(422).withBody("""
          {
            "failures" : [ {
              "code" : "TAX_OBLIGATION_ALREADY_FULFILLED",
              "reason" : "The remote endpoint has indicated that Tax obligation already fulfilled."
            } ]
          }""")
      )
    )

    val response = await(wsClient.url(submitReturnUrl).withHttpHeaders("Authorization" -> "TOKEN").post(pptReference))

    withClue("there should be no retries") {
      wireMock.verify(count = 1, putRequestedFor(urlEqualTo("/plastic-packaging-tax/returns/PPT/7777777")))
    }

    response.status mustBe 208 // Internally used status for an already submitted return
    Json.parse(response.body) mustBe obj("returnAlreadyReceived" -> "22C2")
  }

  "retry 3 times Des if api call fails where getting the returns" in {
    withAuthorizedUser()
    stubReturnDisplayErrorResponse()
    await(wsClient.url(validGetReturnDisplayUrl).get())
    wireMock.verify(3, getRequestedFor(urlEqualTo(s"/plastic-packaging-tax/returns/PPT/$pptReference/$periodKey")))
  }

  "retry 1 times if des api call successful" in {
    withAuthorizedUser()
    stubReturnDisplayResponse()
    await(wsClient.url(validGetReturnDisplayUrl).get())
    wireMock.verify(1, getRequestedFor(urlEqualTo(s"/plastic-packaging-tax/returns/PPT/$pptReference/$periodKey")))
  }

  "use EIS header" in {
    withAuthorizedUser()
    stubReturnDisplayResponse()
    await(wsClient.url(validGetReturnDisplayUrl).get())

    wireMock.verify(
      getRequestedFor(urlEqualTo(s"/plastic-packaging-tax/returns/PPT/$pptReference/$periodKey"))
        .withHeader(HeaderNames.AUTHORIZATION, equalTo("Bearer eis-test123456"))
    )
  }

  private def setUpStub() = {
    stubObligationDesRequest()
    stubGetBalanceEISRequest
    stubSubmitReturnEISRequest(pptReference)
    stubNrsRequest
  }

  private def setUpMocks() = {
    when(cacheRepository.get(any())).thenReturn(
      Future.successful(Option(UserAnswers("id").copy(data = ReturnTestHelper.returnsWithNoCreditDataJson)))
    )
    when(cacheRepository.clear(any[String]())).thenReturn(Future.successful(true))
  }

  private def stubObligationDesRequest(status: Int = Status.OK) = {
    implicit val odWrites: OWrites[ObligationDetail] = Json.writes[ObligationDetail]
    implicit val oWrites: OWrites[Obligation]        = Json.writes[Obligation]
    val writes: OWrites[ObligationDataResponse]      = Json.writes[ObligationDataResponse]
    wireMock.stubFor(
      get(obligationDesRequest)
        .willReturn(
          aResponse
            .withStatus(status)
            .withBody(Json.toJson(ObligationSpecHelper.createOneObligation(pptReference))(writes).toString())
        )
    )
  }

  private def stubReturnDisplayResponse(): Unit =
    wireMock.stubFor(
      get(DesUrl)
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withHeader(HeaderNames.AUTHORIZATION, "Gino")
            .withBody(displayApiResponse)
        )
    )

  private def stubReturnDisplayErrorResponse(): Unit =
    wireMock.stubFor(
      get(DesUrl)
        .willReturn(
          aResponse()
            .withStatus(Status.INTERNAL_SERVER_ERROR)
        )
    )

  private def stubGetBalanceEISRequest =
    wireMock.stubFor(
      get(urlPathEqualTo(balanceEISURL))
        .willReturn(ok().withBody(Json.toJson(ReturnTestHelper.createCreditBalanceDisplayResponse).toString()))
    )

  def displayApiResponse: String = """
    {
      "processingDate": "2022-07-03T09:30:47Z",
      "idDetails": {
        "pptReferenceNumber": "XMPPT0000000003",
        "submissionId": "123456789012"
      },
      "chargeDetails": {
        "periodKey": "22C2",
        "chargeReference": "XY007000075425",
        "periodFrom": "2022-04-01",
        "periodTo": "2022-06-30",
        "receiptDate": "2022-09-03T09:30:47Z",
        "returnType": "Amend"
      },
      "returnDetails": {
        "manufacturedWeight": 250,
        "importedWeight": 150,
        "totalNotLiable": 180,
        "humanMedicines": 50,
        "directExports": 60,
        "recycledPlastic": 70,
        "creditForPeriod": 12.13,
        "debitForPeriod": 0,
        "totalWeight": 220,
        "taxDue": 44
      }
    }
    """

}
