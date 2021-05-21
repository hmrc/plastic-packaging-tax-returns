/*
 * Copyright 2021 HM Revenue & Customs
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

import uk.gov.hmrc.plasticpackagingtaxreturns.models.{
  ConvertedPackagingCredit,
  ImportedPlasticWeight,
  ManufacturedPlasticWeight,
  TaxReturnRequest
}
import uk.gov.hmrc.plasticpackagingtaxreturns.models._

//noinspection ScalaStyle
trait TaxReturnRequestBuilder {

  private type TaxReturnRequestModifier = TaxReturnRequest => TaxReturnRequest

  def aTaxReturnRequest(modifiers: TaxReturnRequestModifier*): TaxReturnRequest =
    modifiers.foldLeft(modelWithDefaults)((current, modifier) => modifier(current))

  private def modelWithDefaults: TaxReturnRequest =
    TaxReturnRequest(manufacturedPlasticWeight = Some(ManufacturedPlasticWeight(5, 5)),
                     importedPlasticWeight = Some(ImportedPlasticWeight(6)),
                     humanMedicinesPlasticWeight = Some(HumanMedicinesPlasticWeight(1)),
                     exportedPlasticWeight = Some(ExportedPlasticWeight(2000, 460089)),
                     convertedPackagingCredit = Some(ConvertedPackagingCredit(1010)),
                     metaData = MetaData()
    )

  def withManufacturedPlasticWeight(manufacturedPlasticWeight: ManufacturedPlasticWeight): TaxReturnRequestModifier =
    _.copy(manufacturedPlasticWeight = Some(manufacturedPlasticWeight))

  def withImportedPlasticWeight(importedPlasticWeight: ImportedPlasticWeight): TaxReturnRequestModifier =
    _.copy(importedPlasticWeight = Some(importedPlasticWeight))

  def withConvertedPlasticPackagingCredit(
    convertedPlasticPackagingCredit: ConvertedPackagingCredit
  ): TaxReturnRequestModifier =
    _.copy(convertedPackagingCredit = Some(convertedPlasticPackagingCredit))

  def withHumanMedicinesPlasticWeight(
    humanMedicinesPlasticWeight: HumanMedicinesPlasticWeight
  ): TaxReturnRequestModifier =
    _.copy(humanMedicinesPlasticWeight = Some(humanMedicinesPlasticWeight))

  def withDirectExportDetails(directExportDetails: ExportedPlasticWeight): TaxReturnRequestModifier =
    _.copy(exportedPlasticWeight = Some(directExportDetails))

  def withMetadata(metaData: MetaData): TaxReturnRequestModifier = _.copy(metaData = metaData)
}
