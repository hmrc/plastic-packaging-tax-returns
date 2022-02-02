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
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ObligationDataConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{
  Identification,
  Obligation,
  ObligationDataResponse,
  ObligationStatus
}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.TileInfoController
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.SubscriptionTestData
import uk.gov.hmrc.plasticpackagingtaxreturns.models.PPTObligations
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.TaxReturnRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.PPTObligationsService

import java.time.LocalDate
import java.util.UUID
import scala.concurrent.Future

class TileInfoControllerSpec
    extends PlaySpec with AuthTestSupport with SubscriptionTestData with BeforeAndAfterEach with MockitoSugar
    with GuiceOneAppPerSuite with Injecting {

  val mockPPTObligationsService: PPTObligationsService = mock[PPTObligationsService]
  val mockObligationDataConnector               = mock[ObligationDataConnector]
  val mockTaxReturnRepository                   = mock[TaxReturnRepository]
  lazy val sut: TileInfoController              = inject[TileInfoController]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector, mockPPTObligationsService, mockObligationDataConnector, mockTaxReturnRepository)

  }

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[PPTObligationsService].toInstance(mockPPTObligationsService),
               bind[ObligationDataConnector].toInstance(mockObligationDataConnector),
               bind[AuthConnector].toInstance(mockAuthConnector),
               bind[TaxReturnRepository].toInstance(mockTaxReturnRepository)
    )
    .build()

  "get" must {
    val request          = FakeRequest("GET", "/obligations/open/" + pptReference)
    val obligations      = PPTObligations(None, None, 0, false, false)
    val rightObligations = Right(obligations)

    "be accessible from the requestHandler" in { //todo eventually be moved to integration test package
      withAuthorizedUser()
      when(mockPPTObligationsService.get(any())).thenReturn(rightObligations)
      when(mockObligationDataConnector.get(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(ObligationDataResponse(Seq.empty))))

      val result: Future[Result] = route(app, request).get

      status(result) must not be NOT_FOUND
    }

    "get PTPObligation from service" in {
      withAuthorizedUser()
      val desResponse = ObligationDataResponse(Seq.empty)

      when(mockPPTObligationsService.get(any())).thenReturn(rightObligations)
      when(mockObligationDataConnector.get(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))

      val result: Future[Result] = sut.get(pptReference)(request)

      status(result) mustBe OK
      verify(mockPPTObligationsService).get(ObligationDataResponse(Seq.empty))
      contentAsJson(result) mustBe Json.toJson(obligations)
    }

    "get should call Obligation Connector" in {
      withAuthorizedUser()
      val desResponse = ObligationDataResponse(Seq.empty)

      when(mockObligationDataConnector.get(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))
      when(mockPPTObligationsService.get(any())).thenReturn(rightObligations)

      await(sut.get(pptReference)(request))

      verify(mockObligationDataConnector)
        .get(exactlyEq(pptReference),
             exactlyEq(LocalDate.of(2022, 4, 1)),
             exactlyEq(LocalDate.now()),
             exactlyEq(ObligationStatus.OPEN)
        )(any())
    }

    "get should call the service" in {
      withAuthorizedUser()

      val desResponse = ObligationDataResponse(Seq(Obligation(Identification("", "", ""), Nil)))

      when(mockObligationDataConnector.get(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(desResponse)))
      when(mockPPTObligationsService.get(any())).thenReturn(rightObligations)

      await(sut.get(pptReference)(request))

      verify(mockPPTObligationsService).get(desResponse)
    }

    "return internal server error response" when {
      "if error return from connector" in {
        withAuthorizedUser()
        when(mockObligationDataConnector.get(any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(Left(500)))

        val result: Future[Result] = sut.get(pptReference)(request)

        status(result) mustBe INTERNAL_SERVER_ERROR
      }

      "if the controller handles an error" in {
        withAuthorizedUser()
        val desResponse = ObligationDataResponse(Seq(Obligation(Identification("", "", ""), Nil)))
        when(mockObligationDataConnector.get(any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(Right(desResponse)))
        when(mockPPTObligationsService.get(any())).thenReturn(Left("test error message"))

        val result: Future[Result] = sut.get(pptReference)(request)

        status(result) mustBe SERVICE_UNAVAILABLE
      }
    }
  }
}
