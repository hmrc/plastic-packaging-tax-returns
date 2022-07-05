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

//TODO: Split out what is a tax return and what is user info re: tax return
//TODO: OR-> Extract userAnswers in the backend so they aren't sent from Frontend (Pan's preferred)
@deprecated
case class TaxReturn(
  id: String,
  periodKey: String,
  returnType: ReturnType,
  manufacturedPlastic: Option[Boolean],
  manufacturedPlasticWeight: ManufacturedPlasticWeight,
  importedPlastic: Option[Boolean],
  importedPlasticWeight: ImportedPlasticWeight,
  exportedPlastic: Option[Boolean],
  exportedPlasticWeight: ExportedPlasticWeight,
  humanMedicinesPlastic: Option[Boolean],
  humanMedicinesPlasticWeight: HumanMedicinesPlasticWeight,
  recycledPlastic: Option[Boolean],
  recycledPlasticWeight: RecycledPlasticWeight,
  convertedPackagingCredit: ConvertedPackagingCredit,
  lastModifiedDateTime: Option[DateTime]
) {
  def updateLastModified(): TaxReturn = this.copy(lastModifiedDateTime = Some(DateTime.now(DateTimeZone.UTC)))
}

@deprecated
object TaxReturn {
  import play.api.libs.json._

  implicit val dateFormatDefault: Format[DateTime] = new Format[DateTime] {

    override def reads(json: JsValue): JsResult[DateTime] =
      JodaReads.DefaultJodaDateTimeReads.reads(json)

    override def writes(o: DateTime): JsValue = JodaWrites.JodaDateTimeNumberWrites.writes(o)
  }

  implicit val format: OFormat[TaxReturn] = Json.format[TaxReturn]
}
