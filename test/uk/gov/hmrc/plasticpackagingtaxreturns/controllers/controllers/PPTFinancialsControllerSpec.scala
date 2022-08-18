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
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.FinancialDataResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.PPTFinancialsController
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.FakeAuthenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.models.PPTFinancials
import uk.gov.hmrc.plasticpackagingtaxreturns.services.{FinancialDataService, PPTFinancialsService}

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PPTFinancialsControllerSpec extends PlaySpec with BeforeAndAfterEach with MockitoSugar {

  private val mockPPTFinancialsService: PPTFinancialsService = mock[PPTFinancialsService]
  private val mockFinancialDataService: FinancialDataService = mock[FinancialDataService]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPPTFinancialsService, mockFinancialDataService)
  }

  private val cc: ControllerComponents = Helpers.stubControllerComponents()
  private val sut = new PPTFinancialsController(cc, new FakeAuthenticator(cc), mockFinancialDataService, 
    mockPPTFinancialsService)
  private val financials = PPTFinancials(None, None, None)
  private val desResponse = FinancialDataResponse(None, None, None, LocalDateTime.now(), Seq.empty)

  "get" must {
    val pptReference = "1234"

    "get PTPFinancial from service" in {
      when(mockPPTFinancialsService.construct(any())).thenReturn(financials)
      when(mockFinancialDataService.getFinancials(any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))

      val result: Future[Result] = sut.get(pptReference).apply(FakeRequest())

      status(result) mustBe OK
      verify(mockPPTFinancialsService).construct(desResponse)
      contentAsJson(result) mustBe Json.toJson(financials)
    }

    "get should call Financial Connector" in {

      when(mockPPTFinancialsService.construct(any())).thenReturn(financials)
      when(mockFinancialDataService.getFinancials(any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))

      sut.get(pptReference).apply(FakeRequest())

      verify(mockFinancialDataService).getFinancials(exactlyEq(pptReference), any())(any())
    }

    "get should call the service" in {
      when(mockPPTFinancialsService.construct(any())).thenReturn(financials)
      when(mockFinancialDataService.getFinancials(any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))

      await(sut.get(pptReference).apply(FakeRequest()))

      verify(mockPPTFinancialsService).construct(desResponse)
    }

    "return internal server error response" when {
      "if error return from connector" in {
        when(mockFinancialDataService.getFinancials(any(), any())(any()))
          .thenReturn(Future.successful(Left(500)))

        val result: Future[Result] = sut.get(pptReference).apply(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }
  }
  
  "isDdInProgress" should {
    
    "respond with false" in {
      when(mockPPTFinancialsService.tempMethodName(any(), any())).thenReturn(false)
      when(mockFinancialDataService.getFinancials(any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))
      val result = sut.isDdInProgress("ppt-ref", "period-key").apply(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe Json.parse(
        """
        {
          "pptReference": "ppt-ref", 
          "periodKey": "period-key", 
          "isDdCollectionInProgress": "false" 
        }
        """)
      verify(mockPPTFinancialsService).tempMethodName(exactlyEq("period-key"), any())
      verify(mockFinancialDataService).getFinancials(exactlyEq("ppt-ref"), any()) (any())
    }

    "respond with true" in {
      when(mockPPTFinancialsService.tempMethodName(any(), any())).thenReturn(true)
      when(mockFinancialDataService.getFinancials(any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))
      val result = sut.isDdInProgress("ppt-ref", "period-key").apply(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe Json.parse(
        """
        {
          "pptReference": "ppt-ref", 
          "periodKey": "period-key", 
          "isDdCollectionInProgress": "true" 
        }
        """)
      verify(mockPPTFinancialsService).tempMethodName(exactlyEq("period-key"), any())
      verify(mockFinancialDataService).getFinancials(exactlyEq("ppt-ref"), any()) (any())
    }
  }
}
