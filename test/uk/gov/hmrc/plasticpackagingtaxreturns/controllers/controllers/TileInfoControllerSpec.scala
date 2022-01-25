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
import org.mockito.Mockito.{never, reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers.{OK, contentAsJson, defaultAwaitTimeout, route, status, _}
import play.api.test.{FakeRequest, Injecting}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ObligationDataConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{
  Identification,
  Obligation,
  ObligationDataResponse,
  ObligationStatus
}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.TileInfoController
import uk.gov.hmrc.plasticpackagingtaxreturns.models.PPTObligations
import uk.gov.hmrc.plasticpackagingtaxreturns.services.PTPObligationsService

import java.time.LocalDate
import java.util.UUID
import scala.concurrent.Future

class TileInfoControllerSpec
    extends PlaySpec with BeforeAndAfterEach with MockitoSugar with GuiceOneAppPerSuite with Injecting {
  val mockPTPObligations: PTPObligationsService = mock[PTPObligationsService]
  val mockObligationDataConnector               = mock[ObligationDataConnector]
  lazy val sut: TileInfoController              = inject[TileInfoController]
  val testPPTReference: String                  = UUID.randomUUID().toString

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPTPObligations, mockObligationDataConnector)
  }

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[PTPObligationsService].toInstance(mockPTPObligations),
               bind[ObligationDataConnector].toInstance(mockObligationDataConnector)
    )
    .build()

  "get" must {
    val request     = FakeRequest("GET", "/obligations/open/" + testPPTReference)
    val obligations = PPTObligations(false)

    "be accessible from the requestHandler" in { //todo eventually be moved to integration test package
      when(mockPTPObligations.get(any())).thenReturn(obligations)
      when(mockObligationDataConnector.get(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(ObligationDataResponse(Seq.empty))))

      val result: Future[Result] = route(app, request).get

      status(result) must not be NOT_FOUND
    }

    "get PTPObligation from service" in {
      val desResponse = ObligationDataResponse(Seq.empty)

      when(mockPTPObligations.get(any())).thenReturn(obligations)
      when(mockObligationDataConnector.get(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))

      val result: Future[Result] = sut.get(testPPTReference)(request)

      verify(mockPTPObligations).get(ObligationDataResponse(Seq.empty))
      contentAsJson(result) mustBe Json.toJson(obligations)
      status(result) mustBe OK
    }

    "get should call Obligation Connector" in {
      val desResponse = ObligationDataResponse(Seq.empty)

      when(mockObligationDataConnector.get(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))
      when(mockPTPObligations.get(any())).thenReturn(obligations)

      val result: Future[Result] = sut.get(testPPTReference)(request)

      verify(mockObligationDataConnector)
        .get(exactlyEq(testPPTReference),
             exactlyEq(LocalDate.of(2022, 4, 1)),
             exactlyEq(LocalDate.now()),
             exactlyEq(ObligationStatus.OPEN)
        )(any())

      verify(mockPTPObligations).get(desResponse)
    }

    "get should call Obligation Connector and actually call the service with it" in {
      val desResponse = ObligationDataResponse(Seq(Obligation(Identification("", "", ""), Nil)))

      when(mockObligationDataConnector.get(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))
      when(mockPTPObligations.get(any())).thenReturn(obligations)

      val result: Future[Result] = sut.get(testPPTReference)(request)

      verify(mockObligationDataConnector)
        .get(exactlyEq(testPPTReference),
             exactlyEq(LocalDate.of(2022, 4, 1)),
             exactlyEq(LocalDate.now()),
             exactlyEq(ObligationStatus.OPEN)
        )(any())

      verify(mockPTPObligations).get(desResponse)
    }

    "return internal server error response" when {
      "if error return from connector" in {
        val desResponse: Int = 500

        when(mockObligationDataConnector.get(any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(Left(desResponse)))

        val result: Future[Result] = sut.get(testPPTReference)(request)

        verify(mockObligationDataConnector)
          .get(exactlyEq(testPPTReference),
               exactlyEq(LocalDate.of(2022, 4, 1)),
               exactlyEq(LocalDate.now()),
               exactlyEq(ObligationStatus.OPEN)
          )(any())

        verify(mockPTPObligations, never()).get(any())

        status(result) mustBe INTERNAL_SERVER_ERROR

      }

    }

  }

}
