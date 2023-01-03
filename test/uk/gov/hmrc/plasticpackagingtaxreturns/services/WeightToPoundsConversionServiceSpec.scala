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

import org.mockito.Mockito.{atLeastOnce, never, reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig

import java.time.LocalDate

class WeightToPoundsConversionServiceSpec extends PlaySpec with BeforeAndAfterEach {

  val mockAppConfig: AppConfig = mock[AppConfig]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAppConfig)
    when(mockAppConfig.taxRegimeStartDate) thenReturn LocalDate.of(2022, 4, 1)
    when(mockAppConfig.taxRateBefore1stApril2022) thenReturn BigDecimal(1.44)
    when(mockAppConfig.taxRateFrom1stApril2022) thenReturn BigDecimal(3.14)
  }

  val sut = new WeightToPoundsConversionService(mockAppConfig)

  val firstApril2022: LocalDate = LocalDate.of(2022, 4, 1)

  "weightToDebit" must {
    behave like aConverter(weight => sut.weightToDebit(firstApril2022, weight))

    "round DOWN for conversion rate of 3 d.p." in {
      when(mockAppConfig.taxRateFrom1stApril2022).thenReturn(0.333)
      sut.weightToDebit(firstApril2022, 5L) mustBe BigDecimal(1.66)
    }
  }

  "weightToCredit" must {
    behave like aConverter(sut.weightToCredit)

    "round UP for conversion rate of 3 d.p." in {
      when(mockAppConfig.taxRateFrom1stApril2022).thenReturn(0.333)
      sut.weightToCredit(5L) mustBe BigDecimal(1.67)
    }
  }

  "look up the tax rate" when {
    "before 1/4/2022" in {
      sut.lookupTaxRateForPeriod(LocalDate.of(2022, 3, 31)) mustBe BigDecimal(1.44)
      verify(mockAppConfig, atLeastOnce()).taxRateBefore1stApril2022
      verify(mockAppConfig, never()).taxRateFrom1stApril2022
    }
    "on or after 1/4/2022" in {
      sut.lookupTaxRateForPeriod(LocalDate.of(2022, 4, 1)) mustBe BigDecimal(3.14)
      verify(mockAppConfig, never()).taxRateBefore1stApril2022
      verify(mockAppConfig, atLeastOnce()).taxRateFrom1stApril2022
    }
  }

  def aConverter(method: Long => BigDecimal): Unit = {
    
    "multiply the weight by the conversion rate 1 d.p." in {
      when(mockAppConfig.taxRateFrom1stApril2022).thenReturn(0.5)
      method(5L) mustBe BigDecimal(2.5)
    }

    "multiply the weight by the conversion rate 2 d.p." in {
      when(mockAppConfig.taxRateFrom1stApril2022).thenReturn(0.25)
      method(5L) mustBe BigDecimal(1.25)
    }
    
  }
}
