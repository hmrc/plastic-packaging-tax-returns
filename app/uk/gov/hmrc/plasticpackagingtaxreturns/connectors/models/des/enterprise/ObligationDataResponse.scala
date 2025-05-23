/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise

import play.api.libs.json.{Format, JsPath, Json, OFormat, Reads, Writes}
import play.api.libs.functional.syntax.toFunctionalBuilderOps

import java.time.LocalDate
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.ObligationStatus.ObligationStatus

object ObligationStatus extends Enumeration {
  type ObligationStatus = Value
  val OPEN: Value      = Value("O")
  val FULFILLED: Value = Value("F")
  val UNKNOWN: Value   = Value("")

  implicit val format: Format[ObligationStatus] =
    Format(Reads.enumNameReads(ObligationStatus), Writes.enumNameWrites)

}

case class Identification(incomeSourceType: Option[String], referenceNumber: String, referenceType: String)

object Identification {
  implicit val format: OFormat[Identification] = Json.format[Identification]
}

case class ObligationDetail(
  status: ObligationStatus,
  inboundCorrespondenceFromDate: LocalDate,
  inboundCorrespondenceToDate: LocalDate,
  inboundCorrespondenceDateReceived: Option[LocalDate],
  inboundCorrespondenceDueDate: LocalDate,
  periodKey: String
)

object ObligationDetail {

  // to keep PPT APIs simple; filter information returned
  implicit val customWrites: Writes[ObligationDetail] = (
    (JsPath \ "periodKey").write[String] and
      (JsPath \ "fromDate").write[LocalDate] and
      (JsPath \ "toDate").write[LocalDate] and
      (JsPath \ "dueDate").write[LocalDate]
  )(o => (o.periodKey, o.inboundCorrespondenceFromDate, o.inboundCorrespondenceToDate, o.inboundCorrespondenceDueDate))

  implicit val format: Reads[ObligationDetail] = Json.reads[ObligationDetail]
}

case class Obligation(identification: Option[Identification], obligationDetails: Seq[ObligationDetail])

object Obligation {
  implicit val format: OFormat[Obligation] = Json.format[Obligation]
}

case class ObligationDataResponse(obligations: Seq[Obligation])

object ObligationDataResponse {

  def empty: ObligationDataResponse = ObligationDataResponse(Seq(Obligation(None, Seq())))

  implicit val format: OFormat[ObligationDataResponse] =
    Json.format[ObligationDataResponse]

}
