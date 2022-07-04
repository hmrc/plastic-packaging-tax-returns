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
import uk.gov.hmrc.plasticpackagingtaxreturns.models.{ExportedPlasticWeight, HumanMedicinesPlasticWeight, ImportedPlasticWeight, ManufacturedPlasticWeight, RecycledPlasticWeight}

class PPTReturnsCalculatorServiceSpec
    extends PlaySpec with MockitoSugar with TaxReturnBuilder {

  val mockAppConfig: AppConfig = mock[AppConfig]
  val calculator: PPTReturnsCalculatorService = new PPTReturnsCalculatorService(mockAppConfig)
  val taxReturn = aTaxReturn()

  "calculate" must {

    when(mockAppConfig.taxRatePoundsPerKg).thenReturn(BigDecimal(0.5))

    "add up liabilities" when {

      "all zero" in {

        val expected = 0L
        calculator.calculate(taxReturn).packagingTotal mustBe expected

      }

      "has a manufactured amount" in {

        val taxReturn = aTaxReturn().copy(manufacturedPlasticWeight = ManufacturedPlasticWeight(5))
        val expected = 5L

        calculator.calculate(taxReturn).packagingTotal mustBe expected

      }

      "has a imported amount" in {

        val taxReturn = aTaxReturn().copy(importedPlasticWeight = ImportedPlasticWeight(8))
        val expected = 8L

        calculator.calculate(taxReturn).packagingTotal mustBe expected

      }

      "has a manufactured and imported amount" in {

        val taxReturn = aTaxReturn().copy(manufacturedPlasticWeight = ManufacturedPlasticWeight(3), importedPlasticWeight = ImportedPlasticWeight(5))
        val expected = BigDecimal(8)

        calculator.calculate(taxReturn).packagingTotal mustBe expected

      }

    }

    "must add up deductions" when {

      "all zero" in {

        val expected = 0L

        calculator.calculate(taxReturn).deductionsTotal mustBe expected

      }

      "has an exported amount" in {

        val taxReturn = aTaxReturn().copy(exportedPlasticWeight = ExportedPlasticWeight(10))
        val expected = 10L

        calculator.calculate(taxReturn).deductionsTotal mustBe expected

      }

      "has a human medicines amount" in {

        val taxReturn = aTaxReturn().copy(humanMedicinesPlasticWeight = HumanMedicinesPlasticWeight(10))
        val expected = 10L

        calculator.calculate(taxReturn).deductionsTotal mustBe expected

      }

      "has a recycled plastic amount" in {

        val taxReturn = aTaxReturn().copy(recycledPlasticWeight = RecycledPlasticWeight(6))
        val expected = 6L

        calculator.calculate(taxReturn).deductionsTotal mustBe expected

      }

      "has all deductions" in {

        val taxReturn = aTaxReturn().copy(recycledPlasticWeight = RecycledPlasticWeight(3),
          humanMedicinesPlasticWeight = HumanMedicinesPlasticWeight(5),
          exportedPlasticWeight = ExportedPlasticWeight(11))

        val expected = 19L

        calculator.calculate(taxReturn).deductionsTotal mustBe expected

      }

    }

    "sum up chargeable total" when {

      "all zero (nil return)" in {

        val expected = 0L

        calculator.calculate(taxReturn).chargeableTotal mustBe expected

      }

      "when has a non zero packaging total" in {

        val taxReturn = aTaxReturn().copy(manufacturedPlasticWeight = ManufacturedPlasticWeight(3))
        val expected = 3L

        calculator.calculate(taxReturn).chargeableTotal mustBe expected

      }

      "has non zero packaging total and non zero deductions" in {

        val taxReturn = aTaxReturn().copy(importedPlasticWeight = ImportedPlasticWeight(8),
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

        calculator.calculate(taxReturn).taxDue mustBe expected

      }

      "when has a non zero packaging total" in {

        val taxReturn = aTaxReturn().copy(manufacturedPlasticWeight = ManufacturedPlasticWeight(3))
        val expected = BigDecimal(1.5)

        calculator.calculate(taxReturn).taxDue mustBe expected

      }

      "has non zero packaging total and non zero deductions" in {

        val taxReturn = aTaxReturn().copy(importedPlasticWeight = ImportedPlasticWeight(8),
          manufacturedPlasticWeight = ManufacturedPlasticWeight(3),
          exportedPlasticWeight = ExportedPlasticWeight(2),
          humanMedicinesPlasticWeight = HumanMedicinesPlasticWeight(1),
          recycledPlasticWeight = RecycledPlasticWeight(2))

        val expected = BigDecimal(3)

        calculator.calculate(taxReturn).taxDue mustBe expected

      }

    }

  }

}
