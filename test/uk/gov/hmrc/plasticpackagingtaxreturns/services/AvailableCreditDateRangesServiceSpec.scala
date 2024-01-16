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
import org.mockito.scalatest.ResetMocksAfterEachTest
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.CreditRangeOption
import uk.gov.hmrc.plasticpackagingtaxreturns.services.AvailableCreditDateRangesService.TaxQuarter
import uk.gov.hmrc.plasticpackagingtaxreturns.services.AvailableCreditDateRangesService.TaxQuarter._
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EdgeOfSystem

import java.time.LocalDate

class AvailableCreditDateRangesServiceSpec extends PlaySpec with BeforeAndAfterEach with MockitoSugar with ResetMocksAfterEachTest {

  private val edgeOfSystem = mock[EdgeOfSystem]
  private val service      = new AvailableCreditDateRangesService(edgeOfSystem)

  "calculate" should {

    "return 0 years when start date cuts them all off" in {
      val returnEndDate = LocalDate.of(2022, 6, 30)

      service.calculate(returnEndDate) mustBe Seq.empty
    }

    "return 1 year" in {
      val returnEndDate = LocalDate.of(2022, 9, 30)

      service.calculate(returnEndDate) mustBe Seq(CreditRangeOption(LocalDate.of(2022, 4, 1), LocalDate.of(2022, 6, 30)))
    }

    "return 2 years" when {
      "into second year cut off by start date" in {
        val returnEndDate = LocalDate.of(2023, 9, 30)

        service.calculate(returnEndDate) mustBe Seq(
          CreditRangeOption(LocalDate.of(2023, 4, 1), LocalDate.of(2023, 6, 30)),
          CreditRangeOption(LocalDate.of(2022, 4, 1), LocalDate.of(2023, 3, 31))
        )
      }

      "all of 2 years is available" in {
        val returnEndDate = LocalDate.of(2025, 6, 30)

        service.calculate(returnEndDate) mustBe Seq(
          CreditRangeOption(LocalDate.of(2024, 4, 1), LocalDate.of(2025, 3, 31)),
          CreditRangeOption(LocalDate.of(2023, 4, 1), LocalDate.of(2024, 3, 31))
        )
      }
    }

    "return 3 years" in {
      val returnEndDate = LocalDate.of(2024, 9, 30)

      service.calculate(returnEndDate) mustBe Seq(
        CreditRangeOption(LocalDate.of(2024, 4, 1), LocalDate.of(2024, 6, 30)),
        CreditRangeOption(LocalDate.of(2023, 4, 1), LocalDate.of(2024, 3, 31)),
        CreditRangeOption(LocalDate.of(2022, 7, 1), LocalDate.of(2023, 3, 31))
      )
    }

    "include the subscriptions tax start date" when {
      "tax start date affects available dates" in {
        service.calculate(returnEndDate = LocalDate.of(2023, 6, 30), taxStartDate = LocalDate.of(2022, 5, 6)) mustBe Seq(
          CreditRangeOption(LocalDate.of(2022, 5, 6), LocalDate.of(2023, 3, 31))
        )
      }

      "tax start date is before available dates" in {
        service.calculate(returnEndDate = LocalDate.of(2025, 6, 30), taxStartDate = LocalDate.of(2022, 4, 1)) mustBe Seq(
          CreditRangeOption(LocalDate.of(2024, 4, 1), LocalDate.of(2025, 3, 31)),
          CreditRangeOption(LocalDate.of(2023, 4, 1), LocalDate.of(2024, 3, 31))
        )
      }
    }
  }

  "TaxQuarter" when {
    "previous quarter" when {
      "Q1 give Q4" in {
        Q1(2024).previousQuarter mustBe Q4(2023)
      }
      "Q2 give Q1" in {
        Q2(2024).previousQuarter mustBe Q1(2024)
      }
      "Q3 give Q2" in {
        Q3(2024).previousQuarter mustBe Q2(2024)
      }
      "Q4 give Q3" in {
        Q4(2024).previousQuarter mustBe Q3(2024)
      }
    }

    "previousEightQuarters" must {
      "return 8 quarters" in {
        val quarters = Q1(2025).previousEightQuarters

        quarters.size mustBe 8
        quarters must contain(Q4(2024))
        quarters must contain(Q3(2024))
        quarters must contain(Q2(2024))
        quarters must contain(Q1(2024))
        quarters must contain(Q4(2023))
        quarters must contain(Q3(2023))
        quarters must contain(Q2(2023))
        quarters must contain(Q1(2023))
      }
    }

    "apply" must {
      "create a quarter from a date" when {
        "Q1" in {
          TaxQuarter.apply(LocalDate.of(2022, 6, 30)) mustBe Q1(2022)
        }
        "Q2" in {
          TaxQuarter.apply(LocalDate.of(2022, 9, 30)) mustBe Q2(2022)
        }
        "Q3" in {
          TaxQuarter.apply(LocalDate.of(2022, 12, 31)) mustBe Q3(2022)
        }
        "Q4" in {
          TaxQuarter.apply(LocalDate.of(2022, 3, 31)) mustBe Q4(2021)
        }
      }
    }

    "fromDate and toDate" must {
      "give correct dates for quarter 1" in {
        val quarter = Q1(2025)

        quarter.fromDate mustBe LocalDate.of(2025, 4, 1)
        quarter.toDate mustBe LocalDate.of(2025, 6, 30)
      }
      "give correct dates for quarter 2" in {
        val quarter = Q2(2025)

        quarter.fromDate mustBe LocalDate.of(2025, 7, 1)
        quarter.toDate mustBe LocalDate.of(2025, 9, 30)
      }
      "give correct dates for quarter 3" in {
        val quarter = Q3(2025)

        quarter.fromDate mustBe LocalDate.of(2025, 10, 1)
        quarter.toDate mustBe LocalDate.of(2025, 12, 31)
      }
      "give correct dates for quarter 4" in {
        val quarter = Q4(2025)

        quarter.fromDate mustBe LocalDate.of(2026, 1, 1)
        quarter.toDate mustBe LocalDate.of(2026, 3, 31)
      }
    }
  }

  "taxYears" should {

    "return 8 previous quarters" when {
      "for a return for a tax year in quarter 1 (1/4/2024)" in {
        val taxYears = service.taxYears(LocalDate.of(2024, 6, 30))
        taxYears mustBe Seq(LocalDate.of(2023, 4, 1) -> LocalDate.of(2024, 3, 31), LocalDate.of(2022, 4, 1) -> LocalDate.of(2023, 3, 31))
      }
      "for a return for a tax year in quarter 2 (1/7/2024)" in {
        val taxYears = service.taxYears(LocalDate.of(2024, 9, 30))
        taxYears mustBe Seq(
          LocalDate.of(2024, 4, 1) -> LocalDate.of(2024, 6, 30),
          LocalDate.of(2023, 4, 1) -> LocalDate.of(2024, 3, 31),
          LocalDate.of(2022, 7, 1) -> LocalDate.of(2023, 3, 31)
        )
      }
      "for a return for a tax year in quarter 3 (1/10/2024)" in {
        val taxYears = service.taxYears(LocalDate.of(2024, 12, 31))
        taxYears mustBe Seq(
          LocalDate.of(2024, 4, 1)  -> LocalDate.of(2024, 9, 30),
          LocalDate.of(2023, 4, 1)  -> LocalDate.of(2024, 3, 31),
          LocalDate.of(2022, 10, 1) -> LocalDate.of(2023, 3, 31)
        )
      }

      "for a return for a tax year in quarter 4 (1/1/2024)" in {
        val taxYears = service.taxYears(LocalDate.of(2024, 3, 31))
        taxYears mustBe Seq(
          LocalDate.of(2023, 4, 1) -> LocalDate.of(2023, 12, 31),
          LocalDate.of(2022, 4, 1) -> LocalDate.of(2023, 3, 31),
          LocalDate.of(2022, 1, 1) -> LocalDate.of(2022, 3, 31)
        )
      }
    }
  }

}
