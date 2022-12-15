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

import org.mockito.Mockito.{never, reset, verify, when}
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.Status.{BAD_REQUEST, EXPECTATION_FAILED, NOT_FOUND, UNPROCESSABLE_ENTITY}
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import play.api.test.Helpers.{OK, SERVICE_UNAVAILABLE, await, contentAsJson, defaultAwaitTimeout, status}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.http.{HeaderCarrier, HttpException}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{FinancialDataResponse, ObligationDataResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns._
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.ReturnsController
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.FakeAuthenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.unit.MockConnectors
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.builders.ReturnsSubmissionResponseBuilder
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.calculations.Calculations
import uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation.NonRepudiationSubmissionAccepted
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.{AmendReturnValues, NewReturnValues, ReturnValues}
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.CreditsCalculationService.Credit
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService
import uk.gov.hmrc.plasticpackagingtaxreturns.services.{AvailableCreditService, CreditsCalculationService, FinancialDataService, PPTCalculationService, PPTFinancialsService}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.{LocalDateTime, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


//todo this spec is a MESS
class ReturnsControllerSpec
  extends AnyWordSpec with BeforeAndAfterEach with ScalaFutures
    with Matchers with AuthTestSupport with MockConnectors with ReturnsSubmissionResponseBuilder {

  private val periodKey = "21C4"
  private val userAnswersDataReturns: JsObject = Json.parse(
    s"""{
      |        "obligation" : {
      |            "periodKey" : "$periodKey"
      |        },
      |        "amendSelectedPeriodKey": "$periodKey",
      |        "manufacturedPlasticPackagingWeight" : 100,
      |        "importedPlasticPackagingWeight" : 0,
      |        "exportedPlasticPackagingWeight" : 0,
      |        "nonExportedHumanMedicinesPlasticPackagingWeight" : 10,
      |        "nonExportRecycledPlasticPackagingWeight" : 5
      |    }""".stripMargin).asInstanceOf[JsObject]

  private val invalidUserAnswersDataReturns: JsObject = Json.parse(
    s"""{
      |        "obligation" : {
      |            "periodKey" : "$periodKey"
      |        },
      |        "amendSelectedPeriodKey": "$periodKey",
      |        "manufacturedPlasticPackagingWeight" : 100,
      |        "exportedPlasticPackagingWeight" : 0
      |    }""".stripMargin).asInstanceOf[JsObject]

  private val invalidDeductionsAnswersDataReturns: JsObject = Json.parse(
    s"""{
      |        "obligation" : {
      |            "periodKey" : "$periodKey"
      |        },
      |        "amendSelectedPeriodKey": "$periodKey",
      |        "manufacturedPlasticPackagingWeight" : 10,
      |        "importedPlasticPackagingWeight" : 0,
      |        "exportedPlasticPackagingWeight" : 0,
      |        "nonExportedHumanMedicinesPlasticPackagingWeight" : 10,
      |        "nonExportRecycledPlasticPackagingWeight" : 5
      |    }""".stripMargin).asInstanceOf[JsObject]

  private val userAnswersDataAmends: JsObject = Json.parse(
    s"""{
      |        "obligation" : {
      |            "periodKey" : "$periodKey"
      |        },
      |        "amendSelectedPeriodKey": "$periodKey",
      |        "returnDisplayApi" : {
      |            "idDetails" : {
      |                "pptReferenceNumber" : "pptref",
      |                "submissionId" : "submission12"
      |            },
      |            "returnDetails" : {
      |                "manufacturedWeight" : 250,
      |                "importedWeight" : 0,
      |                "totalNotLiable" : 0,
      |                "humanMedicines" : 10,
      |                "directExports" : 0,
      |                "recycledPlastic" : 5,
      |                "creditForPeriod" : 12.13,
      |                "debitForPeriod" : 0,
      |                "totalWeight" : 220,
      |                "taxDue" : 44
      |            }
      |        },
      |        "amend": {
      |            "amendManufacturedPlasticPackaging" : 100,
      |            "amendImportedPlasticPackaging" : 0,
      |            "amendDirectExportPlasticPackaging" : 0,
      |            "amendHumanMedicinePlasticPackaging" : 10,
      |            "amendRecycledPlasticPackaging" : 5
      |        }
      |    }""".stripMargin).asInstanceOf[JsObject]

  private val userAnswersDataPartialAmends: JsObject = Json.parse(
    s"""{
      |        "obligation" : {
      |            "periodKey" : "$periodKey"
      |        },
      |        "amendSelectedPeriodKey": "$periodKey",
      |        "amend": {
      |            "amendManufacturedPlasticPackaging" : 100
      |        },
      |        "returnDisplayApi" : {
      |            "idDetails" : {
      |                "pptReferenceNumber" : "pptref",
      |                "submissionId" : "submission12"
      |            },
      |            "returnDetails" : {
      |                "manufacturedWeight" : 250,
      |                "importedWeight" : 0,
      |                "totalNotLiable" : 0,
      |                "humanMedicines" : 10,
      |                "directExports" : 0,
      |                "recycledPlastic" : 5,
      |                "creditForPeriod" : 12.13,
      |                "debitForPeriod" : 0,
      |                "totalWeight" : 220,
      |                "taxDue" : 44
      |            }
      |        }
      |    }""".stripMargin).asInstanceOf[JsObject]

  private val invalidUserAnswersDataAmends: JsObject = Json.parse(
    s"""{
      |        "obligation" : {
      |            "periodKey" : "$periodKey"
      |        },
      |        "amendSelectedPeriodKey": "$periodKey",
      |        "amend": {
      |            "amendManufacturedPlasticPackaging" : 100,
      |            "amendDirectExportPlasticPackaging" : 0
      |        }
      |    }""".stripMargin).asInstanceOf[JsObject]

  private val invalidDeductionsAnswersDataAmends: JsObject = Json.parse(
    s"""{
      |        "obligation" : {
      |            "periodKey" : "$periodKey"
      |        },
      |        "amendSelectedPeriodKey": "$periodKey",
      |        "amend": {
      |            "amendManufacturedPlasticPackaging" : 10,
      |            "amendImportedPlasticPackaging" : 0,
      |            "amendDirectExportPlasticPackaging" : 0,
      |            "amendHumanMedicinePlasticPackaging" : 10,
      |            "amendRecycledPlasticPackaging" : 5
      |        }
      |    }""".stripMargin).asInstanceOf[JsObject]

  private val userAnswersReturns: UserAnswers                  = UserAnswers("id").copy(data = userAnswersDataReturns)
  private val invalidUserAnswersReturns: UserAnswers           = UserAnswers("id").copy(data = invalidUserAnswersDataReturns)
  private val invalidDeductionsUserAnswersReturns: UserAnswers = UserAnswers("id").copy(data = invalidDeductionsAnswersDataReturns)
  private val userAnswersAmends: UserAnswers                   = UserAnswers("id").copy(data = userAnswersDataAmends)
  private val userAnswersPartialAmends: UserAnswers            = UserAnswers("id").copy(data = userAnswersDataPartialAmends)
  private val invalidUserAnswersAmends: UserAnswers            = UserAnswers("id").copy(data = invalidUserAnswersDataAmends)
  private val invalidDeductionsUserAnswersAmends: UserAnswers  = UserAnswers("id").copy(data = invalidDeductionsAnswersDataAmends)

  private val expectedNewReturnValues: ReturnValues = NewReturnValues(
    periodKey = periodKey,
    manufacturedPlasticWeight = 100,
    importedPlasticWeight = 0,
    exportedPlasticWeight = 0,
    humanMedicinesPlasticWeight = 10,
    recycledPlasticWeight = 5,
    convertedPackagingCredit = 0,
    availableCredit = 0
  )

  private val expectedAmendReturnValues: ReturnValues = AmendReturnValues(
    periodKey = periodKey,
    manufacturedPlasticWeight = 100,
    importedPlasticWeight = 0,
    exportedPlasticWeight = 0,
    humanMedicinesPlasticWeight = 10,
    recycledPlasticWeight = 5,
    submission = "submission12"
  )

  private val mockNonRepudiationService: NonRepudiationService = mock[NonRepudiationService]
  private val mockAppConfig: AppConfig = mock[AppConfig]
  private val nrSubmissionId: String = "nrSubmissionId"
  private val mockSessionRepository: SessionRepository = mock[SessionRepository]
  private val mockAuditConnector = mock[AuditConnector]
  private val mockPptCalculationService = mock[PPTCalculationService]
  private val mockFinancialDataService = mock[FinancialDataService]
  private val mockFinancialsService = mock[PPTFinancialsService]
  private val mockCreditCalcService = mock[CreditsCalculationService]
  private val mockAvailableCreditService = mock[AvailableCreditService]

  private val cc: ControllerComponents = Helpers.stubControllerComponents()
  val sut = new ReturnsController(
    new FakeAuthenticator(cc),
    mockSessionRepository,
    mockNonRepudiationService,
    cc,
    mockReturnsConnector,
    mockObligationDataConnector,
    mockAppConfig,
    mockAuditConnector,
    mockPptCalculationService,
    mockFinancialDataService,
    mockFinancialsService,
    mockCreditCalcService,
    mockAvailableCreditService
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAppConfig)
    reset(mockAuthConnector)
    reset(mockSessionRepository)
    reset(mockNonRepudiationService, mockObligationDataConnector)
  }

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

        val result: Future[Result] = sut.get(pptReference, periodKey).apply(FakeRequest())

        status(result) mustBe OK
        contentAsJson(result) mustBe toJson(returnDisplayResponse)
      }

      "return 404" in {
        withAuthorizedUser()
        val periodKey = "22CC"
        mockReturnDisplayConnectorFailure(404)

        val result: Future[Result] = sut.get(pptReference, periodKey).apply(FakeRequest())

        status(result) mustBe NOT_FOUND
      }
    }

    "propagate status code when failure occurs" in {
      withAuthorizedUser()
      setupMocks(userAnswersReturns)
      mockReturnsSubmissionConnectorFailure(BAD_REQUEST)

      val result: Future[Result] = sut.submit(pptReference).apply(FakeRequest())

      status(result) mustBe BAD_REQUEST
    }

    "returns" when {
      "propagate un-processable entity when cache incomplete" in {
        withAuthorizedUser()
        setupMocks(userAnswersReturns)
        when(mockSessionRepository.get(any)).thenReturn(Future.successful(Some(invalidUserAnswersReturns)))

        val result: Future[Result] = sut.submit(pptReference).apply(FakeRequest())

        status(result) mustBe UNPROCESSABLE_ENTITY
      }

      "propagate un-processable entity when invalid deductions" in {
        withAuthorizedUser()
        setupMocks(userAnswersReturns)
        when(mockPptCalculationService.calculate(any)).thenReturn(Calculations(1, 1, 1, 1, 0, isSubmittable = false))
        when(mockSessionRepository.get(any[String])).thenReturn(Future.successful(Some(invalidDeductionsUserAnswersReturns)))

        val result: Future[Result] = sut.submit(pptReference).apply(FakeRequest())

        status(result) mustBe UNPROCESSABLE_ENTITY
      }

      "submit a return via the returns connector" in {

        returnSubmittedAsExpected(pptReference, expectedNewReturnValues)

        withClue("delete a return after successful submission") {
          verify(mockSessionRepository).clearUserAnswers("7777777", FakeAuthenticator.cacheKey)
        }
      }

      "respond successfully when return submission is successful but the return delete fails" in {

        when(mockSessionRepository.clear(any[String])).thenReturn(Future.failed(new RuntimeException("BANG!")))

        returnSubmittedAsExpected(pptReference, expectedNewReturnValues)

      }

      "return expectation failed when obligation is not open" in {
        withAuthorizedUser()
        setupMocks(userAnswersReturns)
        when(mockObligationDataConnector.get(any, any, any, any, any)(any))
          .thenReturn(Future.successful(Right(ObligationDataResponse(Nil))))

        val result: Future[Result] = sut.submit(pptReference).apply(FakeRequest())

        status(result) mustBe EXPECTATION_FAILED
        verify(mockReturnsConnector, never()).submitReturn(any,any,any)(any)
        verify(mockSessionRepository).clearUserAnswers("7777777", FakeAuthenticator.cacheKey)
      }

    }

    "amends" when {
      "propagate un-processable entity when cache incomplete" in {
        withAuthorizedUser()
        setupMocks(userAnswersAmends)
        setUpFinancialApiMock(false)

        when(mockSessionRepository.get(any[String])).thenReturn(Future.successful(Some(invalidUserAnswersAmends)))

        val result: Future[Result] = sut.amend(pptReference).apply(FakeRequest())

        status(result) mustBe UNPROCESSABLE_ENTITY
      }

      "propagate un-processable entity when invalid deductions" in {
        withAuthorizedUser()
        setupMocks(userAnswersAmends)

        when(mockSessionRepository.get(any[String])).thenReturn(Future.successful(Some(invalidDeductionsUserAnswersAmends)))

        val result: Future[Result] = sut.amend(pptReference).apply(FakeRequest())

        status(result) mustBe UNPROCESSABLE_ENTITY
      }
    }

    "submit an amendment via the returns controller" in {

      setUpFinancialApiMock(false)
      amendSubmittedAsExpected(pptReference, expectedAmendReturnValues)

      withClue("delete a return after successful amend") {
        verify(mockSessionRepository).clearUserAnswers("7777777", FakeAuthenticator.cacheKey)
      }
    }

    "submit a partial amendment via the returns controller" in {

      amendSubmittedAsExpected(pptReference, expectedAmendReturnValues, userAnswersPartialAmends)
    }

    "respond successfully when return amend is successful but the return delete fails" in {

      when(mockSessionRepository.clear(any[String])).thenReturn(Future.failed(new RuntimeException("BANG!")))

      amendSubmittedAsExpected(pptReference, expectedAmendReturnValues)

    }

    "submit return handle NRS fail" in {
      withAuthorizedUser()
      setupMocks(userAnswersReturns)
      val nrsErrorMessage = "Something went wrong"

      when(mockNonRepudiationService.submitNonRepudiation(any, any, any, any, any)(any)).thenReturn(
        Future.failed(new HttpException(nrsErrorMessage, SERVICE_UNAVAILABLE))
      )

      val returnsSubmissionResponse: Return = aReturn()
      val returnsSubmissionResponseWithNrsFail: ReturnWithNrsFailureResponse = aReturnWithNrsFailure()
      mockReturnsSubmissionConnector(returnsSubmissionResponse)

      val result: Future[Result] = sut.submit(pptReference).apply(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe toJson(returnsSubmissionResponseWithNrsFail)

    }

    "amend return an error if Direct debit in progress" in {
      withAuthorizedUser()
      setupMocks(userAnswersAmends)
      setUpFinancialApiMock(true)

      val result: Future[Result] = sut.amend(pptReference).apply(FakeRequest())

      status(result) mustBe UNPROCESSABLE_ENTITY
      verify(mockReturnsConnector, never()).submitReturn(any,any,any)(any)

    }

    "amend throw an error if financial API error" in {
      withAuthorizedUser()
      setupMocks(userAnswersAmends)
      when(mockFinancialDataService.getFinancials(any,any)(any))
        .thenReturn(Future.successful(Left(500)))

      intercept[RuntimeException] {
        val result: Future[Result] = sut.amend(pptReference).apply(FakeRequest())
        status(result)
      }
      verify(mockReturnsConnector, never()).submitReturn(any,any,any)(any)
    }

    "amend submit return if Direct debit not in progress" in {
      withAuthorizedUser()
      setupMocks(userAnswersAmends)
      setUpFinancialApiMock(false)
      mockReturnsSubmissionConnector(aReturn())

      await(sut.amend(pptReference).apply(FakeRequest()))

      verify(mockReturnsConnector).submitReturn(any,any,any)(any)
    }
  }

  private def setUpFinancialApiMock(isDdInProgress: Boolean): Any = {
    when(mockFinancialDataService.getFinancials(any, any)(any))
      .thenReturn(
        Future.successful(
          Right(FinancialDataResponse(None, None, None, LocalDateTime.now(), Seq.empty))
        )
      )

    when(mockFinancialsService.lookUpForDdInProgress(eqTo(periodKey), any)).thenReturn(isDdInProgress)
  }

  private def amendSubmittedAsExpected(pptReference: String, returnValues: ReturnValues, userAnswers: UserAnswers = userAnswersAmends): Future[NonRepudiationSubmissionAccepted] =
    submissionSuccess("amend-return")(sut.amend, pptReference, returnValues, userAnswers)

  private def returnSubmittedAsExpected(pptReference: String, returnValues: ReturnValues): Future[NonRepudiationSubmissionAccepted] =
    submissionSuccess("submit-return")(sut.submit, pptReference, returnValues, userAnswersReturns)

  private def submissionSuccess(expectedEventName: String)
  (
    fun: String => Action[AnyContent],
    pptReference: String,
    returnValues: ReturnValues,
    userAnswers: UserAnswers): Future[NonRepudiationSubmissionAccepted] = {

    withAuthorizedUser()
    setupMocks(userAnswers)

    val calculations: Calculations = Calculations(taxDue = 17, chargeableTotal = 85, deductionsTotal = 15, packagingTotal = 100, totalRequestCreditInPounds = 0, isSubmittable = true)
    val eisRequest: ReturnsSubmissionRequest = ReturnsSubmissionRequest(returnValues, calculations)

    when(mockPptCalculationService.calculate(any)).thenReturn(calculations)

    val returnsSubmissionResponse: Return = aReturn()
    val returnsSubmissionResponseWithNrs: ReturnWithNrsSuccessResponse = aReturnWithNrs()

    mockReturnsSubmissionConnector(returnsSubmissionResponse)
    val submitReturnRequest = FakeRequest().withHeaders(newHeaders = ("foo", "bar"))
    val result: Future[Result] = fun(pptReference).apply(submitReturnRequest)

    status(result) mustBe OK
    contentAsJson(result) mustBe toJson(returnsSubmissionResponseWithNrs)

    val expectedHeaders = Map("Host" -> "localhost", "foo" -> "bar")
    val expectedNrsPayload = NrsReturnOrAmendSubmission(userAnswers.data, eisRequest)

    verify(mockNonRepudiationService).submitNonRepudiation(
      eqTo(expectedEventName), 
      eqTo(Json.toJson(expectedNrsPayload).toString),
      any[ZonedDateTime],
      eqTo(pptReference),
      eqTo(expectedHeaders)
    )(any[HeaderCarrier])
  }

  private def setupMocks(userAnswers: UserAnswers) = {
    mockGetObligationDataPeriodKey(pptReference, periodKey)
    when(mockCreditCalcService.totalRequestedCredit(any)).thenReturn(Credit(0L, BigDecimal(0)))
    when(mockAvailableCreditService.getBalance(any)(any)).thenReturn(Future.successful(BigDecimal(10)))
    when(mockAppConfig.taxRateFrom1stApril2022).thenReturn(BigDecimal(0.20))
    when(mockSessionRepository.clear(any[String])).thenReturn(Future.successful(true))
    when(mockSessionRepository.get(any[String])).thenReturn(Future.successful(Some(userAnswers)))
    when(mockNonRepudiationService.submitNonRepudiation(any, any, any, any, any)(any)).thenReturn(
      Future.successful(NonRepudiationSubmissionAccepted(nrSubmissionId))
    )
    when(mockPptCalculationService.calculate(any)).thenReturn(Calculations(1, 1, 1, 1, 0, isSubmittable = true))
  }

}
