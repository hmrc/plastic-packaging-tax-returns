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

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchersSugar.eqTo
import org.mockito.Mockito.{never, reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.JsPath
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.services.CreditsCalculationService.Credit
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Settable.SettableUserAnswers

class CreditsCalculationServiceSpec extends PlaySpec with BeforeAndAfterEach {

  val mockConversion: WeightToPoundsConversionService = mock[WeightToPoundsConversionService]
  val captor: ArgumentCaptor[Long] = ArgumentCaptor.forClass(classOf[Long])

  val sut = new CreditsCalculationService(mockConversion)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConversion)
  }

  private def converterJustAddsOne() =
    when(mockConversion.weightToCredit(any(), any())).thenAnswer(a => BigDecimal(a.getArgument(1).asInstanceOf[Long] + 1))

  "totalRequestCredit" must {
    "return 0" when {
      "both fields are empty" in { // TODO should complain about missing user-answers?
        converterJustAddsOne()

        val userAnswers = UserAnswers("user-answers-id")

        sut.totalRequestedCredit(userAnswers) mustBe Credit(0, 1)
      }
    }

    "total the weight" when {
      // TODO what do we want if only part answers are there, eg just the yes-no, not the weight?
      "converted is supplied" in {
        converterJustAddsOne()
        val userAnswers = UserAnswers("user-answers-id")
          .setUnsafe(JsPath \ "convertedCredits", CreditsAnswer(true, Some(5L)))

        sut.totalRequestedCredit(userAnswers) mustBe Credit(5, 6)
      }
      "exported is supplied" in {
        converterJustAddsOne()
        val userAnswers = UserAnswers("user-answers-id")
          .setUnsafe(JsPath \ "exportedCredits", CreditsAnswer(true, Some(7L)))

        sut.totalRequestedCredit(userAnswers) mustBe Credit(7, 8)
      }
      "both are supplied" in {
        converterJustAddsOne()
        val userAnswers = UserAnswers("user-answers-id")
          .setUnsafe(JsPath \ "convertedCredits", CreditsAnswer(true, Some(5L)))
          .setUnsafe(JsPath \ "exportedCredits", CreditsAnswer(true, Some(7L)))

        sut.totalRequestedCredit(userAnswers) mustBe Credit(12, 13)
      }
    }

    "convert the weight in to pounds(£) and return it unchanged" in {
      val expectedPounds = 42.69
      when(mockConversion.weightToCredit(any(), any())).thenReturn(expectedPounds)
      val userAnswers = UserAnswers("user-answers-id")
        .setUnsafe(JsPath \ "convertedCredits", CreditsAnswer(true, Some(5L)))
        .setUnsafe(JsPath \ "exportedCredits", CreditsAnswer(true, Some(7L)))

      sut.totalRequestedCredit(userAnswers) mustBe Credit(12L, expectedPounds)

      verify(mockConversion).weightToCredit(any(), eqTo(12L)) // TODO date percolator
      verify(mockConversion, never()).weightToDebit(any(), any())
    }
  }

}
