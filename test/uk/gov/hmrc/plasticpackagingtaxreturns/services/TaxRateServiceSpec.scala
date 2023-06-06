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

import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.plasticpackagingtaxreturns.models.TaxRateValues
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.TaxRate

import java.time.LocalDate

class TaxRateServiceSpec extends PlaySpec with BeforeAndAfterEach {

  private val taxRates: TaxRateValues = mock[TaxRateValues]
  private val sut = new TaxRateService(taxRates)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(taxRates)

    when(taxRates.taxRatesPoundsPerKg).thenReturn(
      Seq(
        TaxRate(0.5, LocalDate.of(2023, 4, 1)),
        TaxRate(0.2, LocalDate.of(2022, 4, 1))
      ))
  }

  "lookupTaxRateForPeriod" must {
    "return the correct tax rate" when {
      "before 1/4/2022" in {
        sut.lookupTaxRateForPeriod(LocalDate.of(2022, 3, 31)) mustBe 0.0
      }

      "on or after 1/4/2022" in {
        sut.lookupTaxRateForPeriod(LocalDate.of(2022, 4, 1)) mustBe 0.2
      }

      "before 1/4/2023" in {
        sut.lookupTaxRateForPeriod(LocalDate.of(2023, 3, 31)) mustBe 0.2
      }

      "on or after 1/4/2023" in {
        sut.lookupTaxRateForPeriod(LocalDate.of(2023, 4, 1)) mustBe 0.5
      }
    }

    "return a taxRates when list is not sorted" in {
      when(taxRates.taxRatesPoundsPerKg).thenReturn(
        Seq(
          TaxRate(0.5, LocalDate.of(2023, 4, 1)),
          TaxRate(0.2, LocalDate.of(2022, 4, 1)),
          TaxRate(0.6, LocalDate.of(2021, 4, 1))
        ))

      sut.lookupTaxRateForPeriod(LocalDate.of(2023, 4, 1)) mustBe 0.5
      sut.lookupTaxRateForPeriod(LocalDate.of(2022, 4, 1)) mustBe 0.2
    }
  }

}
