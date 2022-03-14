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

class ReturnsSubmissionRequestSpec extends AnyWordSpec with TaxReturnBuilder {

  "The EIS Returns Submission Request Object" should {
    "convert a stored Tax Return" when {
      "positive liability" in {
        val taxReturn = aTaxReturn(withManufacturedPlasticWeight(9000),
                                   withImportedPlasticWeight(8000),
                                   withHumanMedicinesPlasticWeight(5000),
                                   withDirectExportDetails(4000),
                                   withRecycledPlasticWeight(3000)
        )

        val eisReturnsSubmissionRequest = ReturnsSubmissionRequest(taxReturn, 0.20)

        eisReturnsSubmissionRequest.returnDetails.manufacturedWeight mustBe 9000
        eisReturnsSubmissionRequest.returnDetails.importedWeight mustBe 8000
        eisReturnsSubmissionRequest.returnDetails.humanMedicines mustBe 5000
        eisReturnsSubmissionRequest.returnDetails.directExports mustBe 4000
        eisReturnsSubmissionRequest.returnDetails.recycledPlastic mustBe 3000

        eisReturnsSubmissionRequest.returnType mustBe ReturnType.NEW
        eisReturnsSubmissionRequest.periodKey mustBe defaultObligation.periodKey
        eisReturnsSubmissionRequest.submissionId mustBe None

        eisReturnsSubmissionRequest.returnDetails.totalNotLiable mustBe 5000 + 4000 + 3000
        eisReturnsSubmissionRequest.returnDetails.totalWeight mustBe (9000 + 8000) - (5000 + 4000 + 3000)
        eisReturnsSubmissionRequest.returnDetails.creditForPeriod mustBe 0
        eisReturnsSubmissionRequest.returnDetails.taxDue mustBe ((9000 + 8000) - (5000 + 4000 + 3000)) * 0.20
      }

      "negative liability" in {
        val taxReturn = aTaxReturn(withManufacturedPlasticWeight(1000),
                                   withImportedPlasticWeight(2000),
                                   withHumanMedicinesPlasticWeight(5000),
                                   withDirectExportDetails(4000),
                                   withRecycledPlasticWeight(3000)
        )

        val eisReturnsSubmissionRequest = ReturnsSubmissionRequest(taxReturn, 0.20)

        eisReturnsSubmissionRequest.returnDetails.manufacturedWeight mustBe 1000
        eisReturnsSubmissionRequest.returnDetails.importedWeight mustBe 2000
        eisReturnsSubmissionRequest.returnDetails.humanMedicines mustBe 5000
        eisReturnsSubmissionRequest.returnDetails.directExports mustBe 4000
        eisReturnsSubmissionRequest.returnDetails.recycledPlastic mustBe 3000

        eisReturnsSubmissionRequest.returnType mustBe ReturnType.NEW
        eisReturnsSubmissionRequest.periodKey mustBe defaultObligation.periodKey
        eisReturnsSubmissionRequest.submissionId mustBe None

        eisReturnsSubmissionRequest.returnDetails.totalNotLiable mustBe 5000 + 4000 + 3000
        eisReturnsSubmissionRequest.returnDetails.totalWeight mustBe 0
        eisReturnsSubmissionRequest.returnDetails.creditForPeriod mustBe 0
        eisReturnsSubmissionRequest.returnDetails.taxDue mustBe 0
      }
    }

    "throw exception when obligation is not present" in {
      val taxReturn = aTaxReturn(withManufacturedPlasticWeight(1000),
                                 withImportedPlasticWeight(2000),
                                 withHumanMedicinesPlasticWeight(3000),
                                 withDirectExportDetails(4000),
                                 withRecycledPlasticWeight(5000)
      ).copy(obligation = None)

      intercept[IllegalStateException] {
        ReturnsSubmissionRequest(taxReturn, 0.20)
      }
    }
  }
}
