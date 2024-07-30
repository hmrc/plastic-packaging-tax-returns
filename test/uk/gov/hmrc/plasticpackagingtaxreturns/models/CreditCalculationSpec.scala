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

package uk.gov.hmrc.plasticpackagingtaxreturns.models

import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec

class CreditCalculationSpec extends PlaySpec with BeforeAndAfterEach with MockitoSugar {

  "Total stuff" in {
    val credit =
      Map(
        "a" -> TaxablePlastic(weight = 1, moneyInPounds = 2, taxRate = 3),
        "b" -> TaxablePlastic(weight = 10, moneyInPounds = 20, taxRate = 30)
      )
    CreditCalculation.totalUp(credit, availableCreditInPounds = 30) mustBe CreditCalculation(
      availableCreditInPounds = 30,
      totalRequestedCreditInPounds = 22,
      totalRequestedCreditInKilograms = 11,
      canBeClaimed = true,
      credit = credit
    )
  }

  "say when a credit claim is too high" when {
    "claim is in one year" in {
      val credit = Map("a" -> TaxablePlastic(0, moneyInPounds = 2, 0))
      CreditCalculation.totalUp(credit, availableCreditInPounds = 1).canBeClaimed mustBe false
      CreditCalculation.totalUp(credit, availableCreditInPounds = 2).canBeClaimed mustBe true
    }
    "claim is spread across years" in {
      val credit = Map("a" -> TaxablePlastic(0, moneyInPounds = 1, 0), "b" -> TaxablePlastic(0, moneyInPounds = 1, 0))
      CreditCalculation.totalUp(credit, availableCreditInPounds = 1).canBeClaimed mustBe false
      CreditCalculation.totalUp(credit, availableCreditInPounds = 2).canBeClaimed mustBe true
    }

    "retain the available credit amount" in {
      val credit = Map("a" -> TaxablePlastic.zero)
      CreditCalculation.totalUp(credit, availableCreditInPounds = 1).availableCreditInPounds mustBe 1
      CreditCalculation.totalUp(credit, availableCreditInPounds = 12).availableCreditInPounds mustBe 12
    }
  }
}
