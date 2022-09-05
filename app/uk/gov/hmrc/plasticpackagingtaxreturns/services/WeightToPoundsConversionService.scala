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


import com.google.inject.Inject
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig

import scala.math.BigDecimal.RoundingMode

class WeightToPoundsConversionService @Inject()(appConfig: AppConfig) {

  /*
    Policy have advised that Rounding must always happen in favor of the customer.
    When Calculating a credit (money given to the customer) round up.
    When calculating a debit (money taken from the customer) round down.

    While the tax rate conversion is currently 0.2 this rounding does not take affect,
     however if it were ever more than 2 d.p. this would make a difference so it is
     important to use the correct conversion method
   */

  def weightToDebit(weight: Long): BigDecimal = convert(weight).setScale(2, RoundingMode.DOWN)
  def weightToCredit(weight: Long): BigDecimal = convert(weight).setScale(2, RoundingMode.UP)

  private def convert(weight: Long): BigDecimal = BigDecimal(weight) * appConfig.taxRatePoundsPerKg


}
