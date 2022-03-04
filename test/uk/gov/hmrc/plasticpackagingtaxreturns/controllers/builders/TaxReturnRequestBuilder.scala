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

import uk.gov.hmrc.plasticpackagingtaxreturns.models.{
  ConvertedPackagingCredit,
  ImportedPlasticWeight,
  ManufacturedPlasticWeight,
  TaxReturnRequest,
  _
}

import java.time.LocalDate

//noinspection ScalaStyle
trait TaxReturnRequestBuilder {

  private type TaxReturnRequestModifier = TaxReturnRequest => TaxReturnRequest

  def aTaxReturnRequest(modifiers: TaxReturnRequestModifier*): TaxReturnRequest =
    modifiers.foldLeft(modelWithDefaults)((current, modifier) => modifier(current))

  private def modelWithDefaults: TaxReturnRequest =
    TaxReturnRequest(obligation = Some(defaultTaxReturnRequestObligation),
                     manufacturedPlasticWeight = Some(ManufacturedPlasticWeight(5)),
                     importedPlasticWeight = Some(ImportedPlasticWeight(6)),
                     humanMedicinesPlasticWeight = Some(HumanMedicinesPlasticWeight(1)),
                     exportedPlasticWeight = Some(ExportedPlasticWeight(2000)),
                     convertedPackagingCredit = Some(ConvertedPackagingCredit(1010)),
                     recycledPlasticWeight = Some(RecycledPlasticWeight(1000)),
                     metaData = MetaData()
    )

  val defaultTaxReturnRequestObligation = TaxReturnObligation(fromDate = LocalDate.parse("2022-04-01"),
                                                              toDate = LocalDate.parse("2022-06-30"),
                                                              dueDate = LocalDate.parse("2022-09-30"),
                                                              periodKey = "22AC"
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

  def withRecycledPlasticWeight(recycledPlasticWeight: RecycledPlasticWeight): TaxReturnRequestModifier =
    _.copy(recycledPlasticWeight = Some(recycledPlasticWeight))

  def withMetadata(metaData: MetaData): TaxReturnRequestModifier = _.copy(metaData = metaData)
}
