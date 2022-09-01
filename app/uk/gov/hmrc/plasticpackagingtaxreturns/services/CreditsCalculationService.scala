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
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.returns.{ConvertedCreditWeightGettable, ExportedCreditWeightGettable}

class CreditsCalculationService @Inject()(convert: WeightToPoundsConversionService) {

  def totalRequestCreditInPounds(userAnswers: UserAnswers): BigDecimal = {
    val exportedWeight: Long = userAnswers.get(ExportedCreditWeightGettable).getOrElse(0)
    val convertedWeight: Long = userAnswers.get(ConvertedCreditWeightGettable).getOrElse(0)
    val totalWeight = exportedWeight + convertedWeight

    convert.weightToCredit(totalWeight)
  }

}
