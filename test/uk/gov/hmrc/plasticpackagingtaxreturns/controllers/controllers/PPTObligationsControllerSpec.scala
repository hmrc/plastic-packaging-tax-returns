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

import org.mockito.ArgumentMatchers.{any, eq => exactlyEq}
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.mvc.{ControllerComponents, Result}
import play.api.test.Helpers.{status, _}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ObligationsDataConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise._
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.PPTObligationsController
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.FakeAuthenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.models.PPTObligations
import uk.gov.hmrc.plasticpackagingtaxreturns.services.PPTObligationsService
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EdgeOfSystem

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PPTObligationsControllerSpec extends PlaySpec with BeforeAndAfterEach with MockitoSugar {

  private val mockPPTObligationsService = mock[PPTObligationsService]
  private val mockObligationDataConnector = mock[ObligationsDataConnector]
  private val appConfig = mock[AppConfig]
  private val edgeOfSystem = mock[EdgeOfSystem]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(appConfig, mockPPTObligationsService, mockObligationDataConnector)

    when(edgeOfSystem.today) thenReturn LocalDate.now() // test uses actual clock
  }

  val cc: ControllerComponents = Helpers.stubControllerComponents()

  val sut = new PPTObligationsController(cc, new FakeAuthenticator(cc), mockObligationDataConnector,
    mockPPTObligationsService, appConfig, edgeOfSystem)

  "getOpen" must {
    val obligations      = PPTObligations(None, None, 0, isNextObligationDue = false, displaySubmitReturnsLink = false)
    val rightObligations = Right(obligations)
    val pptReference     = "1234"

    "get response 200" in {

      val desResponse = ObligationDataResponse(Seq.empty)

      when(mockPPTObligationsService.constructPPTObligations(any())).thenReturn(rightObligations)
      when(mockObligationDataConnector.get(any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))

      val result: Future[Result] = sut.getOpen(pptReference).apply(FakeRequest())

      status(result) mustBe OK
    }

    "get should call Obligation Connector" in {

      val desResponse = ObligationDataResponse(Seq.empty)

      when(mockObligationDataConnector.get(any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))

      sut.getOpen(pptReference).apply(FakeRequest())

      verify(mockObligationDataConnector)
        .get(exactlyEq(pptReference), any(), exactlyEq(None), exactlyEq(None), exactlyEq(Some(ObligationStatus.OPEN)))(any())
    }

    "get should call the service when response is successful" in {
      val desResponse = ObligationDataResponse(Seq(Obligation(Some(Identification(Some(""), "", "")), Nil)))

      when(mockObligationDataConnector.get(any(), any(), any(), any() ,any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))
      when(mockPPTObligationsService.constructPPTObligations(any())).thenReturn(rightObligations)

      await(sut.getOpen(pptReference).apply(FakeRequest()))

      verify(mockPPTObligationsService).constructPPTObligations(desResponse)
    }

    "return internal server error response" when {
      "if error return from connector" in {

        when(mockObligationDataConnector.get(any(), any(), any(), any() ,any())(any()))
          .thenReturn(Future.successful(Left(500)))

        val result: Future[Result] = sut.getOpen(pptReference).apply(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
      }

      "if the controller handles an error" in {

        val desResponse = ObligationDataResponse(Seq(Obligation(Some(Identification(Some(""), "", "")), Nil)))
        when(mockObligationDataConnector.get(any(), any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(Right(desResponse)))
        when(mockPPTObligationsService.constructPPTObligations(any())).thenReturn(Left("test error message"))

        val result: Future[Result] = sut.getOpen(pptReference).apply(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }
    "return not found response" must {
      "if the connector gives a 404" in {
        when(mockObligationDataConnector.get(any(), any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(Left(NOT_FOUND)))

        val result = sut.getOpen(pptReference).apply(FakeRequest())

        status(result) mustBe NOT_FOUND
      }
    }
  }

  "getFulfilled" must {
    val serviceResponse = Right(Seq.empty)
    val pptReference    = "1234"
    val desResponse     = ObligationDataResponse(Seq.empty)

    "get PTPObligation from construct service" in {
      when(mockPPTObligationsService.constructPPTFulfilled(any())).thenReturn(serviceResponse)
      when(mockObligationDataConnector.get(any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))

      val result: Future[Result] = sut.getFulfilled(pptReference).apply(FakeRequest())

      status(result) mustBe OK
      verify(mockPPTObligationsService).constructPPTFulfilled(ObligationDataResponse(Seq.empty))
      verify(mockObligationDataConnector).get(
        exactlyEq(pptReference),
        any(),
        exactlyEq(Some(LocalDate.of(2022, 4, 1))),
        exactlyEq(Some(LocalDate.now)),
        exactlyEq(Some(ObligationStatus.FULFILLED))
      )(any())
      contentAsJson(result) mustBe Json.toJson(Seq.empty[ObligationDetail])
    }

    "get future period fulfilled obligation if flag set" in {
      when(appConfig.qaTestingInProgress).thenReturn(true)
      when(mockPPTObligationsService.constructPPTFulfilled(any())).thenReturn(serviceResponse)
      when(mockObligationDataConnector.get(any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))

      val result: Future[Result] = sut.getFulfilled(pptReference).apply(FakeRequest())

      status(result) mustBe OK
      verify(mockPPTObligationsService).constructPPTFulfilled(desResponse)
      verify(mockObligationDataConnector).get(
        exactlyEq(pptReference),
        any(),
        exactlyEq(Some(LocalDate.of(2022, 4, 1))),
        exactlyEq(Some(LocalDate.now.plusYears(1))),
        exactlyEq(Some(ObligationStatus.FULFILLED))
      )(any())
    }

    "not get future period fulfilled obligation if flag not set" in {
      when(appConfig.qaTestingInProgress).thenReturn(false)
      when(mockPPTObligationsService.constructPPTFulfilled(any())).thenReturn(serviceResponse)
      when(mockObligationDataConnector.get(any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse))).thenReturn(Future.successful(Right(desResponse)))

      val result: Future[Result] = sut.getFulfilled(pptReference).apply(FakeRequest())
      await(result)

      verify(mockObligationDataConnector).get(
        exactlyEq(pptReference),
        any(),
        exactlyEq(Some(LocalDate.of(2022, 4, 1))),
        exactlyEq(Some(LocalDate.now)),
        exactlyEq(Some(ObligationStatus.FULFILLED))
      )(any())
    }

    "call Obligation Connector" in {
      when(mockObligationDataConnector.get(any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))
      when(mockPPTObligationsService.constructPPTFulfilled(any())).thenReturn(serviceResponse)

      sut.getFulfilled(pptReference).apply(FakeRequest())

      verify(mockObligationDataConnector)
        .get(
          exactlyEq(pptReference),
          any(),
          exactlyEq(Some(LocalDate.of(2022, 4, 1))),
          exactlyEq(Some(LocalDate.now())),
          exactlyEq(Some(ObligationStatus.FULFILLED))
        )(any())
    }

    "return internal server error response" when {
      "an error is returned from connector" in {
        when(mockObligationDataConnector.get(any(), any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(Left(500)))

        val result: Future[Result] = sut.getFulfilled(pptReference).apply(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
      }

      "an error is returned from the construct service" in {
        when(mockObligationDataConnector.get(any(), any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(Right(desResponse)))
        when(mockPPTObligationsService.constructPPTFulfilled(any())).thenReturn(Left("test error message"))

        val result: Future[Result] = sut.getFulfilled(pptReference).apply(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }
    "return not found response" must {
      "if the connector gives a 404" in {
        when(mockObligationDataConnector.get(any(), any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(Left(NOT_FOUND)))

        val result = sut.getFulfilled(pptReference).apply(FakeRequest())

        status(result) mustBe NOT_FOUND
      }
    }
  }
}
