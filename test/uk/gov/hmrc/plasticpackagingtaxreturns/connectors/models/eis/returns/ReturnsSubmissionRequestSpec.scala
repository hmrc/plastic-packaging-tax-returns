/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.plasticpackagingtaxreturns.models.ReturnType
import uk.gov.hmrc.plasticpackagingtaxreturns.models.calculations.Calculations
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.NewReturnValues

import java.time.LocalDate

class ReturnsSubmissionRequestSpec extends PlaySpec {

  "The EIS Returns Submission Request Object" should {

    "convert a stored Tax Return" when {

      "positive liability" in {

        val returnValues: NewReturnValues = NewReturnValues(
          periodKey = "somekey",
          periodEndDate = LocalDate.now,
          manufacturedPlasticWeight = 1,
          importedPlasticWeight = 2,
          humanMedicinesPlasticWeight = 3,
          exportedPlasticWeight = 4,
          exportedByAnotherBusinessPlasticWeight = 0,
          recycledPlasticWeight = 5,
          convertedPackagingCredit = 11,
          availableCredit = 12
        )

        val calc = Calculations(
          taxDue = 6,
          chargeableTotal = 7,
          deductionsTotal = 8,
          packagingTotal = 9,
          isSubmittable = true,
          taxRate = 0.123
        )

        val eisReturnsSubmissionRequest = ReturnsSubmissionRequest(returnValues, calc)

        eisReturnsSubmissionRequest.returnDetails.manufacturedWeight mustBe 1
        eisReturnsSubmissionRequest.returnDetails.importedWeight mustBe 2
        eisReturnsSubmissionRequest.returnDetails.humanMedicines mustBe 3
        eisReturnsSubmissionRequest.returnDetails.directExports mustBe 4
        eisReturnsSubmissionRequest.returnDetails.recycledPlastic mustBe 5

        eisReturnsSubmissionRequest.returnType mustBe ReturnType.NEW
        eisReturnsSubmissionRequest.periodKey mustBe "somekey"
        eisReturnsSubmissionRequest.submissionId mustBe None

        eisReturnsSubmissionRequest.returnDetails.taxDue mustBe 6
        eisReturnsSubmissionRequest.returnDetails.totalWeight mustBe 7
        eisReturnsSubmissionRequest.returnDetails.totalNotLiable mustBe 8

        eisReturnsSubmissionRequest.returnDetails.creditForPeriod mustBe 11
      }

    }
  }
}
