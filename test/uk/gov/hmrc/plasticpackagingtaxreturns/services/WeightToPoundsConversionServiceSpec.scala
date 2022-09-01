/*
 * Copyright 2022 HM Revenue & Customs
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
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig

class WeightToPoundsConversionServiceSpec extends PlaySpec with BeforeAndAfterEach {

  val mockAppConfig: AppConfig = mock[AppConfig]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAppConfig)
  }

  val sut = new WeightToPoundsConversionService(mockAppConfig)

  "weightToDebit" must {
    behave like aConverter(_.weightToDebit)

    "round DOWN for conversion rate of 3 d.p." in {
      when(mockAppConfig.taxRatePoundsPerKg).thenReturn(0.333)

      sut.weightToDebit(5L) mustBe BigDecimal(1.66)
    }
  }

  "weightToCredit" must {
    behave like aConverter(_.weightToCredit)

    "round UP for conversion rate of 3 d.p." in {
      when(mockAppConfig.taxRatePoundsPerKg).thenReturn(0.333)

      sut.weightToCredit(5L) mustBe BigDecimal(1.67)
    }
  }

  def aConverter(ServiceToMethod: WeightToPoundsConversionService => Long => BigDecimal) = {
    val method = ServiceToMethod(sut)
    "multiply the weight by the conversion rate 1 d.p." in {
      when(mockAppConfig.taxRatePoundsPerKg).thenReturn(0.5)

      method(5L) mustBe BigDecimal(2.5)
    }

    "multiply the weight by the conversion rate 2 d.p." in {
      when(mockAppConfig.taxRatePoundsPerKg).thenReturn(0.25)

      method(5L) mustBe BigDecimal(1.25)
    }
  }
}
