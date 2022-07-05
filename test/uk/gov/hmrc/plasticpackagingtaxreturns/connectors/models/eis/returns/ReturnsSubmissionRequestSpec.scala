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

  "The EIS Returns Submission Request Object" should {

    "convert a stored Tax Return" when {

      "positive liability" in {

        val taxReturn = aTaxReturn(
          withManufacturedPlasticWeight(1),
          withImportedPlasticWeight(2),
          withHumanMedicinesPlasticWeight(3),
          withDirectExportDetails(4),
          withRecycledPlasticWeight(5),
          withConvertedPlasticPackagingCredit(0)
        )

        val calc = Calculations(taxDue = 6, chargeableTotal = 7, deductionsTotal = 8, packagingTotal = 9, isSubmittable = true)

        val eisReturnsSubmissionRequest = ReturnsSubmissionRequest(taxReturn, calc, None)

        eisReturnsSubmissionRequest.returnDetails.creditForPeriod mustBe 0
        eisReturnsSubmissionRequest.returnDetails.manufacturedWeight mustBe 1
        eisReturnsSubmissionRequest.returnDetails.importedWeight mustBe 2
        eisReturnsSubmissionRequest.returnDetails.humanMedicines mustBe 3
        eisReturnsSubmissionRequest.returnDetails.directExports mustBe 4
        eisReturnsSubmissionRequest.returnDetails.recycledPlastic mustBe 5

        eisReturnsSubmissionRequest.returnType mustBe ReturnType.NEW
        eisReturnsSubmissionRequest.periodKey mustBe taxReturn.periodKey
        eisReturnsSubmissionRequest.submissionId mustBe None

        eisReturnsSubmissionRequest.returnDetails.taxDue mustBe 6
        eisReturnsSubmissionRequest.returnDetails.totalWeight mustBe 7
        eisReturnsSubmissionRequest.returnDetails.totalNotLiable mustBe 8
      }

    }
  }
}
