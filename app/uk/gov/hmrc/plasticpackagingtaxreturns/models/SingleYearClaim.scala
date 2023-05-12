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

package uk.gov.hmrc.plasticpackagingtaxreturns.models

import play.api.libs.json.{JsPath, Json, OFormat}
import uk.gov.hmrc.plasticpackagingtaxreturns.services.WeightToPoundsConversionService

import java.time.LocalDate


case class SingleYearClaim(endDate: LocalDate, exportedCredits: Option[CreditsAnswer], convertedCredits: Option[CreditsAnswer]) {
  def calculate(weightToPoundsConversionService: WeightToPoundsConversionService): TaxablePlastic = {
    val totalWeight = CreditsAnswer.from(exportedCredits).value + CreditsAnswer.from(convertedCredits).value 
    weightToPoundsConversionService.weightToCredit(endDate, totalWeight)
  }
}

object SingleYearClaim {

  def readFirstFrom(userAnswers: UserAnswers): SingleYearClaim =
    userAnswers
      .getOrFail[Map[String, SingleYearClaim]](JsPath \ "credit")
      .values
      .head

  implicit val formats: OFormat[SingleYearClaim] = Json.format[SingleYearClaim]
}