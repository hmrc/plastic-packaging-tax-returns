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
  HumanMedicinesPlasticWeight,
  ImportedPlasticWeight,
  ManufacturedPlasticWeight,
  TaxReturnRequest
}

//noinspection ScalaStyle
trait TaxReturnRequestBuilder {

  private type TaxReturnRequestModifier = TaxReturnRequest => TaxReturnRequest

  def aTaxReturnRequest(modifiers: TaxReturnRequestModifier*): TaxReturnRequest =
    modifiers.foldLeft(modelWithDefaults)((current, modifier) => modifier(current))

  private def modelWithDefaults: TaxReturnRequest =
    TaxReturnRequest(manufacturedPlasticWeight = ManufacturedPlasticWeight(Some(5), Some(5)),
                     importedPlasticWeight = ImportedPlasticWeight(Some(6), Some(6)),
                     humanMedicinesPlasticWeight = HumanMedicinesPlasticWeight(Some(1))
    )

  def withManufacturedPlasticWeight(manufacturedPlasticWeight: ManufacturedPlasticWeight): TaxReturnRequestModifier =
    _.copy(manufacturedPlasticWeight = manufacturedPlasticWeight)

  def withImportedPlasticWeight(importedPlasticWeight: ImportedPlasticWeight): TaxReturnRequestModifier =
    _.copy(importedPlasticWeight = importedPlasticWeight)

  def withHumanMedicinesPlasticWeight(
    humanMedicinesPlasticWeight: HumanMedicinesPlasticWeight
  ): TaxReturnRequestModifier =
    _.copy(humanMedicinesPlasticWeight = humanMedicinesPlasticWeight)

}
