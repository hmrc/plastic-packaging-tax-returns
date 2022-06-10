package uk.gov.hmrc.plasticpackagingtaxreturns.audit

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.{EisReturnDetails, ReturnsSubmissionRequest}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.ReturnType.ReturnType
import uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation.NrsDetails

import java.time.ZonedDateTime

case class SubmitReturnEvent(
                              returnType: ReturnType,
                              submissionId: Option[String] = None,
                              periodKey: String,
                              returnDetails: EisReturnDetails,
                              pptReference: Option[String],
                              processingDateTime: Option[ZonedDateTime],
                              nrsDetails: Option[NrsDetails] = None
                            )

object SubmitReturnEvent {
  implicit val format: OFormat[SubmitReturnEvent] = Json.format[SubmitReturnEvent]
  val eventType: String = "submitPPTReturn"

  def apply(
             submission: ReturnsSubmissionRequest,
             pptReference: Option[String],
             processingDateTime: Option[ZonedDateTime]
           ): SubmitReturnEvent =
    SubmitReturnEvent(
      submission.returnType,
      submission.submissionId,
      submission.periodKey,
      submission.returnDetails,
      pptReference,
      processingDateTime,
      submission.nrsDetails
    )
}
