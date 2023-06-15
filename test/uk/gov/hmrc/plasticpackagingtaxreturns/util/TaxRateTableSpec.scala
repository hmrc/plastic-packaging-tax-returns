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

package uk.gov.hmrc.plasticpackagingtaxreturns.util

import org.scalatest.BeforeAndAfterEach
import org.scalatest.exceptions.TestFailedException
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.plasticpackagingtaxreturns.models.TaxRate

import java.time.LocalDate

class TaxRateTableSpec extends PlaySpec with BeforeAndAfterEach {

  private val taxRateTable = new TaxRateTable

  override def beforeEach(): Unit = {
    super.beforeEach()
  }

  private def inReverseChronologicalOrder(table: Seq[TaxRate]) = {
    table.sortBy(- _.useFromDate.toEpochDay)
  }

  "lookupTaxRateForPeriod" must {
    "return the correct tax rate" when {
      
      "before 1/4/2022" in {
        the [IllegalStateException] thrownBy {
          taxRateTable.lookupRateFor(LocalDate.of(2022, 3, 31))
        } must have message "No tax rate found for 2022-03-31"
      }

      "2022 to 2023" in {
        taxRateTable.lookupRateFor(LocalDate.of(2022, 4, 1)) mustBe 0.2
        taxRateTable.lookupRateFor(LocalDate.of(2023, 3, 31)) mustBe 0.2
      }

      "2023 onwards" in {
        taxRateTable.lookupRateFor(LocalDate.of(2023, 4, 1)) mustBe 0.21082
        taxRateTable.lookupRateFor(LocalDate.of(2024, 3, 31)) mustBe 0.21082
        taxRateTable.lookupRateFor(LocalDate.of(2024, 6, 11)) mustBe 0.21082
      }
    }
  }
  
  "it" must {
    
    "have a table in reverse chronological order" in {
      taxRateTable.table mustBe inReverseChronologicalOrder(taxRateTable.table)
    }
  }

  "check we can detect a sorted table" when {
    
    "table is in chronological order (don't want)" in {
      val table = Seq(
        TaxRate(poundsPerKg = 1, useFromDate = LocalDate.of(2022, 4, 1)),
        TaxRate(poundsPerKg = 2, useFromDate = LocalDate.of(2023, 4, 1))
      )
      a [TestFailedException] must be thrownBy {
        table mustBe inReverseChronologicalOrder(table)
      }
    }
    
    "table is in reverse chronological order (do want)" in {
      val table = Seq(
        TaxRate(poundsPerKg = 2, useFromDate = LocalDate.of(2023, 4, 1)),
        TaxRate(poundsPerKg = 1, useFromDate = LocalDate.of(2022, 4, 1))
      )
      table mustBe inReverseChronologicalOrder(table)
    }
    
  }


}
