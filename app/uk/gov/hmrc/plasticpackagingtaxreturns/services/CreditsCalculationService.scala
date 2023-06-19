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
import play.api.libs.json.JsPath
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.{Gettable, UserAnswers}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.returns.{ConvertedCreditWeightGettable, ConvertedCreditYesNoGettable, ExportedCreditWeightGettable, ExportedCreditYesNoGettable}
import uk.gov.hmrc.plasticpackagingtaxreturns.services.CreditsCalculationService.Credit

class CreditsCalculationService @Inject()(convert: WeightToPoundsConversionService) {

  def totalRequestedCredit(userAnswers: UserAnswers): Credit = {
    val exportedWeight: Long = lookupWeight(userAnswers, ExportedCreditYesNoGettable, ExportedCreditWeightGettable)
    val convertedWeight: Long = lookupWeight(userAnswers, ConvertedCreditYesNoGettable, ConvertedCreditWeightGettable)
    val totalWeight = exportedWeight + convertedWeight

    Credit(
      totalWeight,
      convert.weightToCredit(totalWeight)
    )
  }

  private def lookupWeight(userAnswers: UserAnswers, yesNo: Gettable[Boolean], weight: Gettable[Long]): Long = {
    userAnswers
      .get(yesNo)
      .filter(isYes => isYes && userAnswers.get[Boolean](JsPath \ "whatDoYouWantToDo").contains(true))
      .flatMap(_ => userAnswers.get(weight))
      .getOrElse(0L)
  }
}

object CreditsCalculationService {

  final case class Credit(weight: Long, moneyInPounds: BigDecimal)

}
