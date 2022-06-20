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

package uk.gov.hmrc.plasticpackagingtaxreturns.models

import org.joda.time.{DateTime, DateTimeZone}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.ReturnType.ReturnType

case class TaxReturn(
  id: String,
  periodKey: String,
  returnType: ReturnType = ReturnType.NEW,
  manufacturedPlastic: Boolean,
  manufacturedPlasticWeight: ManufacturedPlasticWeight,
  importedPlastic: Boolean,
  importedPlasticWeight: ImportedPlasticWeight,
  exportedPlasticWeight: ExportedPlasticWeight,
  humanMedicinesPlasticWeight: HumanMedicinesPlasticWeight,
  recycledPlasticWeight: RecycledPlasticWeight,
  convertedPackagingCredit: ConvertedPackagingCredit,
  lastModifiedDateTime: DateTime
) {
  def updateLastModified(): TaxReturn = this.copy(lastModifiedDateTime = (DateTime.now(DateTimeZone.UTC)))
}

object TaxReturn {
  import play.api.libs.json._

  implicit val dateFormatDefault: Format[DateTime] = new Format[DateTime] {

    override def reads(json: JsValue): JsResult[DateTime] =
      JodaReads.DefaultJodaDateTimeReads.reads(json)

    override def writes(o: DateTime): JsValue = JodaWrites.JodaDateTimeNumberWrites.writes(o)
  }

  implicit val format: OFormat[TaxReturn] = Json.format[TaxReturn]
}
