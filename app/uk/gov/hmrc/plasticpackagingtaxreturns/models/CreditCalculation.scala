package uk.gov.hmrc.plasticpackagingtaxreturns.models

case class CreditCalculation(
  availableCreditInPounds: BigDecimal,
  totalRequestedCreditInPounds: BigDecimal,
  totalRequestedCreditInKilograms: Long,
  credit: Map[String, TaxablePlastic]
) {

}
