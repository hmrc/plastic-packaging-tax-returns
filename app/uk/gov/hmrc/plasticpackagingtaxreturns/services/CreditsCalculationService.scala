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

import com.google.inject.Inject
import uk.gov.hmrc.plasticpackagingtaxreturns.models.{TaxablePlastic, CreditsAnswer}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.returns.ReturnObligationToDateGettable

import java.time.LocalDate

class CreditsCalculationService @Inject()(convert: WeightToPoundsConversionService) {
  
  def totalRequestedCredit(userAnswers: UserAnswers): TaxablePlastic = {
    val periodEndDate = userAnswers.getOrFail[LocalDate](ReturnObligationToDateGettable)
    val exportedCredit = CreditsAnswer.readFrom(userAnswers, "exportedCredits")
    val convertedCredit = CreditsAnswer.readFrom(userAnswers, "convertedCredits")
    val totalWeight = exportedCredit.value + convertedCredit.value
    convert.weightToCredit(periodEndDate, totalWeight)
  }
}
