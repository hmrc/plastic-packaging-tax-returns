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

import org.mockito.Mockito.{calls, clearAllCaches, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.builders.TaxReturnBuilder
import uk.gov.hmrc.plasticpackagingtaxreturns.models.{ExportedPlasticWeight, HumanMedicinesPlasticWeight, ImportedPlasticWeight, ManufacturedPlasticWeight, RecycledPlasticWeight, TaxReturn}

class PPTReturnsCalculatorServiceSpec
    extends PlaySpec with MockitoSugar with TaxReturnBuilder {

  val mockAppConfig: AppConfig = mock[AppConfig]
  val calculator: PPTReturnsCalculatorService = new PPTReturnsCalculatorService(mockAppConfig)
  val allZeroReturn = aTaxReturn()

  "calculate" must {

    when(mockAppConfig.taxRatePoundsPerKg).thenReturn(BigDecimal(0.5))

    "add up liabilities" when {

      "all zero" in {

        val expected = 0L
        calculator.calculate(allZeroReturn).packagingTotal mustBe expected

      }

      "has a manufactured amount" in {

        val taxReturn = allZeroReturn.copy(manufacturedPlasticWeight = ManufacturedPlasticWeight(5))
        val expected = 5L

        calculator.calculate(taxReturn).packagingTotal mustBe expected

      }

      "has a imported amount" in {

        val taxReturn = allZeroReturn.copy(importedPlasticWeight = ImportedPlasticWeight(8))
        val expected = 8L

        calculator.calculate(taxReturn).packagingTotal mustBe expected

      }

      "has a manufactured and imported amount" in {

        val taxReturn = allZeroReturn.copy(manufacturedPlasticWeight = ManufacturedPlasticWeight(3), importedPlasticWeight = ImportedPlasticWeight(5))
        val expected = BigDecimal(8)

        calculator.calculate(taxReturn).packagingTotal mustBe expected

      }

    }

    "must add up deductions" when {

      "all zero" in {

        val expected = 0L

        calculator.calculate(allZeroReturn).deductionsTotal mustBe expected

      }

      "has an exported amount" in {

        val taxReturn = allZeroReturn.copy(exportedPlasticWeight = ExportedPlasticWeight(10))
        val expected = 10L

        calculator.calculate(taxReturn).deductionsTotal mustBe expected

      }

      "has a human medicines amount" in {

        val taxReturn = allZeroReturn.copy(humanMedicinesPlasticWeight = HumanMedicinesPlasticWeight(10))
        val expected = 10L

        calculator.calculate(taxReturn).deductionsTotal mustBe expected

      }

      "has a recycled plastic amount" in {

        val taxReturn = allZeroReturn.copy(recycledPlasticWeight = RecycledPlasticWeight(6))
        val expected = 6L

        calculator.calculate(taxReturn).deductionsTotal mustBe expected

      }

      "has all deductions" in {

        val taxReturn = allZeroReturn.copy(recycledPlasticWeight = RecycledPlasticWeight(3),
          humanMedicinesPlasticWeight = HumanMedicinesPlasticWeight(5),
          exportedPlasticWeight = ExportedPlasticWeight(11))

        val expected = 19L

        calculator.calculate(taxReturn).deductionsTotal mustBe expected

      }

    }

    "sum up chargeable total" when {

      "all zero (nil return)" in {

        val expected = 0L

        calculator.calculate(allZeroReturn).chargeableTotal mustBe expected

      }

      "when has a non zero packaging total" in {

        val taxReturn = allZeroReturn.copy(manufacturedPlasticWeight = ManufacturedPlasticWeight(3))
        val expected = 3L

        calculator.calculate(taxReturn).chargeableTotal mustBe expected

      }

      "has non zero packaging total and non zero deductions" in {

        val taxReturn = allZeroReturn.copy(importedPlasticWeight = ImportedPlasticWeight(8),
          manufacturedPlasticWeight = ManufacturedPlasticWeight(3),
          exportedPlasticWeight = ExportedPlasticWeight(2),
          humanMedicinesPlasticWeight = HumanMedicinesPlasticWeight(1),
          recycledPlasticWeight = RecycledPlasticWeight(2))

        val expected = 6L

        calculator.calculate(taxReturn).chargeableTotal mustBe expected

      }

    }

    "sum up the tax due amount" when {

      "All zero (nil return)" in {

        val expected = BigDecimal(0)

        calculator.calculate(allZeroReturn).taxDue mustBe expected

      }

      "when has a non zero packaging total" in {

        val taxReturn = allZeroReturn.copy(manufacturedPlasticWeight = ManufacturedPlasticWeight(3))
        val expected = BigDecimal(1.5)

        calculator.calculate(taxReturn).taxDue mustBe expected

      }

      "has non zero packaging total and non zero deductions" in {

        val taxReturn = allZeroReturn.copy(importedPlasticWeight = ImportedPlasticWeight(8),
          manufacturedPlasticWeight = ManufacturedPlasticWeight(3),
          exportedPlasticWeight = ExportedPlasticWeight(2),
          humanMedicinesPlasticWeight = HumanMedicinesPlasticWeight(1),
          recycledPlasticWeight = RecycledPlasticWeight(2))

        val expected = BigDecimal(3) // 50% (appConfig set) of (8 + 3 + 2 + 1 + 2) = 3

        calculator.calculate(taxReturn).taxDue mustBe expected

      }

      "the amount calculated has decimal places" in {
        val taxReturn = allZeroReturn.copy(
          importedPlasticWeight = ImportedPlasticWeight(7)
        )

        val expected = BigDecimal(3.5) // 50% (appConfig set) of 7 = 3.3

        calculator.calculate(taxReturn).taxDue mustBe expected
      }

      "the amount calculated has more than 2 decimal places (round up)" in {
        when(mockAppConfig.taxRatePoundsPerKg).thenReturn(BigDecimal(0.005))

        val taxReturn = allZeroReturn.copy(
          importedPlasticWeight = ImportedPlasticWeight(1)
        )

        val expected = BigDecimal(0.01) // 0.005 rounds to 0.01 2dp

        calculator.calculate(taxReturn).taxDue mustBe expected
      }

      "the amount calculated has more than 2 decimal places (round down)" in {
        when(mockAppConfig.taxRatePoundsPerKg).thenReturn(BigDecimal(0.004))

        val taxReturn = allZeroReturn.copy(
          importedPlasticWeight = ImportedPlasticWeight(1)
        )

        val expected = BigDecimal(0) // 0.004 rounds to 0.00 2dp

        calculator.calculate(taxReturn).taxDue mustBe expected
      }

    }

    "evaluate if the return is good to submit" when {
      "all zero" in {

        calculator.calculate(allZeroReturn).isSubmittable mustBe true
      }

      "the values balance, nil return" in {
        val taxReturn = allZeroReturn.copy(
          manufacturedPlasticWeight = ManufacturedPlasticWeight(1),
          exportedPlasticWeight = ExportedPlasticWeight(1)
        )

        calculator.calculate(taxReturn).isSubmittable mustBe true
      }

      "normal return non zero values" in {
        val taxReturn = allZeroReturn.copy(importedPlasticWeight = ImportedPlasticWeight(8),
          manufacturedPlasticWeight = ManufacturedPlasticWeight(3),
          exportedPlasticWeight = ExportedPlasticWeight(2),
          humanMedicinesPlasticWeight = HumanMedicinesPlasticWeight(1),
          recycledPlasticWeight = RecycledPlasticWeight(2))

        calculator.calculate(taxReturn).isSubmittable mustBe true
      }

      "the deductions are greater than the accretions" in {
        val taxReturn = allZeroReturn.copy(
          recycledPlasticWeight = RecycledPlasticWeight(2))

        calculator.calculate(taxReturn).isSubmittable mustBe false
      }
      "any value is less than 0" in {
        Seq[TaxReturn => TaxReturn](
          _.copy(manufacturedPlasticWeight = ManufacturedPlasticWeight(-1)),
          _.copy(importedPlasticWeight = ImportedPlasticWeight(-1)),
          _.copy(exportedPlasticWeight = ExportedPlasticWeight(-1)),
          _.copy(recycledPlasticWeight = RecycledPlasticWeight(-1)),
          _.copy(humanMedicinesPlasticWeight = HumanMedicinesPlasticWeight(-1)),
        ).map(_(allZeroReturn)).foreach{taxReturn =>

          calculator.calculate(taxReturn).isSubmittable mustBe false
        }
      }
    }
  }

}
