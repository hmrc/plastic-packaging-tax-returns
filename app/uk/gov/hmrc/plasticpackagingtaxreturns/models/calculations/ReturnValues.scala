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

package uk.gov.hmrc.plasticpackagingtaxreturns.models.calculations

import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables._
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.returns.{ConvertedPackagingCreditGettable, ExportedPlasticPackagingWeightGettable, ImportedPlasticPackagingWeightGettable, ManufacturedPlasticPackagingWeightGettable, NonExportedHumanMedicinesPlasticPackagingWeightGettable, NonExportedRecycledPlasticPackagingWeightGettable}

case class ReturnValues(periodKey: String,
                        manufacturedPlasticWeight: Long,
                        importedPlasticWeight: Long,
                        exportedPlasticWeight: Long,
                        humanMedicinesPlasticWeight: Long,
                        recycledPlasticWeight: Long,
                        convertedPackagingCredit: BigDecimal // TODO - need to factor credits into the calculations
                       )

object ReturnValues {

  def apply(userAnswers: UserAnswers): Option[ReturnValues] = {

    for {
      periodKey <- userAnswers.get(PeriodKeyGettable)
      manufactured <- userAnswers.get(ManufacturedPlasticPackagingWeightGettable)
      imported <- userAnswers.get(ImportedPlasticPackagingWeightGettable)
      exported <- userAnswers.get(ExportedPlasticPackagingWeightGettable)
      humanMedicines <- userAnswers.get(NonExportedHumanMedicinesPlasticPackagingWeightGettable)
      recycled <- userAnswers.get(NonExportedRecycledPlasticPackagingWeightGettable)
    } yield {

      val credits = userAnswers.get(ConvertedPackagingCreditGettable).getOrElse(BigDecimal(0))

      ReturnValues(
        periodKey,
        manufactured,
        imported,
        exported,
        humanMedicines,
        recycled,
        credits
      )
    }
  }

}


