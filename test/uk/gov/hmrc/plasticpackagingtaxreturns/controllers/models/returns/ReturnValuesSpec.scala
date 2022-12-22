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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.returns

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.{AmendReturnValues, NewReturnValues}
import uk.gov.hmrc.plasticpackagingtaxreturns.services.CreditsCalculationService.Credit
import uk.gov.hmrc.plasticpackagingtaxreturns.support.{AmendTestHelper, ReturnTestHelper}

class ReturnValuesSpec extends PlaySpec {

  "NewReturnValues" should {
    "extract value from userAnswer" in {

      val result = NewReturnValues.apply(Credit(10L, 5), 10)(UserAnswers("123", ReturnTestHelper.returnWithCreditsDataJson))

      result mustBe Some(NewReturnValues(
        periodKey = "21C4",
        manufacturedPlasticWeight = 100L,
        importedPlasticWeight = 0,
        exportedPlasticWeight = 200L,
        exportedByAnotherBusinessPlasticWeight = 100L,
        humanMedicinesPlasticWeight = 10L,
        recycledPlasticWeight = 5L,
        convertedPackagingCredit = 5,
        availableCredit = 10
      ))
    }

    "Return None if cannot get value from useAnswer" in {
      val result = NewReturnValues.apply(Credit(10L, 5), 10)(UserAnswers("123", ReturnTestHelper.invalidReturnsDataJson))

      result mustBe None
    }
  }

  "AmendReturnValues" should {
    "extract value from userAnswer" in {

      val result = AmendReturnValues.apply(UserAnswers("123", AmendTestHelper.userAnswersDataAmends))

      result mustBe Some(AmendReturnValues(
        periodKey = "21C4",
        manufacturedPlasticWeight = 100L,
        importedPlasticWeight = 1L,
        exportedPlasticWeight = 2L,
        humanMedicinesPlasticWeight = 3L,
        recycledPlasticWeight = 5L,
        submission = "submission12"
      ))
    }

    "return original values when amend values not found" in {
      val result = AmendReturnValues.apply(UserAnswers("123", AmendTestHelper.userAnswersDataWithoutAmends))

      result mustBe Some(AmendReturnValues(
        periodKey = "21C4",
        manufacturedPlasticWeight = 255L,
        importedPlasticWeight = 0,
        exportedPlasticWeight = 6L,
        humanMedicinesPlasticWeight = 10L,
        recycledPlasticWeight = 5L,
        submission = "submission12"
      ))
    }

    "throw when period key is missing" in {

      intercept[Exception] {
        AmendReturnValues.apply(UserAnswers("123", AmendTestHelper.userAnswersDataWithoutKey))
      }
    }

    "return empty when original and amend value not found" in {

      AmendReturnValues.apply(UserAnswers("123")) mustBe None
    }
  }
}
