/*
 * Copyright 2023 HM Revenue & Customs
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

import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.Mockito.{never, reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.JsPath
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.{NewReturnValues, ReturnValues}

import java.time.LocalDate

class PPTCalculationServiceSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach {

  private val mockConversionService = mock[WeightToPoundsConversionService]
  private val mockCalculationService = mock[CreditsCalculationService]

  val calculationService: PPTCalculationService = new PPTCalculationService(mockCalculationService, mockConversionService)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConversionService, mockCalculationService)
    when(mockConversionService.weightToDebit(any, any)) thenReturn BigDecimal(0)
  }

  private val allZeroReturn = NewReturnValues("", LocalDate.now(), 0, 0, 0, 0, 0, 0, 0)


  "calculate" must {

    "add up liabilities" when {

      "all zero" in {
        val expected = 0L
        calculationService.calculate(allZeroReturn).packagingTotal mustBe expected

        withClue("Credit should not be called for a debit calculator") {
          verify(mockConversionService, never()).weightToCredit(any)
        }
      }

      "has a manufactured amount" in {
        val taxReturn = allZeroReturn.copy(manufacturedPlasticWeight = 5)
        val expected = 5L

        calculationService.calculate(taxReturn).packagingTotal mustBe expected
      }

      "has a imported amount" in {
        val taxReturn = allZeroReturn.copy(importedPlasticWeight = 8)
        val expected = 8L

        calculationService.calculate(taxReturn).packagingTotal mustBe expected
      }

      "has a manufactured and imported amount" in {
        val taxReturn = allZeroReturn.copy(manufacturedPlasticWeight = 3, importedPlasticWeight = 5)
        val expected = BigDecimal(8)

        calculationService.calculate(taxReturn).packagingTotal mustBe expected
      }

    }

    "must add up deductions" when {

      "all zero" in {
        val expected = 0L

        calculationService.calculate(allZeroReturn).deductionsTotal mustBe expected
      }

      "has an exported amount" in {
        val taxReturn = allZeroReturn.copy(exportedPlasticWeight = 10)
        val expected = 10L

        calculationService.calculate(taxReturn).deductionsTotal mustBe expected
      }

      "has a human medicines amount" in {
        val taxReturn = allZeroReturn.copy(humanMedicinesPlasticWeight = 10)
        val expected = 10L

        calculationService.calculate(taxReturn).deductionsTotal mustBe expected
      }

      "has a recycled plastic amount" in {
        val taxReturn = allZeroReturn.copy(recycledPlasticWeight = 6)
        val expected = 6L

        calculationService.calculate(taxReturn).deductionsTotal mustBe expected
      }

      "has all deductions" in {
        val taxReturn = allZeroReturn.copy(recycledPlasticWeight = 3,
          humanMedicinesPlasticWeight = 5,
          exportedPlasticWeight = 11)

        val expected = 19L

        calculationService.calculate(taxReturn).deductionsTotal mustBe expected
      }

    }

    "sum up chargeable total" when {

      "all zero (nil return)" in {
        val expected = 0L

        calculationService.calculate(allZeroReturn).chargeableTotal mustBe expected
      }

      "deductions greater than (manufactured + imported)" in {
        val expected = 0L

        calculationService.calculate(allZeroReturn.copy(exportedPlasticWeight = 10)).chargeableTotal mustBe expected
      }

      "when has a non zero packaging total" in {
        val taxReturn = allZeroReturn.copy(manufacturedPlasticWeight = 3)
        val expected = 3L

        calculationService.calculate(taxReturn).chargeableTotal mustBe expected
      }

      "has non zero packaging total and non zero deductions" in {
        val taxReturn = allZeroReturn.copy(importedPlasticWeight = 8,
          manufacturedPlasticWeight = 3,
          exportedPlasticWeight = 2,
          humanMedicinesPlasticWeight = 1,
          recycledPlasticWeight = 2)

        val expected = 6L

        calculationService.calculate(taxReturn).chargeableTotal mustBe expected
      }

    }

    "return the tax due amount" in {
      when(mockConversionService.weightToDebit(any, any)) thenReturn BigDecimal(7.17)
      calculationService.calculate(allZeroReturn).taxDue mustBe BigDecimal(7.17)
      verify(mockConversionService).weightToDebit(eqTo(LocalDate.now()), eqTo(0))
    }
    
    "sum up the taxable plastic total" when {
      
      "All zero (nil return)" in {
        calculationService.calculate(allZeroReturn)
        verify(mockConversionService).weightToDebit(any, eqTo(0))
      }

      "when has a non zero packaging total" in {
        val taxReturn = allZeroReturn.copy(manufacturedPlasticWeight = 3)
        calculationService.calculate(taxReturn)
        verify(mockConversionService).weightToDebit(any, eqTo(3))
      }

      "has non zero packaging total and non zero deductions" in {
        val taxReturn = allZeroReturn.copy(importedPlasticWeight = 8,
          manufacturedPlasticWeight = 3,
          exportedPlasticWeight = 2,
          humanMedicinesPlasticWeight = 1,
          recycledPlasticWeight = 2)
        calculationService.calculate(taxReturn)
        verify(mockConversionService).weightToDebit(any, eqTo(6)) //(8 + 3 - 2 - 1 - 2) = 6
      }

    }

    "evaluate if the return is good to submit" when {
      "all zero" in {
        calculationService.calculate(allZeroReturn).isSubmittable mustBe true
      }

      "the values balance, nil return" in {
        val taxReturn = allZeroReturn.copy(
          manufacturedPlasticWeight = 1,
          exportedPlasticWeight = 1
        )

        calculationService.calculate(taxReturn).isSubmittable mustBe true
      }

      "normal return non zero values" in {
        val taxReturn = allZeroReturn.copy(importedPlasticWeight = 8,
          manufacturedPlasticWeight = 3,
          exportedPlasticWeight = 2,
          humanMedicinesPlasticWeight = 1,
          recycledPlasticWeight = 2)

        calculationService.calculate(taxReturn).isSubmittable mustBe true
      }

      "the deductions are greater than the accretions" in {
        val taxReturn = allZeroReturn.copy(
          recycledPlasticWeight = 2)

        calculationService.calculate(taxReturn).isSubmittable mustBe false
      }

      "any value is less than 0" in {

        Seq[NewReturnValues => ReturnValues](
          _.copy(manufacturedPlasticWeight = -1),
          _.copy(importedPlasticWeight = -1),
          _.copy(exportedPlasticWeight = -1),
          _.copy(recycledPlasticWeight = -1),
          _.copy(humanMedicinesPlasticWeight = -1)
        ).map(_(allZeroReturn)).foreach{taxReturn =>

          calculationService.calculate(taxReturn).isSubmittable mustBe false

        }
      }
    }
  }

}
