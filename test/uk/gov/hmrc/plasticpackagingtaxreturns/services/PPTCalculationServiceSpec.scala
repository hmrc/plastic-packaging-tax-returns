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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.{NewReturnValues, ReturnValues}

class PPTCalculationServiceSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach {

  val mockConversion: WeightToPoundsConversionService = mock[WeightToPoundsConversionService]
  def converterReturnsInput() =
    when(mockConversion.weightToDebit(any())).thenAnswer(a => BigDecimal(a.getArgument(0).asInstanceOf[Long]))

  val calculator: PPTCalculationService = new PPTCalculationService(mockConversion)
  val allZeroReturn = NewReturnValues("", 0, 0, 0, 0, 0, 0)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConversion)
  }


  "calculate" must {

    "add up liabilities" when {

      "all zero" in {
        converterReturnsInput()
        val expected = 0L
        calculator.calculate(allZeroReturn).packagingTotal mustBe expected

        withClue("Credit should not be called for a debit calculator") {
          verify(mockConversion, never()).weightToCredit(any())
        }
      }

      "has a manufactured amount" in {
        converterReturnsInput()
        val taxReturn = allZeroReturn.copy(manufacturedPlasticWeight = 5)
        val expected = 5L

        calculator.calculate(taxReturn).packagingTotal mustBe expected
      }

      "has a imported amount" in {
        converterReturnsInput()
        val taxReturn = allZeroReturn.copy(importedPlasticWeight = 8)
        val expected = 8L

        calculator.calculate(taxReturn).packagingTotal mustBe expected
      }

      "has a manufactured and imported amount" in {
        converterReturnsInput()
        val taxReturn = allZeroReturn.copy(manufacturedPlasticWeight = 3, importedPlasticWeight = 5)
        val expected = BigDecimal(8)

        calculator.calculate(taxReturn).packagingTotal mustBe expected
      }

    }

    "must add up deductions" when {

      "all zero" in {
        converterReturnsInput()
        val expected = 0L

        calculator.calculate(allZeroReturn).deductionsTotal mustBe expected
      }

      "has an exported amount" in {
        converterReturnsInput()
        val taxReturn = allZeroReturn.copy(exportedPlasticWeight = 10)
        val expected = 10L

        calculator.calculate(taxReturn).deductionsTotal mustBe expected
      }

      "has a human medicines amount" in {
        converterReturnsInput()
        val taxReturn = allZeroReturn.copy(humanMedicinesPlasticWeight = 10)
        val expected = 10L

        calculator.calculate(taxReturn).deductionsTotal mustBe expected
      }

      "has a recycled plastic amount" in {
        converterReturnsInput()
        val taxReturn = allZeroReturn.copy(recycledPlasticWeight = 6)
        val expected = 6L

        calculator.calculate(taxReturn).deductionsTotal mustBe expected
      }

      "has all deductions" in {
        converterReturnsInput()
        val taxReturn = allZeroReturn.copy(recycledPlasticWeight = 3,
          humanMedicinesPlasticWeight = 5,
          exportedPlasticWeight = 11)

        val expected = 19L

        calculator.calculate(taxReturn).deductionsTotal mustBe expected
      }

    }

    "sum up chargeable total" when {

      "all zero (nil return)" in {
        converterReturnsInput()
        val expected = 0L

        calculator.calculate(allZeroReturn).chargeableTotal mustBe expected
      }

      "deductions greater than (manufactured + imported)" in {
        converterReturnsInput()
        val expected = 0L

        calculator.calculate(allZeroReturn.copy(exportedPlasticWeight = 10)).chargeableTotal mustBe expected
      }

      "when has a non zero packaging total" in {
        converterReturnsInput()
        val taxReturn = allZeroReturn.copy(manufacturedPlasticWeight = 3)
        val expected = 3L

        calculator.calculate(taxReturn).chargeableTotal mustBe expected
      }

      "has non zero packaging total and non zero deductions" in {
        converterReturnsInput()
        val taxReturn = allZeroReturn.copy(importedPlasticWeight = 8,
          manufacturedPlasticWeight = 3,
          exportedPlasticWeight = 2,
          humanMedicinesPlasticWeight = 1,
          recycledPlasticWeight = 2)

        val expected = 6L

        calculator.calculate(taxReturn).chargeableTotal mustBe expected
      }

    }

    "sum up the tax due amount" when {

      "All zero (nil return)" in {
        converterReturnsInput()
        val expected = BigDecimal(0)

        calculator.calculate(allZeroReturn).taxDue mustBe expected

      }

      "when has a non zero packaging total" in {
        converterReturnsInput()
        val taxReturn = allZeroReturn.copy(manufacturedPlasticWeight = 3)
        val expected = BigDecimal(3)

        calculator.calculate(taxReturn).taxDue mustBe expected

      }

      "has non zero packaging total and non zero deductions" in {
        converterReturnsInput()
        val taxReturn = allZeroReturn.copy(importedPlasticWeight = 8,
          manufacturedPlasticWeight = 3,
          exportedPlasticWeight = 2,
          humanMedicinesPlasticWeight = 1,
          recycledPlasticWeight = 2)

        val expected = BigDecimal(6) //(8 + 3 - 2 - 1 - 2) = 6

        calculator.calculate(taxReturn).taxDue mustBe expected

      }

      "the amount calculated has decimal places" in {
        when(mockConversion.weightToDebit(any())).thenReturn(BigDecimal(3.5))
        val taxReturn = allZeroReturn

        val expected = BigDecimal(3.5)

        calculator.calculate(taxReturn).taxDue mustBe expected
      }
    }

    "evaluate if the return is good to submit" when {
      "all zero" in {
        converterReturnsInput()
        calculator.calculate(allZeroReturn).isSubmittable mustBe true
      }

      "the values balance, nil return" in {
        converterReturnsInput()
        val taxReturn = allZeroReturn.copy(
          manufacturedPlasticWeight = 1,
          exportedPlasticWeight = 1
        )

        calculator.calculate(taxReturn).isSubmittable mustBe true
      }

      "normal return non zero values" in {
        converterReturnsInput()
        val taxReturn = allZeroReturn.copy(importedPlasticWeight = 8,
          manufacturedPlasticWeight = 3,
          exportedPlasticWeight = 2,
          humanMedicinesPlasticWeight = 1,
          recycledPlasticWeight = 2)

        calculator.calculate(taxReturn).isSubmittable mustBe true
      }

      "the deductions are greater than the accretions" in {
        converterReturnsInput()
        val taxReturn = allZeroReturn.copy(
          recycledPlasticWeight = 2)

        calculator.calculate(taxReturn).isSubmittable mustBe false
      }

      "any value is less than 0" in {
        converterReturnsInput()

        Seq[NewReturnValues => ReturnValues](
          _.copy(manufacturedPlasticWeight = -1),
          _.copy(importedPlasticWeight = -1),
          _.copy(exportedPlasticWeight = -1),
          _.copy(recycledPlasticWeight = -1),
          _.copy(humanMedicinesPlasticWeight = -1)
        ).map(_(allZeroReturn)).foreach{taxReturn =>

          calculator.calculate(taxReturn).isSubmittable mustBe false

        }
      }
    }
  }

}
