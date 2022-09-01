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

package uk.gov.hmrc.plasticpackagingtaxreturns.services

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.returns.{ConvertedCreditWeightGettable, ExportedCreditWeightGettable}
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Settable.SettableUserAnswers

class CreditsCalculationServiceSpec extends PlaySpec with BeforeAndAfterEach{

  val mockConversion: WeightToPoundsConversionService = mock[WeightToPoundsConversionService]
  val captor: ArgumentCaptor[Long] = ArgumentCaptor.forClass(classOf[Long])

  val sut = new CreditsCalculationService(mockConversion)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConversion)
  }

  def converterReturnsInput() =
    when(mockConversion.weightToCredit(any())).thenAnswer(a => BigDecimal(a.getArgument(0).asInstanceOf[Long]))

  "totalRequestCreditInPounds" must {
    "return 0" when {
      "both fields are empty" in {
        converterReturnsInput()

        val userAnswers = UserAnswers("user-answers-id")

        sut.totalRequestCreditInPounds(userAnswers) mustBe 0
      }
    }

    "total the weight" when {
      "converted is supplied" in {
        converterReturnsInput()
        val userAnswers = UserAnswers("user-answers-id")
          .setUnsafe(ConvertedCreditWeightGettable, 5L)

        sut.totalRequestCreditInPounds(userAnswers) mustBe 5L
      }
      "exported is supplied" in {
        converterReturnsInput()
        val userAnswers = UserAnswers("user-answers-id")
          .setUnsafe(ExportedCreditWeightGettable, 7L)

        sut.totalRequestCreditInPounds(userAnswers) mustBe 7L
      }
      "both are supplied" in {
        converterReturnsInput()
        val userAnswers = UserAnswers("user-answers-id")
          .setUnsafe(ConvertedCreditWeightGettable, 5L)
          .setUnsafe(ExportedCreditWeightGettable, 7L)

        sut.totalRequestCreditInPounds(userAnswers) mustBe 12L
      }
    }

    "convert the weight in to pounds(£) and return it unchanged" in {
      val expectedPounds = 42.69
      when(mockConversion.weightToCredit(any())).thenReturn(expectedPounds)
      val userAnswers = UserAnswers("user-answers-id")
        .setUnsafe(ConvertedCreditWeightGettable, 5L)
        .setUnsafe(ExportedCreditWeightGettable, 7L)

      sut.totalRequestCreditInPounds(userAnswers) mustBe expectedPounds

      verify(mockConversion).weightToCredit(12L)
      verify(mockConversion, never()).weightToDebit(any())
    }
  }

}
