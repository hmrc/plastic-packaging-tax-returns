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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.nonRepudiation.{
  NrsFailureResponse,
  NrsSuccessfulResponse
}

case class IdDetails(pptReferenceNumber: String, submissionId: String)

object IdDetails {
  implicit val format: OFormat[IdDetails] = Json.format[IdDetails]
}

case class ChargeDetails(chargeType: String, chargeReference: String, amount: BigDecimal, dueDate: String)

object ChargeDetails {
  implicit val format: OFormat[ChargeDetails] = Json.format[ChargeDetails]
}

case class ExportChargeDetails(
  chargeType: String,
  chargeReference: Option[String],
  amount: BigDecimal,
  dueDate: Option[String]
)

object ExportChargeDetails {
  implicit val format: OFormat[ExportChargeDetails] = Json.format[ExportChargeDetails]
}

case class Return(
  processingDate: String,
  idDetails: IdDetails,
  chargeDetails: Option[ChargeDetails],
  exportChargeDetails: Option[ExportChargeDetails],
  returnDetails: Option[EisReturnDetails]
)

object Return {
  implicit val format: OFormat[Return] = Json.format[Return]
}

case class ReturnWithNrsFailureResponse(
  processingDate: String,
  idDetails: IdDetails,
  chargeDetails: Option[ChargeDetails],
  exportChargeDetails: Option[ExportChargeDetails],
  returnDetails: Option[EisReturnDetails],
  override val nrsFailureReason: String
) extends NrsFailureResponse

object ReturnWithNrsFailureResponse {
  implicit val format: OFormat[ReturnWithNrsFailureResponse] = Json.format[ReturnWithNrsFailureResponse]
}

case class ReturnWithNrsSuccessResponse(
  processingDate: String,
  idDetails: IdDetails,
  chargeDetails: Option[ChargeDetails],
  exportChargeDetails: Option[ExportChargeDetails],
  returnDetails: Option[EisReturnDetails],
  override val nrSubmissionId: String
) extends NrsSuccessfulResponse

object ReturnWithNrsSuccessResponse {
  implicit val format: OFormat[ReturnWithNrsSuccessResponse] = Json.format[ReturnWithNrsSuccessResponse]
}
