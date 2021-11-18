/*
 * Copyright 2021 HM Revenue & Customs
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

class EisReturnsSubmissionRequestSpec extends AnyWordSpec with TaxReturnBuilder {

  "The EIS Returns Submission Request Object" should {
    "convert a stored Tax Return as expected" in {
      val taxReturn = aTaxReturn(withManufacturedPlasticWeight(1000),
                                 withImportedPlasticWeight(2000),
                                 withHumanMedicinesPlasticWeight(3000),
                                 withDirectExportDetails(4000),
                                 withRecycledPlasticWeight(5000)
      )

      val eisReturnsSubmissionRequest = EisReturnsSubmissionRequest.fromTaxReturn(taxReturn)

      eisReturnsSubmissionRequest.returnDetails.manufacturedWeight mustBe 1000
      eisReturnsSubmissionRequest.returnDetails.importedWeight mustBe 2000
      eisReturnsSubmissionRequest.returnDetails.humanMedicines mustBe 3000
      eisReturnsSubmissionRequest.returnDetails.directExports mustBe 4000
      eisReturnsSubmissionRequest.returnDetails.recycledPlastic mustBe 5000

      // TODO: fix up the translation of these values
      eisReturnsSubmissionRequest.returnType mustBe "New"
      eisReturnsSubmissionRequest.periodKey mustBe "TODO"
      eisReturnsSubmissionRequest.submissionId mustBe None
      eisReturnsSubmissionRequest.returnDetails.totalNotLiable mustBe 0
      eisReturnsSubmissionRequest.returnDetails.totalWeight mustBe 0
      eisReturnsSubmissionRequest.returnDetails.creditForPeriod mustBe 0
      eisReturnsSubmissionRequest.returnDetails.taxDue mustBe 0
    }
  }
}
