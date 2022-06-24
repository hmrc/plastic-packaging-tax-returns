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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns

import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.builders.TaxReturnBuilder
import uk.gov.hmrc.plasticpackagingtaxreturns.models.ReturnType

import scala.math.BigDecimal.RoundingMode

class ReturnsSubmissionRequestSpec extends AnyWordSpec with TaxReturnBuilder {

  private val taxRate = BigDecimal("0.211") // Use a strange rate to test rounding

  "The EIS Returns Submission Request Object" should {
    "convert a stored Tax Return" when {
      "positive liability" in {
        val taxReturn = aTaxReturn(
          withManufacturedPlasticWeight(9001),
          withImportedPlasticWeight(8000),
          withHumanMedicinesPlasticWeight(5000),
          withDirectExportDetails(4000),
          withRecycledPlasticWeight(3000)
        )

        val eisReturnsSubmissionRequest = ReturnsSubmissionRequest(taxReturn, taxRate, None)

        eisReturnsSubmissionRequest.returnDetails.manufacturedWeight mustBe 9001
        eisReturnsSubmissionRequest.returnDetails.importedWeight mustBe 8000
        eisReturnsSubmissionRequest.returnDetails.humanMedicines mustBe 5000
        eisReturnsSubmissionRequest.returnDetails.directExports mustBe 4000
        eisReturnsSubmissionRequest.returnDetails.recycledPlastic mustBe 3000

        eisReturnsSubmissionRequest.returnType mustBe ReturnType.NEW
        eisReturnsSubmissionRequest.periodKey mustBe taxReturn.periodKey
        eisReturnsSubmissionRequest.submissionId mustBe None

        eisReturnsSubmissionRequest.returnDetails.totalNotLiable mustBe 5000 + 4000 + 3000
        eisReturnsSubmissionRequest.returnDetails.totalWeight mustBe (9001 + 8000) - (5000 + 4000 + 3000)
        eisReturnsSubmissionRequest.returnDetails.creditForPeriod mustBe 0
        eisReturnsSubmissionRequest.returnDetails.taxDue mustBe (((9001 + 8000) - (5000 + 4000 + 3000)) * taxRate).setScale(2, RoundingMode.HALF_EVEN)
      }

      "negative liability" in {

        val thrown = intercept[IllegalArgumentException] {
          val taxReturn = aTaxReturn(
            withManufacturedPlasticWeight(1000),
            withImportedPlasticWeight(2000),
            withHumanMedicinesPlasticWeight(5000),
            withDirectExportDetails(4000),
            withRecycledPlasticWeight(3000)
          )
        }
        assert(thrown.getMessage === "requirement failed: Deductions were greater than total packaging")
      }

    }
  }
}
