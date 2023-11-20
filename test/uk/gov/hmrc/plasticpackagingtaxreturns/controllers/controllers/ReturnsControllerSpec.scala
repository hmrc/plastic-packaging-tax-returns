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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.controllers

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.Mockito.{never, verify, when}
import org.mockito.MockitoSugar.reset
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Logger
import play.api.http.Status.{BAD_REQUEST, EXPECTATION_FAILED, NOT_FOUND, UNPROCESSABLE_ENTITY}
import play.api.libs.json.Json.toJson
import play.api.libs.json._
import play.api.mvc.{ControllerComponents, Result}
import play.api.test.Helpers.{OK, SERVICE_UNAVAILABLE, await, contentAsJson, defaultAwaitTimeout, status}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.http.{HeaderCarrier, HttpException}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{FinancialDataResponse, ObligationDataResponse, ObligationStatus}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns._
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.ReturnsController
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.ReturnsController.ReturnWithTaxRate
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.FakeAuthenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.unit.MockConnectors
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.builders.ReturnsSubmissionResponseBuilder
import uk.gov.hmrc.plasticpackagingtaxreturns.models.{CreditCalculation, ReturnType, TaxablePlastic, UserAnswers}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.calculations.Calculations
import uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation.NonRepudiationSubmissionAccepted
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.{AmendReturnValues, NewReturnValues, ReturnValues}
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService.NotableEvent
import uk.gov.hmrc.plasticpackagingtaxreturns.services.{AvailableCreditService, CreditsCalculationService, FinancialDataService, PPTCalculationService, PPTFinancialsService}
import uk.gov.hmrc.plasticpackagingtaxreturns.support.{AmendTestHelper, ReturnTestHelper}
import uk.gov.hmrc.plasticpackagingtaxreturns.util.{EdgeOfSystem, TaxRateTable}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.{LocalDate, LocalDateTime, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ReturnsControllerSpec
    extends AnyWordSpec with BeforeAndAfterEach with ScalaFutures with Matchers with AuthTestSupport with MockConnectors
    with ReturnsSubmissionResponseBuilder {

  private val userAnswersReturns: UserAnswers        = UserAnswers("id").copy(data = ReturnTestHelper.returnWithCreditsDataJson)
  private val invalidUserAnswersReturns: UserAnswers = UserAnswers("id").copy(data = ReturnTestHelper.invalidReturnsDataJson)
  private val userAnswersAmends: UserAnswers         = UserAnswers("id").copy(data = AmendTestHelper.userAnswersDataAmends)
  private val userAnswersPartialAmends: UserAnswers  = UserAnswers("id").copy(data = AmendTestHelper.userAnswersDataWithoutAmends)
  private val invalidUserAnswersAmends: UserAnswers  = UserAnswers("id").copy(data = AmendTestHelper.userAnswersDataWithInvalidAmends)
  private val calculations: Calculations = Calculations(1, 1, 1, 1,true,0.123)

  private val expectedNewReturnValues: ReturnValues = NewReturnValues(
    periodKey = "21C4",
    periodEndDate = LocalDate.now,
    manufacturedPlasticWeight = 100,
    importedPlasticWeight = 1,
    exportedPlasticWeight = 200,
    exportedByAnotherBusinessPlasticWeight = 100,
    humanMedicinesPlasticWeight = 10,
    recycledPlasticWeight = 5,
    convertedPackagingCredit = 0,
    availableCredit = 0
  )

  private val expectedAmendReturnValues: ReturnValues = AmendReturnValues(
    periodKey = "21C4",
    periodEndDate = LocalDate.now,
    manufacturedPlasticWeight = 100,
    importedPlasticWeight = 1,
    exportedPlasticWeight = 2,
    exportedByAnotherBusinessPlasticWeight = 5,
    humanMedicinesPlasticWeight = 3,
    recycledPlasticWeight = 5,
    submission = "submission12"
  )

  private val expectedAmendReturnValuesWithNoAmend: ReturnValues = AmendReturnValues(
    periodKey = "21C4",
    periodEndDate = LocalDate.now,
    manufacturedPlasticWeight = 255,
    importedPlasticWeight = 0,
    exportedPlasticWeight = 6,
    exportedByAnotherBusinessPlasticWeight = 0L,
    humanMedicinesPlasticWeight = 10,
    recycledPlasticWeight = 5,
    submission = "submission12"
  )

  private val mockNonRepudiationService: NonRepudiationService = mock[NonRepudiationService]
  private val mockAppConfig: AppConfig                         = mock[AppConfig]
  private val nrSubmissionId: String                           = "nrSubmissionId"
  private val mockSessionRepository: SessionRepository         = mock[SessionRepository]
  private val mockAuditConnector                               = mock[AuditConnector]
  private val mockPptCalculationService                        = mock[PPTCalculationService]
  private val mockFinancialDataService                         = mock[FinancialDataService]
  private val mockFinancialsService                            = mock[PPTFinancialsService]
  private val mockCreditsCalculationService                    = mock[CreditsCalculationService]
  private val mockAvailableCreditService                       = mock[AvailableCreditService]
  private val mockTaxRateTable                                 = mock[TaxRateTable]
  private val mockEdgeOfSystem                                 = mock[EdgeOfSystem]

  private val cc: ControllerComponents = Helpers.stubControllerComponents()

  private val sut = new ReturnsController(
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
    mockCreditsCalculationService,
    mockAvailableCreditService,
    mockTaxRateTable,
    mockEdgeOfSystem
  ) {
    protected override val logger: Logger = mockLogger
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(
      mockSessionRepository,
      mockNonRepudiationService,
      mockReturnsConnector,
      mockObligationDataConnector,
      mockAppConfig,
      mockAuditConnector,
      mockPptCalculationService,
      mockFinancialDataService,
      mockFinancialsService,
      mockCreditsCalculationService,
      mockAvailableCreditService,
      mockTaxRateTable)
  }

  "get" should {

    "return OK response" in {
      withAuthorizedUser()
      val periodKey = "22CC"
      val returnDisplayResponse = JsObject(Seq("chargeDetails" -> JsObject(Seq("periodTo" ->JsString(LocalDate.of(2020, 5, 14).toString)))))

      mockReturnDisplayConnector(returnDisplayResponse)
      when(mockTaxRateTable.lookupRateFor(any)).thenReturn(0.133)

      val result: Future[Result] = sut.get(pptReference, periodKey).apply(FakeRequest())
      val returnWithTaxRate = ReturnWithTaxRate(returnDisplayResponse, 0.133)
      status(result) mustBe OK
      contentAsJson(result) mustBe toJson(returnWithTaxRate)
      verify(mockTaxRateTable).lookupRateFor(LocalDate.of(2020, 5, 14))
      verify(mockReturnsConnector).get(eqTo(pptReference), eqTo(periodKey), any)(any)
    }

    "return 404" in {
      withAuthorizedUser()
      val periodKey = "22CC"
      mockReturnDisplayConnectorFailure(404)

      val result: Future[Result] = sut.get(pptReference, periodKey).apply(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "throw exception" when {
      "periodTo is not in the returnDisplayResponse" in {
        withAuthorizedUser()
        val returnDisplayResponse = JsObject(Seq("chargeDetails" -> JsObject(Seq.empty)))

        mockReturnDisplayConnector(returnDisplayResponse)

        val result: Future[Result] = sut.get(pptReference, "22CC").apply(FakeRequest())
        intercept[NoSuchElementException](await(result))
      }

      "periodTo is not a local date" in {
        withAuthorizedUser()
        val returnDisplayResponse = JsObject(Seq("chargeDetails" -> JsObject(Seq("periodTo" ->JsString("marcy")))))

        mockReturnDisplayConnector(returnDisplayResponse)

        val result: Future[Result] = sut.get(pptReference, "22CC").apply(FakeRequest())
        intercept[JsResultException](await(result))
      }
    }
  }


  "submit" should {
    "propagate status code when failure occurs" in {
      withAuthorizedUser()
      setupMocksForSubmit(userAnswersReturns)
      mockReturnsSubmissionConnectorFailure(BAD_REQUEST)

      val result: Result = await {
        sut.submit(pptReference).apply(FakeRequest())
      }

      verify(mockObligationDataConnector).get(eqTo(pptReference), eqTo("some-internal-ID"), eqTo(None), eqTo(None), 
        eqTo(Some(ObligationStatus.OPEN)))(any)

      result.header.status mustBe BAD_REQUEST
    }

    "propagate un-processable entity when cache incomplete" in {
      withAuthorizedUser()
      setupMocksForSubmit(userAnswersReturns)
      when(mockSessionRepository.get(any)).thenReturn(Future.successful(Some(invalidUserAnswersReturns)))

      val result: Future[Result] = sut.submit(pptReference).apply(FakeRequest())

      status(result) mustBe UNPROCESSABLE_ENTITY
    }

    "submit a return via the returns connector" in {
      withAuthorizedUser()
      setupMocksForSubmit(userAnswersReturns)
      when(mockSessionRepository.clear(any[String])).thenReturn(Future.failed(new RuntimeException("BANG!")))
      mockReturnsSubmissionConnector(aReturn())

      val result: Future[Result] = sut.submit(pptReference).apply(
        FakeRequest().withHeaders(newHeaders = ("foo", "bar"))
      )

      status(result) mustBe OK
      contentAsJson(result) mustBe toJson(aReturnWithNrs())
      verifySubmitNonRepudiation(createNrsPayload(expectedNewReturnValues, userAnswersReturns))

      withClue("delete a return after successful submission") {
        verify(mockSessionRepository).clearUserAnswers("7777777", FakeAuthenticator.cacheKey)
      }
    }

    "respond successfully when return submission is successful but the return delete fails" in {

      withAuthorizedUser()
      setupMocksForSubmit(userAnswersReturns)
      when(mockSessionRepository.clear(any[String])).thenReturn(Future.failed(new RuntimeException("BANG!")))
      mockReturnsSubmissionConnector(aReturn())

      val result: Future[Result] = sut.submit(pptReference).apply(
        FakeRequest().withHeaders(newHeaders = ("foo", "bar"))
      )

      status(result) mustBe OK
      contentAsJson(result) mustBe toJson(aReturnWithNrs())
      verifySubmitNonRepudiation(createNrsPayload(expectedNewReturnValues, userAnswersReturns))
    }

    "return expectation failed when obligation is not open" in {
      withAuthorizedUser()
      setupMocksForSubmit(userAnswersReturns)
      when(mockObligationDataConnector.get(any, any, any, any, any)(any))
        .thenReturn(Future.successful(Right(ObligationDataResponse(Nil))))

      val result: Future[Result] = sut.submit(pptReference).apply(FakeRequest())

      status(result) mustBe EXPECTATION_FAILED
      verify(mockReturnsConnector, never()).submitReturn(any, any, any)(any)
      verify(mockSessionRepository).clearUserAnswers("7777777", FakeAuthenticator.cacheKey)
    }

    "submit return handle NRS fail" in {
      withAuthorizedUser()
      setupMocksForSubmit(userAnswersReturns)
      when(mockNonRepudiationService.submitNonRepudiation(any, any, any, any, any)(any)).thenReturn(
        Future.failed(new HttpException("Something went wrong", SERVICE_UNAVAILABLE))
      )
      mockReturnsSubmissionConnector(aReturn())

      val result: Future[Result] = sut.submit(pptReference).apply(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe toJson(aReturnWithNrsFailure())
    }

    "submit a return with a total of exported plastic" in {
      withAuthorizedUser()
      setupMocksForSubmit(userAnswersReturns)
      when(mockSessionRepository.clear(any[String])).thenReturn(Future.failed(new RuntimeException("BANG!")))
      mockReturnsSubmissionConnector(aReturn())

      val result: Future[Result] = sut.submit(pptReference).apply(
        FakeRequest().withHeaders(newHeaders = ("foo", "bar"))
      )

      status(result) mustBe OK
      verify(mockReturnsConnector).submitReturn(
        ArgumentMatchers.eq(pptReference),
        ArgumentMatchers.eq(expectedSubmissionRequestForReturns),
        any)(any)
    }
  }

  "amend" should {
    "propagate un-processable entity when cache incomplete" in {
      withAuthorizedUser()
      setupMocksForAmend(userAnswersAmends)

      when(mockSessionRepository.get(any[String])).thenReturn(Future.successful(Some(invalidUserAnswersAmends)))

      val result: Future[Result] = sut.amend(pptReference).apply(FakeRequest())

      status(result) mustBe UNPROCESSABLE_ENTITY
    }

    "submit an amendment via the returns controller" in {
      withAuthorizedUser()
      setupMocksForAmend(userAnswersAmends)
      setUpFinancialApiMock(false)
      when(mockSessionRepository.clear(any[String])).thenReturn(Future.failed(new RuntimeException("BANG!")))
      mockReturnsSubmissionConnector(aReturn())

      val result: Future[Result] = sut.amend(pptReference).apply(
        FakeRequest().withHeaders(newHeaders = ("foo", "bar"))
      )

      status(result) mustBe OK
      contentAsJson(result) mustBe toJson(aReturnWithNrs())
      verify(mockReturnsConnector).submitReturn(ArgumentMatchers.eq(pptReference), ArgumentMatchers.eq(expectedSubmissionRequestForAmend), any)(any)
      verifySubmitNonRepudiation(createNrsPayload(expectedAmendReturnValues, userAnswersAmends))

      withClue("delete a return after successful amend") {
        verify(mockSessionRepository).clearUserAnswers("7777777", FakeAuthenticator.cacheKey)
      }
    }

    "submit a partial amendment via the returns controller" in {
      withAuthorizedUser()
      setupMocksForAmend(userAnswersPartialAmends)
      when(mockSessionRepository.clear(any[String])).thenReturn(Future.failed(new RuntimeException("BANG!")))
      mockReturnsSubmissionConnector(aReturn())

      val result: Future[Result] = sut.amend(pptReference).apply(
        FakeRequest().withHeaders(newHeaders = ("foo", "bar"))
      )

      status(result) mustBe OK
      contentAsJson(result) mustBe toJson(aReturnWithNrs())
      verifySubmitNonRepudiation(createNrsPayload(expectedAmendReturnValuesWithNoAmend, userAnswersPartialAmends))
    }

    "respond successfully when return amend is successful but the return delete fails" in {
      withAuthorizedUser()
      when(mockSessionRepository.clear(any[String])).thenReturn(Future.failed(new RuntimeException("BANG!")))
      setupMocksForAmend(userAnswersAmends)
      mockReturnsSubmissionConnector(aReturn())

      val result: Future[Result] = sut.amend(pptReference).apply(
        FakeRequest().withHeaders(newHeaders = ("foo", "bar"))
      )

      status(result) mustBe OK
      contentAsJson(result) mustBe toJson(aReturnWithNrs())
      verifySubmitNonRepudiation(createNrsPayload(expectedAmendReturnValues, userAnswersAmends))
    }

    "amend return an error if Direct debit in progress" in {
      withAuthorizedUser()
      setupMocksForAmend(userAnswersAmends)
      setUpFinancialApiMock(true)

      val result: Future[Result] = sut.amend(pptReference).apply(FakeRequest())

      status(result) mustBe UNPROCESSABLE_ENTITY
      verify(mockReturnsConnector, never()).submitReturn(any, any, any)(any)
    }

    "return an error if returns is too old to process" in {
      withAuthorizedUser()
      setupMocksForAmend(userAnswersAmends)
      reset(mockEdgeOfSystem)
      when(mockEdgeOfSystem.localDateTimeNow).thenReturn(LocalDate.of(2027, 1, 29).atStartOfDay())
      setUpFinancialApiMock(true)

      val result: Future[Result] = sut.amend(pptReference).apply(FakeRequest())

      status(result) mustBe UNPROCESSABLE_ENTITY
      verify(mockReturnsConnector, never()).submitReturn(any, any, any)(any)
    }

    "amend throw an error if financial API error" in {
      withAuthorizedUser()
      setupMocksForAmend(userAnswersAmends)
      when(mockFinancialDataService.getFinancials(any, any)(any))
        .thenReturn(Future.successful(Left(500)))

      intercept[RuntimeException] {
        val result: Future[Result] = sut.amend(pptReference).apply(FakeRequest())
        status(result)
      }
      verify(mockReturnsConnector, never()).submitReturn(any, any, any)(any)
    }

    "amend submit return if Direct debit not in progress" in {
      withAuthorizedUser()
      setupMocksForAmend(userAnswersAmends)
      mockReturnsSubmissionConnector(aReturn())

      await(sut.amend(pptReference).apply(FakeRequest()))

      verify(mockReturnsConnector).submitReturn(any, any, any)(any)
    }
  }
  
  "it should complain about missing period end-date" when {

    "calculating a new return" when {

      "credits calculation fails" in {
        withAuthorizedUser()
        setupMocksForSubmit(userAnswersReturns)
        mockReturnsSubmissionConnector(aReturn())
        when(mockCreditsCalculationService.totalRequestedCredit(any, any)) thenThrow new RuntimeException("a field is missing")

        the[Exception] thrownBy await(sut.submit(pptReference)(FakeRequest())) must 
          have message "a field is missing"
      }

      "building ReturnValues fails" in {
        withAuthorizedUser()
        setupMocksForSubmit(userAnswersReturns.removePath(JsPath \ "obligation"))
        mockReturnsSubmissionConnector(aReturn())
        the[Exception] thrownBy await(sut.submit(pptReference)(FakeRequest())) must 
          have message "/obligation/periodKey is missing from user answers"
      }

    }    

    "calculating an amended return" in {
      withAuthorizedUser()
      setupMocksForAmend(userAnswersAmends)
      mockReturnsSubmissionConnector(aReturn())
      when(mockPptCalculationService.calculate(any)) thenThrow new RuntimeException("a field is missing")

      the [Exception] thrownBy await(sut.amend(pptReference)(FakeRequest())) must 
        have message "a field is missing"
    }
  }

  private def verifySubmitNonRepudiation(expectedNrsPayload: NrsReturnOrAmendSubmission) = {
    verify(mockNonRepudiationService).submitNonRepudiation(
      eqTo(NotableEvent.PptReturn),
      eqTo(Json.toJson(expectedNrsPayload).toString),
      any[ZonedDateTime],
      eqTo(pptReference),
      eqTo(Map("Host" -> "localhost", "foo" -> "bar"))
    )(any[HeaderCarrier])
  }

  private def createNrsPayload(returnValue: ReturnValues, userAnswer: UserAnswers) = {
    val eisRequest: ReturnsSubmissionRequest = ReturnsSubmissionRequest(returnValue, calculations)
    NrsReturnOrAmendSubmission(userAnswer.data, eisRequest)
  }

  private def setUpFinancialApiMock(isDdInProgress: Boolean): Any = {
    when(mockFinancialDataService.getFinancials(any, any)(any))
      .thenReturn(Future.successful(Right(FinancialDataResponse(None, None, None, LocalDateTime.now(), Seq.empty))))

    when(mockFinancialsService.lookUpForDdInProgress(eqTo("21C4"), any)).thenReturn(isDdInProgress)
  }

  private def setupMocksForSubmit(userAnswers: UserAnswers) = {

    mockGetObligationDataPeriodKey(pptReference, "21C4")
    when(mockCreditsCalculationService.totalRequestedCredit(any, any)).thenReturn(CreditCalculation(0L, 0, 0, true, Map.empty))
    when(mockAvailableCreditService.getBalance(any)(any)).thenReturn(Future.successful(BigDecimal(10)))
    when(mockSessionRepository.clear(any[String])).thenReturn(Future.successful(true))
    when(mockSessionRepository.get(any[String])).thenReturn(Future.successful(Some(userAnswers)))
    when(mockNonRepudiationService.submitNonRepudiation(any, any, any, any, any)(any)).thenReturn(
      Future.successful(NonRepudiationSubmissionAccepted(nrSubmissionId))
    )
    when(mockPptCalculationService.calculate(any)).thenReturn(calculations)
  }

  private def setupMocksForAmend(userAnswers: UserAnswers) = {
    mockGetObligationDataPeriodKey(pptReference, "21C4")
    setUpFinancialApiMock(false)
    when(mockSessionRepository.get(any[String])).thenReturn(Future.successful(Some(userAnswers)))
    when(mockNonRepudiationService.submitNonRepudiation(any, any, any, any, any)(any)).thenReturn(
      Future.successful(NonRepudiationSubmissionAccepted(nrSubmissionId))
    )
    when(mockPptCalculationService.calculate(any)).thenReturn(calculations)
    when(mockEdgeOfSystem.localDateTimeNow).thenReturn(LocalDate.of(2023, 1, 28).atStartOfDay())
  }

  private def expectedSubmissionRequestForAmend = {
    val iesDetails = EisReturnDetails(100, 1, 1, 3, 7, 5, 0.00, 1, 1)
    ReturnsSubmissionRequest(ReturnType.AMEND, Some("submission12"), "21C4", iesDetails, None)
  }

  private def expectedSubmissionRequestForReturns = {
    val returnDetails = EisReturnDetails(100, 1, 1, 10, 300, 5, 0.00, 1, 1)
    ReturnsSubmissionRequest(ReturnType.NEW, None, "21C4", returnDetails, None)
  }

}
