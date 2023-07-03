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

//todo rename this package
package uk.gov.hmrc.plasticpackagingtaxreturns.models.returns

import play.api.libs.json.JsPath
import uk.gov.hmrc.plasticpackagingtaxreturns.models.ReturnType.ReturnType
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.PeriodKeyGettable
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.amends._
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.returns._
import uk.gov.hmrc.plasticpackagingtaxreturns.models.{ReturnType, TaxablePlastic, UserAnswers}

import java.time.LocalDate

sealed trait ReturnValues{
  val periodKey: String
  val periodEndDate: LocalDate
  val manufacturedPlasticWeight: Long
  val importedPlasticWeight: Long
  val humanMedicinesPlasticWeight: Long
  val recycledPlasticWeight: Long
  val convertedPackagingCredit: BigDecimal
  val submissionId: Option[String]
  val returnType: ReturnType
  val availableCredit: BigDecimal

  def totalExportedPlastic: Long
}

final case class NewReturnValues(
                                  periodKey: String,
                                  periodEndDate: LocalDate,
                                  manufacturedPlasticWeight: Long,
                                  importedPlasticWeight: Long,
                                  exportedPlasticWeight: Long,
                                  exportedByAnotherBusinessPlasticWeight: Long,
                                  humanMedicinesPlasticWeight: Long,
                                  recycledPlasticWeight: Long,
                                  convertedPackagingCredit: BigDecimal,
                                  availableCredit: BigDecimal,
                                ) extends ReturnValues {
  override val submissionId: Option[String] = None
  override val returnType: ReturnType = ReturnType.NEW

  override def totalExportedPlastic: Long = {
    exportedPlasticWeight + exportedByAnotherBusinessPlasticWeight
  }
}

object NewReturnValues {

  def apply(creditClaim: TaxablePlastic, availableCredit: BigDecimal)(userAnswers: UserAnswers): Option[NewReturnValues] =
    for {
      // todo which of these are must-have vs can default?
      // todo allow exception from UserAnswers (which has name of missing element) to percolate up
      manufactured <- userAnswers.get(ManufacturedPlasticPackagingWeightGettable)
      periodEndDate = userAnswers.getOrFail(ReturnObligationToDateGettable)
      periodKey = userAnswers.getOrFail(PeriodKeyGettable)
      imported <- userAnswers.get(ImportedPlasticPackagingWeightGettable)
      exported <- userAnswers.get(ExportedPlasticPackagingWeightGettable)
      exportedByAnotherBusiness <- userAnswers.get(AnotherBusinessExportWeightGettable).orElse(Some(0L))
      humanMedicines <- userAnswers.get(NonExportedHumanMedicinesPlasticPackagingWeightGettable)
      recycled <- userAnswers.get(NonExportedRecycledPlasticPackagingWeightGettable)
    } yield {
      new NewReturnValues(
        periodKey,
        periodEndDate,
        manufactured,
        imported,
        exported,
        exportedByAnotherBusiness,
        humanMedicines,
        recycled,
        creditClaim.moneyInPounds,
        availableCredit
      )
    }

}

final case class AmendReturnValues(
                                    periodKey: String,
                                    periodEndDate: LocalDate,
                                    manufacturedPlasticWeight: Long,
                                    importedPlasticWeight: Long,
                                    exportedPlasticWeight: Long,
                                    exportedByAnotherBusinessPlasticWeight: Long,
                                    humanMedicinesPlasticWeight: Long,
                                    recycledPlasticWeight: Long,
                                    submission: String
                                  ) extends ReturnValues {
  val convertedPackagingCredit: BigDecimal = 0
  val availableCredit: BigDecimal = 0
  override val submissionId: Option[String] = Some(submission)
  override val returnType: ReturnType = ReturnType.AMEND

  override def totalExportedPlastic: Long =
    exportedPlasticWeight + exportedByAnotherBusinessPlasticWeight
}

object AmendReturnValues {

  def apply(userAnswers: UserAnswers): Option[AmendReturnValues] = {

    // TODO which of these are really must-haves vs can have default values
    val original = userAnswers.get(ReturnDisplayApiGettable)

    for {
      submissionID <- original.map(_.idDetails.submissionId)
      periodKey = userAnswers.getOrFail[String](JsPath() \ "amendSelectedPeriodKey")
      periodEndDate = userAnswers.getOrFail[LocalDate](JsPath \ "amend" \"obligation" \ "toDate")
      manufactured <- userAnswers.get(AmendManufacturedPlasticPackagingGettable).orElse(original.map(_.returnDetails.manufacturedWeight))
      imported <- userAnswers.get(AmendImportedPlasticPackagingGettable).orElse(original.map(_.returnDetails.importedWeight))
      exported <- userAnswers.get(AmendDirectExportPlasticPackagingGettable).orElse(original.map(_.returnDetails.directExports))
      exportedByAnotherBusiness <- userAnswers.get(AmendExportedByAnotherBusinessPlasticPackagingGettable).orElse(Some(0L))
      humanMedicines <- userAnswers.get(AmendHumanMedicinePlasticPackagingGettable).orElse(original.map(_.returnDetails.humanMedicines))
      recycled <- userAnswers.get(AmendRecycledPlasticPackagingGettable).orElse(original.map(_.returnDetails.recycledPlastic))
    } yield {
      new AmendReturnValues(
        periodKey,
        periodEndDate,
        manufactured,
        imported,
        exported,
        exportedByAnotherBusiness,
        humanMedicines,
        recycled,
        submissionID
      )
    }
  }


}