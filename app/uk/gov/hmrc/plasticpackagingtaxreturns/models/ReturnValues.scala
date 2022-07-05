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

package uk.gov.hmrc.plasticpackagingtaxreturns.models

import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables._

case class ReturnValues(
                         manufacturedPlasticWeight: Long,
                         importedPlasticWeight: Long,
                         exportedPlasticWeight: Long,
                         humanMedicinesPlasticWeight: Long,
                         recycledPlasticWeight: Long,
                         convertedPackagingCredit: BigDecimal // TODO - need to factor credits into the calculations
                       )

object ReturnValues {

  def apply(taxReturn: TaxReturn): ReturnValues = {
    ReturnValues(taxReturn.manufacturedPlasticWeight.totalKg,
      taxReturn.importedPlasticWeight.totalKg,
      taxReturn.exportedPlasticWeight.totalKg,
      taxReturn.humanMedicinesPlasticWeight.totalKg,
      taxReturn.recycledPlasticWeight.totalKg,
      taxReturn.convertedPackagingCredit.totalInPounds
    )
  }

  def apply(userAnswers: UserAnswers): Option[ReturnValues] = {

    for {
      manufactured <- userAnswers.get(ManufacturedPlasticPackagingWeightGettable)
      imported <- userAnswers.get(ImportedPlasticPackagingWeightGettable)
      exported <- userAnswers.get(ExportedPlasticPackagingWeightGettable)
      humanMedicines <- userAnswers.get(NonExportedHumanMedicinesPlasticPackagingWeightGettable)
      recycled <- userAnswers.get(NonExportedRecycledPlasticPackagingWeightGettable)
      credits <- userAnswers.get(ConvertedPackagingCreditGettable)
    } yield
      ReturnValues(
        manufactured,
        imported,
        exported,
        humanMedicines,
        recycled,
        credits
      )
  }

}


