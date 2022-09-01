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

import org.mockito.ArgumentMatchers.{any, refEq}
import org.mockito.Mockito.{never, reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsString, Json, Reads}
import play.api.mvc.{Action, BodyParser, Result}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ExportCreditBalanceConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.exportcreditbalance.ExportCreditBalanceDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.ExportCreditBalanceController
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.{Authenticator, AuthorizedRequest}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.returns.ObligationGettable
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.{CreditsCalculationResponse, Obligation}
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Settable.SettableUserAnswers

import java.time.LocalDate
import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class ExportCreditBalanceControllerSpec extends PlaySpec with BeforeAndAfterEach {

  val mockSessionRepo: SessionRepository = mock[SessionRepository]
  val mockConnector: ExportCreditBalanceConnector = mock[ExportCreditBalanceConnector]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConnector, mockSessionRepo)
  }

  val sut = new ExportCreditBalanceController(
    mockConnector,
    FakeAuthenticator,
    mockSessionRepo,
    Helpers.stubControllerComponents(),
  )(global)

  "get" must {
    "return 200 response with correct values" in {
      def now: LocalDate = LocalDate.now
      val userAnswers = UserAnswers("user-answers-id")
        .setUnsafe(ObligationGettable, Obligation(now, now, now, "now"))

      val creditResponse = ExportCreditBalanceDisplayResponse("date", BigDecimal(0), BigDecimal(0), totalExportCreditAvailable = BigDecimal(200))

      when(mockSessionRepo.get(any()))
        .thenReturn(Future.successful(Some(userAnswers)))
      when(mockConnector.getBalance(any(), any(), any(), any())(any())).thenReturn(Future.successful(Right(creditResponse)))

      val result = sut.get("url-ppt-ref")(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(CreditsCalculationResponse(200, 20))

      withClue("session repo called with the cache key"){
        verify(mockSessionRepo).get(s"some-internal-id-test-ppt-id")
      }

      //todo this verify will be simpler when there is a credit service
      withClue("EIS connector called with correct values"){
        verify(mockConnector).getBalance(refEq("test-ppt-id"), refEq(now.minusYears(2)), refEq(now.minusDays(1)), refEq("some-internal-id"))(any())
      }
    }

    "return 500 internal error" when {
      "The userAnswers does not exist" in {
        when(mockSessionRepo.get(any())).thenReturn(Future.successful(None))

        val result = sut.get("url-ppt-ref")(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj("message" -> JsString("UserAnswers is empty"))

        withClue("EIS connector should not have been called"){
          verify(mockConnector, never()).getBalance(any(), any(), any(), any())(any())
        }
      }

      "The userAnswers does contain an obligation" in {
        when(mockSessionRepo.get(any())).thenReturn(Future.successful(Some(UserAnswers("UserAnswers-id"))))

        val result = sut.get("url-ppt-ref")(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj("message" -> JsString("Obligation not found in user-answers"))

        withClue("EIS connector should not have been called"){
          verify(mockConnector, never()).getBalance(any(), any(), any(), any())(any())
        }
      }

      "getBalance returns an error" in {
        def now: LocalDate = LocalDate.now
        val userAnswers = UserAnswers("user-answers-id")
          .setUnsafe(ObligationGettable, Obligation(now, now, now, "now"))

        when(mockSessionRepo.get(any())).thenReturn(Future.successful(Some(userAnswers)))
        when(mockConnector.getBalance(any(), any(), any(), any())(any())).thenReturn(Future.successful(Left(IM_A_TEAPOT)))

        val result = sut.get("url-ppt-ref")(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj("message" -> JsString("Error calling EIS export credit, status: 418"))
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
