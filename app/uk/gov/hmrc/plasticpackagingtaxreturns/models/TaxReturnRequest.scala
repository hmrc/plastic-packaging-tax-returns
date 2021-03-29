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

package uk.gov.hmrc.plasticpackagingtaxreturns.models

case class TaxReturnRequest(
  manufacturedPlasticWeight: Option[ManufacturedPlasticWeight],
  importedPlasticWeight: Option[ImportedPlasticWeight],
  humanMedicinesPlasticWeight: Option[HumanMedicinesPlasticWeight],
  exportedPlasticWeight: Option[ExportedPlasticWeight],
  convertedPackagingCredit: Option[ConvertedPackagingCredit],
  metaData: MetaData
) {

  def toTaxReturn(providerId: String): TaxReturn =
    TaxReturn(id = providerId,
              manufacturedPlasticWeight = this.manufacturedPlasticWeight,
              importedPlasticWeight = this.importedPlasticWeight,
              humanMedicinesPlasticWeight = this.humanMedicinesPlasticWeight,
              exportedPlasticWeight = this.exportedPlasticWeight,
              convertedPackagingCredit = this.convertedPackagingCredit,
              metaData = this.metaData
    )

}

object TaxReturnRequest {

  import play.api.libs.json._

  implicit val format: OFormat[TaxReturnRequest] = Json.format[TaxReturnRequest]
}
