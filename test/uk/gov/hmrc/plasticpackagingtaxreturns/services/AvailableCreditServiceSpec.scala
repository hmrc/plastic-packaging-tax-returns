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

package uk.gov.hmrc.plasticpackagingtaxreturns.services

import org.mockito.ArgumentMatchers.{any, refEq}
import org.mockito.Mockito.{reset, verify, verifyNoInteractions, verifyNoMoreInteractions, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.IM_A_TEAPOT
import play.api.test.FakeRequest
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ExportCreditBalanceConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.exportcreditbalance.ExportCreditBalanceDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.AuthorizedRequest
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.returns.{ConvertedCreditWeightGettable, ReturnObligationFromDateGettable}
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Settable.SettableUserAnswers

import java.time.LocalDate
import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class AvailableCreditServiceSpec extends PlaySpec with BeforeAndAfterEach {

  val mockConnector: ExportCreditBalanceConnector = mock[ExportCreditBalanceConnector]
  val sut = new AvailableCreditService(mockConnector)(global)

  val fakeRequest = AuthorizedRequest("request-ppt-id", FakeRequest(), "request-internal-id")

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConnector)
  }

  "getBalance" must {
    "return 0 and not call connector" when {
      "no credit is being claimed" in {
        val userAnswers = UserAnswers("user-answers-id")
          .setUnsafe(
            ReturnObligationFromDateGettable, LocalDate.of(1996, 3, 27)
          )

        await(sut.getBalance(userAnswers)(fakeRequest)) mustBe BigDecimal(0)

        verifyNoMoreInteractions(mockConnector)
      }
    }

    "correctly construct the parameters for the connector" in {
      val expected = BigDecimal(200)
      val unUsedBigDec = mock[BigDecimal]
      val creditResponse = ExportCreditBalanceDisplayResponse("date", unUsedBigDec, unUsedBigDec, totalExportCreditAvailable = expected)

      when(mockConnector.getBalance(any(), any(), any(), any())(any())).thenReturn(Future.successful(Right(creditResponse)))

      val userAnswers = UserAnswers("user-answers-id")
        .setUnsafe(
          ReturnObligationFromDateGettable, LocalDate.of(1996, 3, 27)
        ).setUnsafe(ConvertedCreditWeightGettable, 5L)

     await(sut.getBalance(userAnswers)(fakeRequest)) mustBe expected

      verify(mockConnector).getBalance(
        refEq("request-ppt-id"),
        refEq(LocalDate.of(1994, 3, 27)), //fromDate minus 2 years
        refEq(LocalDate.of(1996, 3, 26)), //fromDate minus 1 day
        refEq("request-internal-id")
      )(any())

      verifyNoInteractions(unUsedBigDec)
    }

    "throw an exception" when {
      "the useranswers does not contain an obligation" in {
        val userAnswers = UserAnswers("user-answers-id")

        val error = intercept[IllegalStateException](await(sut.getBalance(userAnswers)(fakeRequest)))

        error.getMessage mustBe "Obligation fromDate not found in user-answers"
      }
      "the connector call fails" in {
        when(mockConnector.getBalance(any(), any(), any(), any())(any())).thenReturn(Future.successful(Left(IM_A_TEAPOT)))


        val userAnswers = UserAnswers("user-answers-id")
          .setUnsafe(ReturnObligationFromDateGettable, LocalDate.now())
          .setUnsafe(ConvertedCreditWeightGettable, 5L)

        val error = intercept[Exception](await(sut.getBalance(userAnswers)(fakeRequest)))

        error.getMessage mustBe "Error calling EIS export credit, status: 418"
      }
    }
  }

}
