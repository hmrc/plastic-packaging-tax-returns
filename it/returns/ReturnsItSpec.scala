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

package returns

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK, UNAUTHORIZED}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json, OWrites}
import play.api.libs.ws.WSClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.{ObligationSpecHelper, ReturnWireMockServerSpec}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise._
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.exportcreditbalance.ExportCreditBalanceDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.NrsTestData
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.amends.{IdDetails, ReturnDisplayApi, ReturnDisplayDetails}
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class ReturnsItSpec extends PlaySpec
  with GuiceOneServerPerSuite
  with ReturnWireMockServerSpec
  with AuthTestSupport
  with NrsTestData
  with BeforeAndAfterEach {
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  val httpClient: DefaultHttpClient          = app.injector.instanceOf[DefaultHttpClient]
  lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]
  private val periodKey = "22C2"
  private val DesUrl = s"/plastic-packaging-tax/returns/PPT/$pptReference/$periodKey"
  private val validGetReturnDisplayUrl = s"http://localhost:$port/returns-submission/$pptReference/$periodKey"
  private val submitReturnUrl = s"http://localhost:$port/returns-submission/$pptReference"
  private val obligationDesRequest = s"/enterprise/obligation-data/zppt/$pptReference/PPT?status=O"
  private val balanceEISURL = s"/plastic-packaging-tax/export-credits/PPT/$pptReference"
  private lazy val cacheRepository = mock[SessionRepository]


  private val userAnswersDataReturns: JsObject = Json.parse(
    s"""{
       |        "obligation" : {
       |            "periodKey" : "$periodKey",
       |            "fromDate" : "${LocalDate.now.minusMonths(1)}", 
       |            "toDate" : "${LocalDate.now}"
       |        },
       |        "amendSelectedPeriodKey": "$periodKey",
       |        "manufacturedPlasticPackagingWeight" : 100,
       |        "importedPlasticPackagingWeight" : 0,
       |        "exportedPlasticPackagingWeight" : 0,
       |        "nonExportedHumanMedicinesPlasticPackagingWeight" : 10,
       |        "nonExportRecycledPlasticPackagingWeight" : 5
       |    }""".stripMargin).asInstanceOf[JsObject]

  override lazy val app: Application = {
    server.start()
    SharedMetricRegistries.clear()
    GuiceApplicationBuilder()
      .configure(server.overrideConfig)
      .overrides(
        bind[AuthConnector].to(mockAuthConnector),
        bind[SessionRepository].to(cacheRepository)
      )
      .build()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(
      mockAuthConnector,
      cacheRepository
    )
  }

  "return 200 when getting return details" in {
    withAuthorizedUser()
    stubReturnDisplayResponse

    val response = await(wsClient.url(validGetReturnDisplayUrl).get())

    response.status mustBe OK
  }

  "return display details" in {
    withAuthorizedUser()
    stubReturnDisplayResponse

    val response = await(wsClient.url(validGetReturnDisplayUrl).get())

    response.json mustBe Json.toJson(createDisplayApiResponse)
  }

  "return an error if DES API fails when getting return" in {
    withAuthorizedUser()
    stubReturnDisplayErrorResponse

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
    setUpStub
    setUpMocks

    val response = await(wsClient.url(submitReturnUrl).withHttpHeaders("Authorization" -> "TOKEN").post(pptReference))

    response.status mustBe OK
  }

  "success return submit with nrs success" in {
    withAuthorizedUser()
    mockAuthorization(NonRepudiationService.nonRepudiationIdentityRetrievals, testAuthRetrievals)
    setUpStub
    setUpMocks

    val response = await(wsClient.url(submitReturnUrl).withHttpHeaders("Authorization" -> "TOKEN").post(pptReference))

    response.status mustBe OK
    response.json mustBe Json.toJson(aReturnWithNrs())
  }

  "success return submit with nrs failure" in {
    withAuthorizedUser()
    mockAuthorization(NonRepudiationService.nonRepudiationIdentityRetrievals, testAuthRetrievals)
    setUpStub
    setUpMocks
    stubNrsFailingRequest

    val response = await(wsClient.url(submitReturnUrl).withHttpHeaders("Authorization" -> "TOKEN").post(pptReference))

    response.status mustBe OK
    response.json mustBe Json.toJson(aReturnWithNrsFailure().copy(nrsFailureReason = "exception"))
  }

  "return an error when submitting return" in {
    withAuthorizedUser()
    mockAuthorization(NonRepudiationService.nonRepudiationIdentityRetrievals, testAuthRetrievals)
    stubObligationDesRequest(INTERNAL_SERVER_ERROR)
    when(cacheRepository.get(any())).thenReturn(Future.successful(Option(UserAnswers("id").copy(data = userAnswersDataReturns))))

    val response = await(wsClient.url(submitReturnUrl).withHttpHeaders("Authorization" -> "TOKEN").post(pptReference))

    response.status mustBe INTERNAL_SERVER_ERROR
  }

  "return unauthorised when submitting return" in {
    withUnauthorizedUser(new RuntimeException)

    val response = await(wsClient.url(submitReturnUrl).withHttpHeaders("Authorization" -> "TOKEN").post(pptReference))

    response.status mustBe UNAUTHORIZED
  }

  private def setUpStub = {
    stubObligationDesRequest()
    stubGetBalanceEISRequest
    stubSubmitReturnEISRequest(pptReference)
    stubNrsRequest
  }

  private def setUpMocks = {
    when(cacheRepository.get(any())).thenReturn(Future.successful(Option(UserAnswers("id").copy(data = userAnswersDataReturns))))
    when(cacheRepository.clear(any[String]())).thenReturn(Future.successful(true))
  }

  private def stubObligationDesRequest(status: Int = Status.OK) = {
    implicit val odWrites: OWrites[ObligationDetail] = Json.writes[ObligationDetail]
    implicit val oWrites: OWrites[Obligation]        = Json.writes[Obligation]
    val writes: OWrites[ObligationDataResponse]      = Json.writes[ObligationDataResponse]
    server.stubFor(get(obligationDesRequest)
      .willReturn(aResponse
        .withStatus(status)
        .withBody(Json.toJson(
          ObligationSpecHelper.createOneObligation(pptReference)
        )(writes).toString()))
    )
  }

  private def stubReturnDisplayResponse: Unit = {
    server.stubFor(
      get(DesUrl)
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(Json.toJson(createDisplayApiResponse).toString())
        )
    )
  }

  private def stubReturnDisplayErrorResponse: Unit = {
    server.stubFor(
      get(DesUrl)
        .willReturn(
          aResponse()
            .withStatus(Status.INTERNAL_SERVER_ERROR)
        )
    )
  }

  private def stubGetBalanceEISRequest = {
    server.stubFor(get(balanceEISURL)
      .willReturn(
        ok().withBody(Json.toJson(createCreditBalanceDisplayResponse).toString())
      )
    )
  }

  private def createCreditBalanceDisplayResponse = {
    ExportCreditBalanceDisplayResponse(
      processingDate = "2021-11-17T09:32:50.345Z",
      totalPPTCharges = BigDecimal(1000),
      totalExportCreditClaimed = BigDecimal(100),
      totalExportCreditAvailable = BigDecimal(200)
    )
  }

  private def createDisplayApiResponse: ReturnDisplayApi = {
    ReturnDisplayApi(
      ReturnDisplayDetails(
        manufacturedWeight = 250L,
        importedWeight = 150L,
        totalNotLiable = 180L,
        humanMedicines = 50L,
        directExports = 60L,
        recycledPlastic = 70L,
        creditForPeriod = BigDecimal(12.13),
        debitForPeriod = BigDecimal(0),
        totalWeight = 220L,
        taxDue = BigDecimal(44)
      ),
      idDetails = IdDetails(pptReferenceNumber = pptReference, submissionId = "123456789012")
    )
  }
}
