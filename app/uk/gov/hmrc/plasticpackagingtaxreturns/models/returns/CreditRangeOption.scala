package uk.gov.hmrc.plasticpackagingtaxreturns.models.returns

import play.api.libs.json.{Json, OFormat}

import java.time.LocalDate

final case class CreditRangeOption(from: LocalDate, to: LocalDate)

object CreditRangeOption {
  implicit val format: OFormat[CreditRangeOption] = Json.format[CreditRangeOption]

}
