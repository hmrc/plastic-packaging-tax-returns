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
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status.{BAD_REQUEST, NOT_FOUND}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.libs.json.Json.toJson
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, SERVICE_UNAVAILABLE, contentAsJson, defaultAwaitTimeout, route, status, writeableOf_AnyContentAsEmpty, writeableOf_AnyContentAsJson}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, HttpException}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ReturnsConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.{EisReturnDetails, NrsReturnOrAmendSubmission, Return, ReturnWithNrsFailureResponse, ReturnWithNrsSuccessResponse, ReturnsSubmissionRequest}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.unit.{MockConnectors, MockReturnsRepository}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.builders.{ReturnsSubmissionResponseBuilder, TaxReturnBuilder}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation.NonRepudiationSubmissionAccepted
import uk.gov.hmrc.plasticpackagingtaxreturns.models.{ManufacturedPlasticWeight, TaxReturn}
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService

import java.time.ZonedDateTime
import scala.concurrent.Future

class ReturnsSubmissionControllerSpec
    extends AnyWordSpec with GuiceOneAppPerSuite with BeforeAndAfterEach with ScalaFutures with Matchers
    with AuthTestSupport with MockConnectors with MockReturnsRepository with TaxReturnBuilder
    with ReturnsSubmissionResponseBuilder {

  SharedMetricRegistries.clear()

  protected val mockNonRepudiationService: NonRepudiationService = mock[NonRepudiationService]
  private val mockAppConfig: AppConfig                           = mock[AppConfig]
  private val nrSubmissionId: String                             = "nrSubmissionId"

  private val userAnswersData = Json.parse(
    """{
      |        "obligation" : {
      |            "fromDate" : "2021-10-01",
      |            "toDate" : "2021-12-01",
      |            "dueDate" : "2022-02-28",
      |            "periodKey" : "21C4"
      |        },
      |        "startYourReturn" : true,
      |        "manufacturedPlasticPackaging" : true,
      |        "manufacturedPlasticPackagingWeight" : 100,
      |        "importedPlasticPackaging" : false,
      |        "importedPlasticPackagingWeight" : 0,
      |        "directlyExportedComponents" : false,
      |        "exportedPlasticPackagingWeight" : 0,
      |        "nonExportedHumanMedicinesPlasticPackaging" : true,
      |        "nonExportedHumanMedicinesPlasticPackagingWeight" : 10,
      |        "nonExportRecycledPlasticPackaging" : false,
      |        "nonExportRecycledPlasticPackagingWeight" : 0
      |    }""".stripMargin)

  private val userAnswers: UserAnswers = UserAnswers("id").copy(data =
    Json.obj("data" -> userAnswersData)
  )

  when(mockAppConfig.taxRatePoundsPerKg).thenReturn(BigDecimal("0.25"))

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockSessionRepository.clear(any[String]())).thenReturn(Future.successful(true))
    reset(mockAuthConnector)
    reset(mockSessionRepository)
    reset(mockNonRepudiationService)
  }

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[AuthConnector].to(mockAuthConnector),
               bind[ReturnsConnector].to(mockReturnsConnector),
               bind[SessionRepository].to(mockSessionRepository),
               bind[NonRepudiationService].to(mockNonRepudiationService),
               bind[AppConfig].to(mockAppConfig)
    )
    .build()

  "Returns submission controller" should {
    "submit a return via the returns connector" in {
      returnSubmittedAsExpected(pptReference, aTaxReturn())
    }

    "submit an amendment via the returns controller" in {
      amendSubmittedAsExpected(pptReference)
    }

    "delete a return after successful submission" in {
      val taxReturn = aTaxReturn()
      returnSubmittedAsExpected(pptReference, taxReturn)

      verify(mockSessionRepository).clear("Int-ba17b467-90f3-42b6-9570-73be7b78eb2b")
    }

    "delete a return after successful amend" in {
      amendSubmittedAsExpected(pptReference)

      verify(mockSessionRepository).clear("Int-ba17b467-90f3-42b6-9570-73be7b78eb2b")
    }

    "respond successfully when return submission is successful but the return delete fails" in {
      when(mockSessionRepository.clear(any[String]())).thenReturn(Future.failed(new RuntimeException("BANG!")))

      val taxReturn = aTaxReturn()
      returnSubmittedAsExpected(pptReference, taxReturn)
    }

    "respond successfully when return amend is successful but the return delete fails" in {
      when(mockSessionRepository.clear(any[String]())).thenReturn(Future.failed(new RuntimeException("BANG!")))

      amendSubmittedAsExpected(pptReference)
    }

    "use the tax rate defined in config" in {
      val taxReturn = aTaxReturn().copy(manufacturedPlasticWeight = Some(ManufacturedPlasticWeight(1000)))

      val returnsSubmissionRequestCaptor: ArgumentCaptor[ReturnsSubmissionRequest] =
        ArgumentCaptor.forClass(classOf[ReturnsSubmissionRequest])

      returnSubmittedAsExpected(pptReference, taxReturn)

      verify(mockReturnsConnector).submitReturn(any(), returnsSubmissionRequestCaptor.capture())(any())

      returnsSubmissionRequestCaptor.getValue.returnDetails.taxDue mustBe 1000 * BigDecimal("0.25")
    }

    "handle NRS fail" in {
      withAuthorizedUser()

      val nrsErrorMessage = "Something went wrong"

      when(mockSessionRepository.get(any[String])).thenReturn(Future.successful(Some(userAnswers)))
      when(mockSessionRepository.clear(any[String]())).thenReturn(Future.successful(true))

      when(mockNonRepudiationService.submitNonRepudiation(any(), any(), any(), any())(any())).thenReturn(
        Future.failed(new HttpException(nrsErrorMessage, SERVICE_UNAVAILABLE))
      )

      val returnsSubmissionResponse: Return = aReturn()
      val returnsSubmissionResponseWithNrsFail: ReturnWithNrsFailureResponse = aReturnWithNrsFailure()

      mockReturnsSubmissionConnector(returnsSubmissionResponse)

      val submitReturnRequest = FakeRequest("POST", s"/returns-submission/$pptReference").withHeaders(newHeaders = ("foo", "bar"))

      val result: Future[Result] = route(app, submitReturnRequest.withJsonBody(toJson(aTaxReturn()))).get

      status(result) mustBe OK
      contentAsJson(result) mustBe toJson(returnsSubmissionResponseWithNrsFail)
    }


    "get return to display" should {
      "return OK response" in {
        withAuthorizedUser()
        val periodKey = "22CC"
        val returnDisplayResponse = aReturn(
          withReturnDetails(returnDetails =
            Some(
              EisReturnDetails(manufacturedWeight = BigDecimal(256.12),
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
      mockReturnsSubmissionConnectorFailure(BAD_REQUEST)

      val submitReturnRequest = FakeRequest("POST", s"/returns-submission/$pptReference")

      val result: Future[Result] = route(app, submitReturnRequest.withJsonBody(toJson(aTaxReturn()))).get

      status(result) mustBe BAD_REQUEST
    }
  }

  private def amendSubmittedAsExpected(pptReference: String) = {

    val updatedTaxReturn = aTaxReturn(withManufacturedPlasticWeight(1000),
                                      withImportedPlasticWeight(2000),
                                      withHumanMedicinesPlasticWeight(3000),
                                      withDirectExportDetails(4000),
                                      withRecycledPlasticWeight(5000)
    )

    withAuthorizedUser()

    when(mockSessionRepository.get(any[String])).thenReturn(Future.successful(Some(userAnswers)))
    when(mockSessionRepository.clear(any[String]())).thenReturn(Future.successful(true))
    when(mockNonRepudiationService.submitNonRepudiation(any(), any(), any(), any())(any())).thenReturn(
      Future.successful(NonRepudiationSubmissionAccepted(nrSubmissionId))
    )

    val returnsSubmissionResponse: Return                              = aReturn()
    val returnsSubmissionResponseWithNrs: ReturnWithNrsSuccessResponse = aReturnWithNrs()

    val eisRequest: ReturnsSubmissionRequest = ReturnsSubmissionRequest(
      updatedTaxReturn,
      mockAppConfig.taxRatePoundsPerKg,
      Some("submission12")
    )

    mockReturnsSubmissionConnector(returnsSubmissionResponse)

    val submitAmendRequest     = FakeRequest("PUT", s"/returns-amend/$pptReference/submission12").withHeaders(newHeaders = ("foo", "bar"))
    val result: Future[Result] = route(app, submitAmendRequest.withJsonBody(toJson(updatedTaxReturn))).get

    status(result) mustBe OK
    contentAsJson(result) mustBe toJson(returnsSubmissionResponseWithNrs)

    val contentLength      = toJson(updatedTaxReturn).toString.length.toString
    val expectedHeaders    = Map("Host" -> "localhost", "foo" -> "bar", "Content-Length" -> contentLength, "Content-Type" -> "application/json")
    val expectedNrsPayload = NrsReturnOrAmendSubmission(userAnswers.data, eisRequest)

    verify(mockNonRepudiationService).submitNonRepudiation(
      ArgumentMatchers.eq(Json.toJson(expectedNrsPayload).toString),
      any[ZonedDateTime],
      ArgumentMatchers.eq(pptReference),
      ArgumentMatchers.eq(expectedHeaders)
    )(any[HeaderCarrier])

  }

  private def returnSubmittedAsExpected(pptReference: String, taxReturn: TaxReturn) = {
    withAuthorizedUser()

    when(mockSessionRepository.get(any[String])).thenReturn(Future.successful(Some(userAnswers)))
    when(mockSessionRepository.clear(any[String]())).thenReturn(Future.successful(true))
    when(mockNonRepudiationService.submitNonRepudiation(any(), any(), any(), any())(any())).thenReturn(
      Future.successful(NonRepudiationSubmissionAccepted(nrSubmissionId))
    )

    val returnsSubmissionResponse: Return = aReturn()
    val returnsSubmissionResponseWithNrs: ReturnWithNrsSuccessResponse = aReturnWithNrs()

    val eisRequest: ReturnsSubmissionRequest = ReturnsSubmissionRequest(
      taxReturn,
      mockAppConfig.taxRatePoundsPerKg,
      None
    )

    mockReturnsSubmissionConnector(returnsSubmissionResponse)

    val submitReturnRequest    = FakeRequest("POST", s"/returns-submission/$pptReference").withHeaders(newHeaders = ("foo", "bar"))
    val result: Future[Result] = route(app, submitReturnRequest.withJsonBody(toJson(taxReturn))).get

    status(result) mustBe OK
    contentAsJson(result) mustBe toJson(returnsSubmissionResponseWithNrs)

    val contentLength      = toJson(taxReturn).toString.length.toString
    val expectedHeaders    = Map("Host" -> "localhost", "foo" -> "bar", "Content-Length" -> contentLength, "Content-Type" -> "application/json")
    val expectedNrsPayload = NrsReturnOrAmendSubmission(userAnswers.data, eisRequest)

    verify(mockNonRepudiationService).submitNonRepudiation(
      ArgumentMatchers.eq(Json.toJson(expectedNrsPayload).toString),
      any[ZonedDateTime],
      ArgumentMatchers.eq(pptReference),
      ArgumentMatchers.eq(expectedHeaders)
    )(any[HeaderCarrier])
  }

}
