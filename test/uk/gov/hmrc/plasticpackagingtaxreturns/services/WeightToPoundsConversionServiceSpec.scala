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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{atLeastOnce, never, reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig

import java.time.LocalDate

class WeightToPoundsConversionServiceSpec extends PlaySpec with BeforeAndAfterEach {

  val mockTaxRateService: TaxRateService = mock[TaxRateService]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockTaxRateService)
  }

  val sut = new WeightToPoundsConversionService(mockTaxRateService)

  val firstApril2022: LocalDate = LocalDate.of(2022, 4, 1)

  "weightToDebit" must {
    behave like aConverter(weight => sut.weightToDebit(firstApril2022, weight))

    "round DOWN for conversion rate of 3 d.p." in {
      when(mockTaxRateService.lookupTaxRateForPeriod(any())).thenReturn(0.333)
      sut.weightToDebit(firstApril2022, 5L) mustBe BigDecimal(1.66)
    }
  }

  "weightToCredit" must {
    behave like aConverter(sut.weightToCredit)

    "round UP for conversion rate of 3 d.p." in {
      when(mockTaxRateService.lookupTaxRateForPeriod(any())).thenReturn(0.333)
      sut.weightToCredit(5L) mustBe BigDecimal(1.67)
    }
  }

  def aConverter(method: Long => BigDecimal): Unit = {

    "multiply the weight by the conversion rate 1 d.p." in {
      when(mockTaxRateService.lookupTaxRateForPeriod(any())).thenReturn(0.5)
      method(5L) mustBe BigDecimal(2.5)
    }

    "multiply the weight by the conversion rate 2 d.p." in {
      when(mockTaxRateService.lookupTaxRateForPeriod(any())).thenReturn(0.25)
      method(5L) mustBe BigDecimal(1.25)
    }
    
  }
}
