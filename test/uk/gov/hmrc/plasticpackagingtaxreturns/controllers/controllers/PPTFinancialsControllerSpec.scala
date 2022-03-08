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
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.FinancialDataConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{
  FinancialDataResponse,
  Identification,
  Obligation,
  ObligationDataResponse
}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.PPTFinancialsController
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.FakeAuthenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.models.PPTFinancials
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.TaxReturnRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.PPTFinancialsService

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PPTFinancialsControllerSpec extends PlaySpec with BeforeAndAfterEach with MockitoSugar {

  val mockPPTFinancialsService: PPTFinancialsService     = mock[PPTFinancialsService]
  val mockFinancialDataConnector: FinancialDataConnector = mock[FinancialDataConnector]
  val mockTaxReturnRepository: TaxReturnRepository       = mock[TaxReturnRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPPTFinancialsService, mockFinancialDataConnector, mockTaxReturnRepository)
  }

  val cc: ControllerComponents = Helpers.stubControllerComponents()

  val sut =
    new PPTFinancialsController(cc, new FakeAuthenticator(cc), mockFinancialDataConnector, mockPPTFinancialsService)

  "get" must {
    val financials   = PPTFinancials(None, None, None)
    val pptReference = "1234"
    val desResponse  = FinancialDataResponse(None, None, None, LocalDateTime.now(), Seq.empty)

    "get PTPObligation from service" in {
      when(mockPPTFinancialsService.construct(any())).thenReturn(financials)
      when(mockFinancialDataConnector.get(any(), any(), any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))

      val result: Future[Result] = sut.get(pptReference).apply(FakeRequest())

      status(result) mustBe OK
      verify(mockPPTFinancialsService).construct(desResponse)
      contentAsJson(result) mustBe Json.toJson(financials)
    }

    "get should call Obligation Connector" in {

      when(mockPPTFinancialsService.construct(any())).thenReturn(financials)
      when(mockFinancialDataConnector.get(any(), any(), any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))

      sut.get(pptReference).apply(FakeRequest())

      verify(mockFinancialDataConnector)
        .get(exactlyEq(pptReference),
             exactlyEq(LocalDate.of(2022, 4, 1)),
             exactlyEq(LocalDate.now()),
             exactlyEq(Some(true)),
             exactlyEq(Some(true)),
             exactlyEq(Some(true)),
             exactlyEq(Some(true)) //todo confirm these flags
        )(any())
    }

    "get should call the service" in {
      when(mockPPTFinancialsService.construct(any())).thenReturn(financials)
      when(mockFinancialDataConnector.get(any(), any(), any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))

      await(sut.get(pptReference).apply(FakeRequest()))

      verify(mockPPTFinancialsService).construct(desResponse)
    }

    "return internal server error response" when {
      "if error return from connector" in {
        when(mockFinancialDataConnector.get(any(), any(), any(), any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(Left(500)))

        val result: Future[Result] = sut.get(pptReference).apply(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }
  }
}
