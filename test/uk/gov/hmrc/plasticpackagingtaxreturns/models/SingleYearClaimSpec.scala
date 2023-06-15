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

import org.mockito.ArgumentMatchersSugar.any
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.plasticpackagingtaxreturns.services.TaxCalculationService

import java.time.LocalDate

class SingleYearClaimSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach {

  private val taxCalculationService = mock[TaxCalculationService]
  private val taxablePlastic = TaxablePlastic(1, 2, 3)

  private val exampleClaim = SingleYearClaim(
    LocalDate.of(2024, 3, 31),
    Some(CreditsAnswer(true, Some(1L))),
    Some(CreditsAnswer(true, Some(2L))),
  )
  

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    when(taxCalculationService.weightToCredit(any, any)) thenReturn taxablePlastic
  }

  "it" should {

    "calculate the total weight in the claim" in {
      exampleClaim.calculate(taxCalculationService) mustBe taxablePlastic
      verify(taxCalculationService).weightToCredit(LocalDate.of(2024, 3, 31), 3L)
    }

  }

}
