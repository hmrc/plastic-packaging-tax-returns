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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.controllers

import com.codahale.metrics.SharedMetricRegistries
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status.{BAD_REQUEST, NOT_FOUND, UNPROCESSABLE_ENTITY}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.libs.json.Json.toJson
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, SERVICE_UNAVAILABLE, contentAsJson, defaultAwaitTimeout, route, status, writeableOf_AnyContentAsEmpty}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, HttpException}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ReturnsConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns._
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.unit.MockConnectors
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.builders.ReturnsSubmissionResponseBuilder
import uk.gov.hmrc.plasticpackagingtaxreturns.models.ReturnType.ReturnType
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.calculations.{Calculations, ReturnValues}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation.NonRepudiationSubmissionAccepted
import uk.gov.hmrc.plasticpackagingtaxreturns.models.ReturnType
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService

import java.time.ZonedDateTime
import scala.concurrent.Future

class ReturnsControllerSpec
  extends AnyWordSpec with GuiceOneAppPerSuite with BeforeAndAfterEach with ScalaFutures
    with Matchers with AuthTestSupport with MockConnectors with ReturnsSubmissionResponseBuilder {

  SharedMetricRegistries.clear()

  private val userAnswersData: JsObject = Json.parse(
    """{
      |        "obligation" : {
      |            "periodKey" : "21C4"
      |        },
      |        "manufacturedPlasticPackagingWeight" : 100,
      |        "importedPlasticPackagingWeight" : 0,
      |        "exportedPlasticPackagingWeight" : 0,
      |        "nonExportedHumanMedicinesPlasticPackagingWeight" : 10,
      |        "nonExportRecycledPlasticPackagingWeight" : 5
      |    }""".stripMargin).asInstanceOf[JsObject]

  private val invalidUserAnswersData: JsObject = Json.parse(
    """{
      |        "obligation" : {
      |            "periodKey" : "21C4"
      |        },
      |        "manufacturedPlasticPackagingWeight" : 100,
      |        "importedPlasticPackagingWeight" : 0,
      |        "exportedPlasticPackagingWeight" : 0,
      |        "nonExportedHumanMedicinesPlasticPackagingWeight" : 10
      |    }""".stripMargin).asInstanceOf[JsObject]

  private val invalidDeductionsAnswersData: JsObject = Json.parse(
    """{
      |        "obligation" : {
      |            "periodKey" : "21C4"
      |        },
      |        "manufacturedPlasticPackagingWeight" : 10,
      |        "importedPlasticPackagingWeight" : 0,
      |        "exportedPlasticPackagingWeight" : 0,
      |        "nonExportedHumanMedicinesPlasticPackagingWeight" : 10,
      |        "nonExportRecycledPlasticPackagingWeight" : 5
      |    }""".stripMargin).asInstanceOf[JsObject]

  private val userAnswers: UserAnswers                  = UserAnswers("id").copy(data = userAnswersData)
  private val invalidUserAnswers: UserAnswers           = UserAnswers("id").copy(data = invalidUserAnswersData)
  private val invalidDeductionsUserAnswers: UserAnswers = UserAnswers("id").copy(data = invalidDeductionsAnswersData)

  private val expectedReturnValues: ReturnValues = ReturnValues(
    periodKey = "21C4",
    manufacturedPlasticWeight = 100,
    importedPlasticWeight = 0,
    exportedPlasticWeight = 0,
    humanMedicinesPlasticWeight = 10,
    recycledPlasticWeight = 5,
    convertedPackagingCredit = 0
  )

  private val mockNonRepudiationService: NonRepudiationService = mock[NonRepudiationService]
  private val mockAppConfig: AppConfig                         = mock[AppConfig]
  private val nrSubmissionId: String                           = "nrSubmissionId"
  private val mockSessionRepository: SessionRepository         = mock[SessionRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAppConfig)
    reset(mockAuthConnector)
    reset(mockSessionRepository)
    reset(mockNonRepudiationService)
  }

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(
      bind[AuthConnector].to(mockAuthConnector),
      bind[ReturnsConnector].to(mockReturnsConnector),
      bind[SessionRepository].to(mockSessionRepository),
      bind[NonRepudiationService].to(mockNonRepudiationService),
      bind[AppConfig].to(mockAppConfig)
    )
    .build()

  "Returns submission controller" should {

    "get return to display" should {

      "return OK response" in {

        withAuthorizedUser()
        val periodKey = "22CC"
        val returnDisplayResponse = aReturn(
          withReturnDetails(returnDetails =
            Some(
              EisReturnDetails(
                manufacturedWeight = BigDecimal(256.12),
                importedWeight = BigDecimal(352.15),
                totalNotLiable = BigDecimal(546.42),
                humanMedicines = BigDecimal(1234.15),
                directExports = BigDecimal(12121.16),
                recycledPlastic = BigDecimal(4345.72),
                creditForPeriod =
                  BigDecimal(1560000.12),
                totalWeight = BigDecimal(16466.88),
                taxDue = BigDecimal(4600)
              )
            )
          )
        )

        mockReturnDisplayConnector(Json.toJson(returnDisplayResponse))

        val submitReturnRequest = FakeRequest("GET", s"/returns-submission/$pptReference/$periodKey")

        val result: Future[Result] = route(app, submitReturnRequest).get

        status(result) mustBe OK
        contentAsJson(result) mustBe toJson(returnDisplayResponse)
      }

      "return 404" in {
        withAuthorizedUser()
        val periodKey = "22CC"
        mockReturnDisplayConnectorFailure(404)

        val submitReturnRequest = FakeRequest("GET", s"/returns-submission/$pptReference/$periodKey")

        val result: Future[Result] = route(app, submitReturnRequest).get

        status(result) mustBe NOT_FOUND
      }
    }

    "propagate status code when failure occurs" in {
      withAuthorizedUser()
      setupMocks
      mockReturnsSubmissionConnectorFailure(BAD_REQUEST)

      val submitReturnRequest = FakeRequest("GET", s"/returns-submission/$pptReference")

      val result: Future[Result] = route(app, submitReturnRequest).get

      status(result) mustBe BAD_REQUEST
    }

    "propagate un-processable entity when cache incomplete" in {
      withAuthorizedUser()
      setupMocks

      when(mockSessionRepository.get(any[String])).thenReturn(Future.successful(Some(invalidUserAnswers)))

      val submitReturnRequest = FakeRequest("GET", s"/returns-submission/$pptReference")

      val result: Future[Result] = route(app, submitReturnRequest).get

      status(result) mustBe UNPROCESSABLE_ENTITY
    }

    "propagate un-processable entity when invalid deductions" in {
      withAuthorizedUser()
      setupMocks

      when(mockSessionRepository.get(any[String])).thenReturn(Future.successful(Some(invalidDeductionsUserAnswers)))

      val submitReturnRequest = FakeRequest("GET", s"/returns-submission/$pptReference")

      val result: Future[Result] = route(app, submitReturnRequest).get

      status(result) mustBe UNPROCESSABLE_ENTITY
    }

    "submit a return via the returns connector" in {
      returnSubmittedAsExpected(pptReference, expectedReturnValues)
    }

    "submit an amendment via the returns controller" in {
      amendSubmittedAsExpected(pptReference, expectedReturnValues)
    }

    "delete a return after successful submission" in {

      returnSubmittedAsExpected(pptReference, expectedReturnValues)

      verify(mockSessionRepository).clear("Int-ba17b467-90f3-42b6-9570-73be7b78eb2b-7777777")
    }

    "delete a return after successful amend" in {
      amendSubmittedAsExpected(pptReference, expectedReturnValues)

      verify(mockSessionRepository).clear("Int-ba17b467-90f3-42b6-9570-73be7b78eb2b-7777777")
    }

    "respond successfully when return submission is successful but the return delete fails" in {
      when(mockSessionRepository.clear(any[String]())).thenReturn(Future.failed(new RuntimeException("BANG!")))

      returnSubmittedAsExpected(pptReference, expectedReturnValues)
    }

    "respond successfully when return amend is successful but the return delete fails" in {
      when(mockSessionRepository.clear(any[String]())).thenReturn(Future.failed(new RuntimeException("BANG!")))

      amendSubmittedAsExpected(pptReference, expectedReturnValues)
    }

    "handle NRS fail" in {
      withAuthorizedUser()
      setupMocks

      val nrsErrorMessage = "Something went wrong"

      when(mockNonRepudiationService.submitNonRepudiation(any(), any(), any(), any())(any())).thenReturn(
        Future.failed(new HttpException(nrsErrorMessage, SERVICE_UNAVAILABLE))
      )

      val returnsSubmissionResponse: Return = aReturn()
      val returnsSubmissionResponseWithNrsFail: ReturnWithNrsFailureResponse = aReturnWithNrsFailure()

      mockReturnsSubmissionConnector(returnsSubmissionResponse)

      val submitReturnRequest = FakeRequest("GET", s"/returns-submission/$pptReference").withHeaders(newHeaders = ("foo", "bar"))

      val result: Future[Result] = route(app, submitReturnRequest).get

      status(result) mustBe OK
      contentAsJson(result) mustBe toJson(returnsSubmissionResponseWithNrsFail)
    }

  }

  private def amendSubmittedAsExpected(pptReference: String, returnValues: ReturnValues): Future[NonRepudiationSubmissionAccepted] =
    submissionSuccess("GET", s"/returns-amend/$pptReference/submission12", pptReference, returnValues, ReturnType.AMEND, Some("submission12"))

  private def returnSubmittedAsExpected(pptReference: String, returnValues: ReturnValues): Future[NonRepudiationSubmissionAccepted] =
    submissionSuccess("GET", s"/returns-submission/$pptReference", pptReference, returnValues, ReturnType.NEW)

  private def submissionSuccess(action: String, url: String, pptReference: String, returnValues: ReturnValues,
                                returnType: ReturnType, submissionId: Option[String] = None): Future[NonRepudiationSubmissionAccepted] = {
    withAuthorizedUser()
    setupMocks

    val calculations: Calculations = Calculations(taxDue = 17, chargeableTotal = 85, deductionsTotal = 15, packagingTotal = 100, isSubmittable = true)
    val eisRequest: ReturnsSubmissionRequest = ReturnsSubmissionRequest(returnValues, calculations, submissionId, returnType)

    val returnsSubmissionResponse: Return = aReturn()
    val returnsSubmissionResponseWithNrs: ReturnWithNrsSuccessResponse = aReturnWithNrs()

    mockReturnsSubmissionConnector(returnsSubmissionResponse)

    val submitReturnRequest = FakeRequest(action, url).withHeaders(newHeaders = ("foo", "bar"))
    val result: Future[Result] = route(app, submitReturnRequest).get

    status(result) mustBe OK
    contentAsJson(result) mustBe toJson(returnsSubmissionResponseWithNrs)

    val expectedHeaders = Map("Host" -> "localhost", "foo" -> "bar", "Content-Length" -> "0")
    val expectedNrsPayload = NrsReturnOrAmendSubmission(userAnswers.data, eisRequest)

    verify(mockNonRepudiationService).submitNonRepudiation(
      ArgumentMatchers.eq(Json.toJson(expectedNrsPayload).toString),
      any[ZonedDateTime],
      ArgumentMatchers.eq(pptReference),
      ArgumentMatchers.eq(expectedHeaders)
    )(any[HeaderCarrier])
  }

  private def setupMocks = {
    when(mockAppConfig.taxRatePoundsPerKg).thenReturn(BigDecimal(0.20))
    when(mockSessionRepository.clear(any[String]())).thenReturn(Future.successful(true))
    when(mockSessionRepository.get(any[String])).thenReturn(Future.successful(Some(userAnswers)))
    when(mockNonRepudiationService.submitNonRepudiation(any(), any(), any(), any())(any())).thenReturn(
      Future.successful(NonRepudiationSubmissionAccepted(nrSubmissionId))
    )
  }

}
