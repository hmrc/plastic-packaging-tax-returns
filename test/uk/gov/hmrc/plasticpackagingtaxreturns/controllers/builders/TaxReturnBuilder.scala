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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.builders

import org.joda.time.DateTime
import uk.gov.hmrc.plasticpackagingtaxreturns.models.ReturnType.ReturnType
import uk.gov.hmrc.plasticpackagingtaxreturns.models._

//noinspection ScalaStyle
trait TaxReturnBuilder {

  private type TaxReturnModifier = TaxReturn => TaxReturn

  def aTaxReturn(modifiers: TaxReturnModifier*): TaxReturn =
    modifiers.foldLeft(modelWithDefaults)((current, modifier) => modifier(current))

  private def modelWithDefaults: TaxReturn =
    TaxReturn(
      id = "id",
      periodKey = "22AC",
      returnType = ReturnType.NEW,
      manufacturedPlastic = Some(true),
      manufacturedPlasticWeight = ManufacturedPlasticWeight(0),
      importedPlastic = Some(true),
      importedPlasticWeight = ImportedPlasticWeight(0),
      exportedPlastic = Some(true),
      exportedPlasticWeight = ExportedPlasticWeight(0),
      humanMedicinesPlastic = Some(true),
      humanMedicinesPlasticWeight = HumanMedicinesPlasticWeight(0),
      recycledPlastic = Some(true),
      recycledPlasticWeight = RecycledPlasticWeight(0),
      convertedPackagingCredit = ConvertedPackagingCredit(0),
      lastModifiedDateTime = Some(DateTime)
    )

  def withId(id: String): TaxReturnModifier = _.copy(id = id)

  def withManufacturedPlasticWeight(totalKg: Long): TaxReturnModifier =
    _.copy(manufacturedPlasticWeight =
      ManufacturedPlasticWeight(totalKg = totalKg)
    )

  def withImportedPlasticWeight(totalKg: Long): TaxReturnModifier =
    _.copy(importedPlasticWeight =
      ImportedPlasticWeight(totalKg = totalKg)
    )

  def withHumanMedicinesPlasticWeight(totalKg: Long): TaxReturnModifier =
    _.copy(humanMedicinesPlasticWeight =
      HumanMedicinesPlasticWeight(totalKg = totalKg)
    )

  def withDirectExportDetails(totalKg: Long): TaxReturnModifier =
    _.copy(exportedPlasticWeight =
      ExportedPlasticWeight(totalKg = totalKg)
    )

  def withConvertedPlasticPackagingCredit(totalPence: Long): TaxReturnModifier =
    _.copy(convertedPackagingCredit =
      ConvertedPackagingCredit(totalPence)
    )

  def withRecycledPlasticWeight(totalKg: Long): TaxReturnModifier =
    _.copy(recycledPlasticWeight = RecycledPlasticWeight(totalKg = totalKg))

  def withTimestamp(dateTime: DateTime): TaxReturnModifier = _.copy(lastModifiedDateTime = Some(dateTime))
}
