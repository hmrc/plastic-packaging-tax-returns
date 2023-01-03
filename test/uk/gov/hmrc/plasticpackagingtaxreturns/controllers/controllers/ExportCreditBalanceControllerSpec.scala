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

import org.mockito.ArgumentMatchers.{any, refEq}
import org.mockito.Mockito.{never, reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsString, Json, Reads}
import play.api.mvc.{Action, BodyParser, Result}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.ExportCreditBalanceController
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.{Authenticator, AuthorizedRequest}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.returns.ReturnObligationFromDateGettable
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.CreditsCalculationResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.CreditsCalculationService.Credit
import uk.gov.hmrc.plasticpackagingtaxreturns.services.{AvailableCreditService, CreditsCalculationService}
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Settable.SettableUserAnswers

import java.time.LocalDate
import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class ExportCreditBalanceControllerSpec extends PlaySpec with BeforeAndAfterEach {

  val mockSessionRepo: SessionRepository = mock[SessionRepository]
  val mockCreditsCalcService = mock[CreditsCalculationService]
  val mockAvailableCreditsService = mock[AvailableCreditService]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAvailableCreditsService, mockSessionRepo, mockCreditsCalcService)
  }

  val sut = new ExportCreditBalanceController(
    FakeAuthenticator,
    mockSessionRepo,
    mockCreditsCalcService,
    mockAvailableCreditsService,
    Helpers.stubControllerComponents(),
  )(global)

  "get" must {
    def now: LocalDate = LocalDate.now
    "return 200 response with correct values" in {
      val userAnswers = UserAnswers("user-answers-id")
        .setUnsafe(ReturnObligationFromDateGettable, now)

      val available = BigDecimal(200)
      val requested = Credit(100L, BigDecimal(20))

      when(mockSessionRepo.get(any()))
        .thenReturn(Future.successful(Some(userAnswers)))
      when(mockAvailableCreditsService.getBalance(any())(any())).thenReturn(Future.successful(available))
      when(mockCreditsCalcService.totalRequestedCredit(any())).thenReturn(requested)

      val result = sut.get("url-ppt-ref")(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(CreditsCalculationResponse(available, requested.moneyInPounds, requested.weight))

      withClue("session repo called with the cache key"){
        verify(mockSessionRepo).get(s"some-internal-id-test-ppt-id")
      }

      withClue("credits calculation service not called"){
        verify(mockCreditsCalcService).totalRequestedCredit(userAnswers)
      }

      withClue("EIS connector called with the user answer"){
        verify(mockAvailableCreditsService).getBalance(refEq(userAnswers))(any())
      }
    }

    "return 500 internal error" when {
      "session repo fails" in {
        when(mockSessionRepo.get(any())).thenReturn(Future.failed(new Exception("boom")))

        val result = sut.get("url-ppt-ref")(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj("message" -> JsString("boom"))

        withClue("available credit should not have been called"){
          verify(mockAvailableCreditsService, never()).getBalance(any())(any())
        }
        withClue("calculator should not have been called"){
          verify(mockCreditsCalcService, never()).totalRequestedCredit(any())
        }
      }

      "session repo is empty" in {
        when(mockSessionRepo.get(any())).thenReturn(Future.successful(None))

        val result = sut.get("url-ppt-ref")(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj("message" -> JsString("UserAnswers is empty"))

        withClue("available credit should not have been called"){
          verify(mockAvailableCreditsService, never()).getBalance(any())(any())
        }
        withClue("calculator should not have been called"){
          verify(mockCreditsCalcService, never()).totalRequestedCredit(any())
        }
      }

      "The available credits service fails" in {
        when(mockSessionRepo.get(any())).thenReturn(Future.successful(Some(UserAnswers(""))))
        when(mockAvailableCreditsService.getBalance(any())(any())).thenReturn(Future.failed(new Exception("test message")))

        val result = sut.get("url-ppt-ref")(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj("message" -> JsString("test message"))

        withClue("calculator should not have been called"){
          verify(mockCreditsCalcService, never()).totalRequestedCredit(any())
        }
      }

      "getBalance returns an error" in {
        val userAnswers = UserAnswers("user-answers-id")
          .setUnsafe(ReturnObligationFromDateGettable, now)

        when(mockSessionRepo.get(any())).thenReturn(Future.successful(Some(userAnswers)))
        when(mockAvailableCreditsService.getBalance(any())(any())).thenReturn(Future.failed(new Exception("test error")))

        val result = sut.get("url-ppt-ref")(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj("message" -> JsString("test error"))
      }
    }
  }


  //todo the world should know this exists
  object FakeAuthenticator extends Authenticator {
    override def authorisedAction[A](bodyParser: BodyParser[A], pptReference: String)(body: AuthorizedRequest[A] => Future[Result]): Action[A] =
      Helpers.stubControllerComponents().actionBuilder.async(bodyParser) { implicit request =>
        body(AuthorizedRequest("test-ppt-id", request, "some-internal-id"))
      }

    override def parsingJson[T](implicit rds: Reads[T]): BodyParser[T] = ???
  }
}
