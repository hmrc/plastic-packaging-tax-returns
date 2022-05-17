package uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models

import play.api.libs.json.{Json, OFormat}

final case class ObligationEmptyDataResponse private(code: String, reason: String)

object ObligationEmptyDataResponse {
  private def apply(code: String, reason: String): ObligationEmptyDataResponse = {
    new ObligationEmptyDataResponse("NOT_FOUND", "What eber")
  }

  def apply(): ObligationEmptyDataResponse = {
    new ObligationEmptyDataResponse("NOT_FOUND", "What eber")
  }

  implicit val format: OFormat[ObligationEmptyDataResponse] =
    Json.format[ObligationEmptyDataResponse]
}
