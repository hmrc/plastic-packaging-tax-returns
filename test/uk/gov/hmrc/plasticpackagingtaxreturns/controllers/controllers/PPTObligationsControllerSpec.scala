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

import org.mockito.ArgumentMatchers.{any, eq => exactlyEq}
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.mvc.{ControllerComponents, Result}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ObligationsDataConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise._
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.PPTObligationsController
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.FakeAuthenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.models.PPTObligations
import uk.gov.hmrc.plasticpackagingtaxreturns.services.PPTObligationsService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PPTObligationsControllerSpec extends PlaySpec with BeforeAndAfterEach with MockitoSugar {

  val mockPPTObligationsService: PPTObligationsService      = mock[PPTObligationsService]
  val mockObligationDataConnector: ObligationsDataConnector = mock[ObligationsDataConnector]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPPTObligationsService, mockObligationDataConnector)
  }

  val cc: ControllerComponents = Helpers.stubControllerComponents()

  val sut =
    new PPTObligationsController(
      cc,
      new FakeAuthenticator(cc),
      mockObligationDataConnector,
      mockPPTObligationsService
    )

  "getOpen" must {
    val obligations      = PPTObligations(None, None, 0, isNextObligationDue = false, displaySubmitReturnsLink = false)
    val rightObligations = Right(obligations)
    val pptReference     = "1234"

    "get PTPObligation from service" in {

      val desResponse = ObligationDataResponse(Seq.empty)

      when(mockPPTObligationsService.constructPPTObligations(any())).thenReturn(rightObligations)
      when(mockObligationDataConnector.get(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))

      val result: Future[Result] = sut.getOpen(pptReference).apply(FakeRequest())

      status(result) mustBe OK
      verify(mockPPTObligationsService).constructPPTObligations(ObligationDataResponse(Seq.empty))
      contentAsJson(result) mustBe Json.toJson(obligations)
    }

    "get should call Obligation Connector" in {

      val desResponse = ObligationDataResponse(Seq.empty)

      when(mockObligationDataConnector.get(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))
      when(mockPPTObligationsService.constructPPTObligations(any())).thenReturn(rightObligations)

      sut.getOpen(pptReference).apply(FakeRequest())

      verify(mockObligationDataConnector)
        .get(exactlyEq(pptReference),
             exactlyEq(None),
             exactlyEq(None),
             exactlyEq(Some(ObligationStatus.OPEN))
        )(any())
    }

    "get should call the service" in {
      val desResponse = ObligationDataResponse(Seq(Obligation(Identification("", "", ""), Nil)))

      when(mockObligationDataConnector.get(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))
      when(mockPPTObligationsService.constructPPTObligations(any())).thenReturn(rightObligations)

      await(sut.getOpen(pptReference).apply(FakeRequest()))

      verify(mockPPTObligationsService).constructPPTObligations(desResponse)
    }

    "return internal server error response" when {
      "if error return from connector" in {

        when(mockObligationDataConnector.get(any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(Left(500)))

        val result: Future[Result] = sut.getOpen(pptReference).apply(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
      }

      "if the controller handles an error" in {

        val desResponse = ObligationDataResponse(Seq(Obligation(Identification("", "", ""), Nil)))
        when(mockObligationDataConnector.get(any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(Right(desResponse)))
        when(mockPPTObligationsService.constructPPTObligations(any())).thenReturn(Left("test error message"))

        val result: Future[Result] = sut.getOpen(pptReference).apply(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }
  }

  "getFulfilled" must {
    val serviceResponse = Right(Seq.empty)
    val pptReference     = "1234"
    val desResponse = ObligationDataResponse(Seq.empty)

    "get PTPObligation from construct service" in {
      when(mockPPTObligationsService.constructPPTFulfilled(any())).thenReturn(serviceResponse)
      when(mockObligationDataConnector.get(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))

      val result: Future[Result] = sut.getFulfilled(pptReference).apply(FakeRequest())

      status(result) mustBe OK
      verify(mockPPTObligationsService).constructPPTFulfilled(ObligationDataResponse(Seq.empty))
      contentAsJson(result) mustBe Json.toJson(Seq.empty[ObligationDetail])
    }

    "call Obligation Connector" in {
      when(mockObligationDataConnector.get(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))
      when(mockPPTObligationsService.constructPPTFulfilled(any())).thenReturn(serviceResponse)

      sut.getFulfilled(pptReference).apply(FakeRequest())

      verify(mockObligationDataConnector)
        .get(exactlyEq(pptReference),
          exactlyEq(None),
          exactlyEq(None),
          exactlyEq(Some(ObligationStatus.FULFILLED))
        )(any())
    }

    "return internal server error response" when {
      "an error is returned from connector" in {
        when(mockObligationDataConnector.get(any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(Left(500)))

        val result: Future[Result] = sut.getFulfilled(pptReference).apply(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
      }

      "an error is returned from the construct service" in {
        when(mockObligationDataConnector.get(any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(Right(desResponse)))
        when(mockPPTObligationsService.constructPPTFulfilled(any())).thenReturn(Left("test error message"))

        val result: Future[Result] = sut.getFulfilled(pptReference).apply(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }
  }
}
