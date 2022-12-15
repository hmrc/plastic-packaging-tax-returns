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

import play.api.libs.json.JsPath
import uk.gov.hmrc.plasticpackagingtaxreturns.models.ReturnType
import uk.gov.hmrc.plasticpackagingtaxreturns.models.ReturnType.ReturnType
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.PeriodKeyGettable
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.amends._
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.returns._
import uk.gov.hmrc.plasticpackagingtaxreturns.services.CreditsCalculationService.Credit

import java.time.LocalDate

sealed trait ReturnValues {
  val periodKey: String
  val periodEndDate: LocalDate
  val manufacturedPlasticWeight: Long
  val importedPlasticWeight: Long
  val exportedPlasticWeight: Long
  val humanMedicinesPlasticWeight: Long
  val recycledPlasticWeight: Long
  val convertedPackagingCredit: BigDecimal
  val submissionId: Option[String]
  val returnType: ReturnType
  val availableCredit: BigDecimal
}

final case class NewReturnValues(
                                  periodKey: String,
                                  periodEndDate: LocalDate,
                                  manufacturedPlasticWeight: Long,
                                  importedPlasticWeight: Long,
                                  exportedPlasticWeight: Long,
                                  humanMedicinesPlasticWeight: Long,
                                  recycledPlasticWeight: Long,
                                  convertedPackagingCredit: BigDecimal,
                                  availableCredit: BigDecimal
                                ) extends ReturnValues {
  override val submissionId: Option[String] = None
  override val returnType: ReturnType = ReturnType.NEW
}

object NewReturnValues {

  def apply(credits: Credit, availableCredit: BigDecimal)(userAnswers: UserAnswers): Option[NewReturnValues] =
    for {
      periodKey <- userAnswers.get(PeriodKeyGettable)
      periodEndDate <- userAnswers.get[LocalDate](JsPath \ "obligation" \ "toDate")
      manufactured <- userAnswers.get(ManufacturedPlasticPackagingWeightGettable)
      imported <- userAnswers.get(ImportedPlasticPackagingWeightGettable)
      exported <- userAnswers.get(ExportedPlasticPackagingWeightGettable)
      humanMedicines <- userAnswers.get(NonExportedHumanMedicinesPlasticPackagingWeightGettable)
      recycled <- userAnswers.get(NonExportedRecycledPlasticPackagingWeightGettable)
    } yield {
      new NewReturnValues(
        periodKey,
        periodEndDate,
        manufactured,
        imported,
        exported,
        humanMedicines,
        recycled,
        credits.moneyInPounds,
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
                                    humanMedicinesPlasticWeight: Long,
                                    recycledPlasticWeight: Long,
                                    submission: String
                                  ) extends ReturnValues {
  val convertedPackagingCredit: BigDecimal = 0
  val availableCredit: BigDecimal = 0
  override val submissionId: Option[String] = Some(submission)
  override val returnType: ReturnType = ReturnType.AMEND
}

object AmendReturnValues {

  def apply(userAnswers: UserAnswers): Option[AmendReturnValues] = {

    val original = userAnswers.get(ReturnDisplayApiGettable)

    for {
      submissionID <- original.map(_.idDetails.submissionId)
      periodKey <- userAnswers.get[String](JsPath() \ "amendSelectedPeriodKey")
      periodEndDate <- userAnswers.get[LocalDate](JsPath \ "amend" \"obligation" \ "toDate")
      manufactured <- userAnswers.get(AmendManufacturedPlasticPackagingGettable).orElse(original.map(_.returnDetails.manufacturedWeight))
      imported <- userAnswers.get(AmendImportedPlasticPackagingGettable).orElse(original.map(_.returnDetails.importedWeight))
      exported <- userAnswers.get(AmendDirectExportPlasticPackagingGettable).orElse(original.map(_.returnDetails.directExports))
      humanMedicines <- userAnswers.get(AmendHumanMedicinePlasticPackagingGettable).orElse(original.map(_.returnDetails.humanMedicines))
      recycled <- userAnswers.get(AmendRecycledPlasticPackagingGettable).orElse(original.map(_.returnDetails.recycledPlastic))
    } yield {
      new AmendReturnValues(
        periodKey,
        periodEndDate,
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

final case class OriginalReturnForAmendValues(
                                               periodKey: String,
                                               periodEndDate: LocalDate,
                                               manufacturedPlasticWeight: Long,
                                               importedPlasticWeight: Long,
                                               exportedPlasticWeight: Long,
                                               humanMedicinesPlasticWeight: Long,
                                               recycledPlasticWeight: Long,
                                               submission: String
                                             ) extends ReturnValues {
  val convertedPackagingCredit: BigDecimal = 0
  val availableCredit: BigDecimal = 0
  override val submissionId: Option[String] = Some(submission)
  override val returnType: ReturnType = ReturnType.AMEND
}

object OriginalReturnForAmendValues {

  def apply(userAnswers: UserAnswers): Option[OriginalReturnForAmendValues] = {
    userAnswers.get(ReturnDisplayApiGettable).map(original =>
      new OriginalReturnForAmendValues(
        userAnswers.getOrFail[String](JsPath() \ "amendSelectedPeriodKey"),
        userAnswers.getOrFail[LocalDate](JsPath \ "amend" \"obligation" \ "toDate"),
        original.returnDetails.manufacturedWeight,
        original.returnDetails.importedWeight,
        original.returnDetails.directExports,
        original.returnDetails.humanMedicines,
        original.returnDetails.recycledPlastic,
        original.idDetails.submissionId
      )
    )
  }

}