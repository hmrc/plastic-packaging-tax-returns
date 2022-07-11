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

//todo rename this package
package uk.gov.hmrc.plasticpackagingtaxreturns.models.returns

import uk.gov.hmrc.plasticpackagingtaxreturns.models.ReturnType
import uk.gov.hmrc.plasticpackagingtaxreturns.models.ReturnType.ReturnType
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.PeriodKeyGettable
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.amends._
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.returns._

sealed trait ReturnValues {
  val periodKey: String
  val manufacturedPlasticWeight: Long
  val importedPlasticWeight: Long
  val exportedPlasticWeight: Long
  val humanMedicinesPlasticWeight: Long
  val recycledPlasticWeight: Long
  val convertedPackagingCredit: BigDecimal
  val submissionId: Option[String]
  val returnType: ReturnType
}

final case class NewReturnValues(
                                  periodKey: String,
                                  manufacturedPlasticWeight: Long,
                                  importedPlasticWeight: Long,
                                  exportedPlasticWeight: Long,
                                  humanMedicinesPlasticWeight: Long,
                                  recycledPlasticWeight: Long,
                                  convertedPackagingCredit: BigDecimal
                                ) extends ReturnValues {
  override val submissionId: Option[String] = None
  override val returnType: ReturnType = ReturnType.NEW
}

object NewReturnValues {

  def apply(userAnswers: UserAnswers): Option[NewReturnValues] = {

    val credits = userAnswers.get(AmendConvertedPackagingCreditGettable).getOrElse(BigDecimal(0)) //todo handle credits in frontend.

    for {
      periodKey <- userAnswers.get(PeriodKeyGettable)
      manufactured <- userAnswers.get(ManufacturedPlasticPackagingWeightGettable)
      imported <- userAnswers.get(ImportedPlasticPackagingWeightGettable)
      exported <- userAnswers.get(ExportedPlasticPackagingWeightGettable)
      humanMedicines <- userAnswers.get(NonExportedHumanMedicinesPlasticPackagingWeightGettable)
      recycled <- userAnswers.get(NonExportedRecycledPlasticPackagingWeightGettable)
    } yield {
      new NewReturnValues(
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

final case class AmendReturnValues(
                                    periodKey: String,
                                    manufacturedPlasticWeight: Long,
                                    importedPlasticWeight: Long,
                                    exportedPlasticWeight: Long,
                                    humanMedicinesPlasticWeight: Long,
                                    recycledPlasticWeight: Long,
                                    submission: String
                                  ) extends ReturnValues {
  val convertedPackagingCredit: BigDecimal = 0
  override val submissionId: Option[String] = Some(submission)
  override val returnType: ReturnType = ReturnType.AMEND
}

object AmendReturnValues {

  def apply(userAnswers: UserAnswers, submissionID: String): Option[AmendReturnValues] = {

    for {
      periodKey <- userAnswers.get(PeriodKeyGettable)
      manufactured <- userAnswers.get(AmendManufacturedPlasticPackagingGettable)
      imported <- userAnswers.get(AmendImportedPlasticPackagingGettable)
      exported <- userAnswers.get(AmendDirectExportPlasticPackagingGettable)
      humanMedicines <- userAnswers.get(AmendHumanMedicinePlasticPackagingGettable)
      recycled <- userAnswers.get(AmendRecycledPlasticPackagingGettable)
    } yield {
      new AmendReturnValues(
        periodKey,
        manufactured,
        imported,
        exported,
        humanMedicines,
        recycled,
        submissionID
      )
    }
  }

}

