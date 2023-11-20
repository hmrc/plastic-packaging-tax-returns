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
import uk.gov.hmrc.plasticpackagingtaxreturns.models._

import java.time.LocalDate

class CreditsCalculationService @Inject()(taxCalculationService: TaxCalculationService) {

  def totalRequestedCredit_old(userAnswers: UserAnswers): TaxablePlastic = {
    isClaimingCredit(userAnswers)
      .flatMap(_ => newJourney(userAnswers).orElse(Some(currentJourney(userAnswers))))
      .getOrElse(TaxablePlastic.zero)
  }

  private def isClaimingCredit(userAnswers: UserAnswers) =
    userAnswers.get[Boolean](JsPath \ "whatDoYouWantToDo")
      .filter(_ == true)

  private def currentJourney(userAnswers: UserAnswers) = {
    val endOfFirstYearOfPpt = LocalDate.of(2023, 3, 31)
    val exportedCredit = CreditsAnswer.readFrom(userAnswers, "exportedCredits")
    val convertedCredit = CreditsAnswer.readFrom(userAnswers, "convertedCredits")
    val totalWeight = exportedCredit.value + convertedCredit.value
    taxCalculationService.weightToCredit(endOfFirstYearOfPpt, totalWeight)
  }

  private def newJourney(userAnswers: UserAnswers): Option[TaxablePlastic] = {
    userAnswers
      .get[Map[String, SingleYearClaim]](JsPath \ "credit")
      .flatMap { map =>
        map.values.headOption
      }
      .map(singleYearClaim => singleYearClaim.calculate(taxCalculationService))
  }

  def newJourney2(userAnswers: UserAnswers): Map[String, TaxablePlastic] = {
    if(isClaimingCredit(userAnswers).contains(true)) {
      userAnswers
        .get[Map[String, SingleYearClaim]](JsPath \ "credit")
        .getOrElse(Map())
        .view.mapValues(_.calculate(taxCalculationService))
        .toMap
    } else {
      Map.empty
    }
  }

  def totalRequestedCredit(userAnswers: UserAnswers, availableCreditInPounds: BigDecimal): CreditCalculation = {
    CreditCalculation.totalUp(newJourney2(userAnswers), availableCreditInPounds)
  }

}
