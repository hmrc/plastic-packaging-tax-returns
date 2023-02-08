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

import org.mockito.Mockito.{never, reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig

import java.time.LocalDate

class TaxRateServiceSpec extends PlaySpec with BeforeAndAfterEach {

  val mockAppConfig: AppConfig = mock[AppConfig]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAppConfig)
    when(mockAppConfig.taxRegimeStartDate) thenReturn LocalDate.of(2022, 4, 1)
    when(mockAppConfig.taxRateBefore1stApril2022) thenReturn 1.44
    when(mockAppConfig.taxRateFrom1stApril2022) thenReturn 3.14
  }

  val sut = new TaxRateService(mockAppConfig)

  "lookupTaxRateForPeriod" must {
    "return the correct tax rate" when {
      "before 1/4/2022" in {
        sut.lookupTaxRateForPeriod(LocalDate.of(2022, 3, 31)) mustBe 1.44

        verify(mockAppConfig).taxRateBefore1stApril2022
        verify(mockAppConfig, never()).taxRateFrom1stApril2022
      }
      "on or after 1/4/2022" in {
        sut.lookupTaxRateForPeriod(LocalDate.of(2022, 4, 1)) mustBe 3.14

        verify(mockAppConfig, never()).taxRateBefore1stApril2022
        verify(mockAppConfig).taxRateFrom1stApril2022
      }
    }
  }

}
