/*
 * Copyright 2026 HM Revenue & Customs
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

import org.mockito.ArgumentMatchersSugar.any
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.plasticpackagingtaxreturns.models.TaxablePlastic
import uk.gov.hmrc.plasticpackagingtaxreturns.util.TaxRateTable

import java.time.LocalDate

class TaxCalculationServiceSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach {

  private val taxRateTable = mock[TaxRateTable]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(taxRateTable)
    when(taxRateTable.lookupRateFor(any)) thenReturn 1.0
  }

  private val sut = new TaxCalculationService(taxRateTable)

  private val aDate: LocalDate = LocalDate.of(2022, 7, 14)

  "weightToDebit" must {

    behave like aConverter { (x: LocalDate, y: Long) =>
      sut.weightToDebit(x, y).moneyInPounds
    }

    "round DOWN for conversion rate of 3 d.p." in {
      when(taxRateTable.lookupRateFor(any)) thenReturn 0.336
      sut.weightToDebit(aDate, 1L) mustBe TaxablePlastic(1, 0.33, 0.336)
    }
  }

  "weightToCredit" must {

    behave like aConverter { (x: LocalDate, y: Long) =>
      sut.weightToCredit(x, y).moneyInPounds
    }

    "round UP for conversion rate of 3 d.p." in {
      when(taxRateTable.lookupRateFor(any)).thenReturn(0.333)
      sut.weightToCredit(aDate, 1L) mustBe TaxablePlastic(1, 0.34, 0.333)
    }
  }

  private def aConverter(method: (LocalDate, Long) => BigDecimal): Unit = {

    "multiply the weight by the conversion rate 1 d.p." in {
      when(taxRateTable.lookupRateFor(any)).thenReturn(0.5)
      method(aDate, 5L) mustBe BigDecimal(2.5)
    }

    "multiply the weight by the conversion rate 2 d.p." in {
      when(taxRateTable.lookupRateFor(any)).thenReturn(0.25)
      method(aDate, 5L) mustBe BigDecimal(1.25)
    }

    "look up the correct rate" in {
      method(LocalDate.of(2025, 4, 3), 5L)
      verify(taxRateTable).lookupRateFor(LocalDate.of(2025, 4, 3))
    }

  }

}
