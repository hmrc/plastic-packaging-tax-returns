/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.{EisReturnDetails, ReturnsSubmissionRequest}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.ReturnType.ReturnType
import uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation.NrsDetails

import java.time.ZonedDateTime

case class NrsSubmitReturnEvent(
  returnType: ReturnType,
  submissionId: Option[String] = None,
  periodKey: String,
  returnDetails: EisReturnDetails,
  pptReference: Option[String],
  processingDateTime: Option[ZonedDateTime],
  nrsDetails: Option[NrsDetails] = None
)

object NrsSubmitReturnEvent {
  implicit val format: OFormat[NrsSubmitReturnEvent] = Json.format[NrsSubmitReturnEvent]
  val eventType: String                              = "nrsSubmitOrAmendReturn"

  def apply(
    submission: ReturnsSubmissionRequest,
    pptReference: Option[String],
    processingDateTime: Option[ZonedDateTime]
  ): NrsSubmitReturnEvent =
    NrsSubmitReturnEvent(
      submission.returnType,
      submission.submissionId,
      submission.periodKey,
      submission.returnDetails,
      pptReference,
      processingDateTime,
      submission.nrsDetails
    )

}
