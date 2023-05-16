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

import org.mockito.MockitoSugar
import org.mockito.integrations.scalatest.ResetMocksAfterEachTest
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.CreditRangeOption
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EdgeOfSystem

import java.time.LocalDate

class AvailableCreditDateRangeServiceSpec extends PlaySpec 
  with BeforeAndAfterEach with MockitoSugar with ResetMocksAfterEachTest {
  
  private val edgeOfSystem = mock[EdgeOfSystem]
  private val service = new AvailableCreditDateRangesService(edgeOfSystem)

  "calculate" should {

    "return 1 year" in {
      val returnEndDate = LocalDate.of(2023, 3, 31)
      service.calculate(returnEndDate) mustBe Seq(
        CreditRangeOption(LocalDate.of(2022, 4, 1), LocalDate.of(2023, 3, 31)),
      )
    }

    "return 2 years" in {
      val returnEndDate = LocalDate.of(2024, 3, 31)
      service.calculate(returnEndDate) mustBe Seq(
        CreditRangeOption(LocalDate.of(2022, 4, 1), LocalDate.of(2023, 3, 31)),
        CreditRangeOption(LocalDate.of(2023, 4, 1), LocalDate.of(2024, 3, 31)),
      )
    }
    
    // TODO 
    // - go back 8 quarters from quarter of obligation being filed
    // - start from account's tax start date
    // - display partial tax years, rather than always April XXXX March YYYY?
  }
  
  "taxYears" should {
    "handle first year" in {
      service.taxYears(LocalDate.of(2022, 4, 1)) mustBe Seq(2022)
      service.taxYears(LocalDate.of(2022, 5, 1)) mustBe Seq(2022)
      service.taxYears(LocalDate.of(2022, 12, 31)) mustBe Seq(2022)
      service.taxYears(LocalDate.of(2023, 1, 1)) mustBe Seq(2022)
      service.taxYears(LocalDate.of(2023, 3, 31)) mustBe Seq(2022)
    }
    
    "handle second year" in {
      service.taxYears(LocalDate.of(2023, 4, 1)) mustBe Seq(2022, 2023)
      service.taxYears(LocalDate.of(2023, 12, 31)) mustBe Seq(2022, 2023)
      service.taxYears(LocalDate.of(2024, 1, 1)) mustBe Seq(2022, 2023)
      service.taxYears(LocalDate.of(2024, 3, 31)) mustBe Seq(2022, 2023)
    }

    "handle third year" in {
      // TODO some of these won't include 2022
      service.taxYears(LocalDate.of(2024, 4, 1)) mustBe Seq(2022, 2023, 2024)
      service.taxYears(LocalDate.of(2024, 12, 31)) mustBe Seq(2022, 2023, 2024)
      service.taxYears(LocalDate.of(2025, 1, 1)) mustBe Seq(2022, 2023, 2024)
      service.taxYears(LocalDate.of(2025, 3, 31)) mustBe Seq(2022, 2023, 2024)
    }

    "handle fourth year" ignore {
      // TODO go back max 8 quarters previous to obligation quarter 
    }
  }
    
}
