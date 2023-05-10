package uk.gov.hmrc.plasticpackagingtaxreturns.services

import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.CreditRangeOption

import java.time.LocalDate

class AvailableCreditDateRangesService {
  //todo: actually do something

  def get = Seq(
    CreditRangeOption(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 3, 31)),
    CreditRangeOption(LocalDate.of(2023, 4, 1), LocalDate.of(2024, 3, 31)),
  )

}
