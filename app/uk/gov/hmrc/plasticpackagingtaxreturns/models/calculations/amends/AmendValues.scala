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

package uk.gov.hmrc.plasticpackagingtaxreturns.models.calculations.amends

import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.PeriodKeyGettable
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.amends._

case class AmendValues(periodKey: String,
                       manufacturedPlasticWeight: Long,
                       importedPlasticWeight: Long,
                       exportedPlasticWeight: Long,
                       humanMedicinesPlasticWeight: Long,
                       recycledPlasticWeight: Long,
                       convertedPackagingCredit: BigDecimal // TODO - need to factor credits into the calculations
                       )

object AmendValues {

  def apply(userAnswers: UserAnswers): Option[AmendValues] = {

    val credits = userAnswers.get(AmendConvertedPackagingCreditGettable).getOrElse(BigDecimal(0))

    for {
      periodKey <- userAnswers.get(PeriodKeyGettable)
      manufactured <- userAnswers.get(AmendManufacturedPlasticPackagingGettable)
      imported <- userAnswers.get(AmendImportedPlasticPackagingGettable)
      exported <- userAnswers.get(AmendDirectExportPlasticPackagingGettable)
      humanMedicines <- userAnswers.get(AmendHumanMedicinePlasticPackagingGettable)
      recycled <- userAnswers.get(AmendRecycledPlasticPackagingPage)
    } yield {

      AmendValues(
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



