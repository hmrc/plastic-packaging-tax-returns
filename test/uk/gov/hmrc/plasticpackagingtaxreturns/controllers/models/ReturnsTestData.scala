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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models

import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise._
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.exportcreditbalance.ExportCreditBalanceDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns._
import uk.gov.hmrc.plasticpackagingtaxreturns.models.ReturnType
import uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation.NrsDetails

import java.time.{LocalDate, LocalDateTime}

trait ReturnsTestData {

  val pptReference: String    = "XMPPT0000000123"
  val nrsSubmissionId: String = "someid"
  private val date: String    = "2020-12-17T09:30:47Z"

  private type ReturnsResponseModifier = Return => Return

  val aReturnWithReturnDetails =
    Return(processingDate = LocalDate.now().toString,
      idDetails = IdDetails(pptReferenceNumber = pptReference, submissionId = "1234567890XX"),
      chargeDetails = Some(
        ChargeDetails(chargeType = "Plastic Tax",
          chargeReference = "ABC123",
          amount = 1234.56,
          dueDate = LocalDate.now().plusDays(30).toString
        )
      ),
      exportChargeDetails = None,
      returnDetails = Some(
        EisReturnDetails(manufacturedWeight = BigDecimal(256.12),
          importedWeight = BigDecimal(352.15),
          totalNotLiable = BigDecimal(546.42),
          humanMedicines = BigDecimal(1234.15),
          directExports = BigDecimal(12121.16),
          recycledPlastic = BigDecimal(4345.72),
          creditForPeriod =
            BigDecimal(1560000.12),
          totalWeight = BigDecimal(16466.88),
          taxDue = BigDecimal(4600)
        )
      )
    )

  val nrsDetailsSuccess = NrsDetails(Some(nrsSubmissionId), None)
  val nrsDetailsFailure = NrsDetails(None, Some("Error"))

  val exportCreditBalanceDisplayResponse = ExportCreditBalanceDisplayResponse(processingDate = "2021-11-17T09:32:50.345Z",
    totalPPTCharges = BigDecimal(1000),
    totalExportCreditClaimed = BigDecimal(100),
    totalExportCreditAvailable = BigDecimal(200)
  )

  val obligation = Obligation(
    identification =
      Some(Identification(incomeSourceType = Some("unused"), referenceNumber = "unused", referenceType = "unused")),
    obligationDetails = Nil
  )
  val obligationDataResponse: ObligationDataResponse =
    ObligationDataResponse(Seq(obligation, obligation))

  val financials = FinancialDataResponse(idType = Some("idType"),
    idNumber = Some("idNumber"),
    regimeType = Some("regimeType"),
    processingDate = LocalDateTime.now(),
    financialTransactions = Seq(
      FinancialTransaction(
        chargeType = Some("chargeType"),
        mainType = Some("mainType"),
        periodKey = Some("periodKey"),
        periodKeyDescription =
          Some("periodKeyDescription"),
        taxPeriodFrom = Some(LocalDate.now()),
        taxPeriodTo = Some(LocalDate.now()),
        outstandingAmount =
          Some(BigDecimal(1000)),
        items = Seq(
          FinancialItem(
            subItem = Some("subItem"),
            dueDate =
              Some(LocalDate.now()),
            amount =
              Some(BigDecimal(1000)),
            clearingDate =
              Some(LocalDate.now()),
            clearingReason =
              Some("clearingReason")
          )
        )
      )
    )
  )

  def aReturn(modifiers: ReturnsResponseModifier*): Return =
    modifiers.foldLeft(modelWithDefaults)((current, modifier) => modifier(current))

  private val modelWithDefaults = Return(processingDate = date,
    idDetails = IdDetails(pptReferenceNumber =
      "7777777",
      submissionId = "1234567890AA"
    ),
    chargeDetails = None,
    exportChargeDetails = None,
    returnDetails = None
  )

  val aReturnsSubmissionRequest =
    ReturnsSubmissionRequest(returnType = ReturnType.NEW,
      submissionId = None,
      periodKey = "AA22",
      returnDetails = EisReturnDetails(manufacturedWeight = 12000,
        importedWeight = 1000,
        totalNotLiable = 2000,
        humanMedicines = 3000,
        directExports = 4000,
        recycledPlastic = 5000,
        creditForPeriod = 10000,
        totalWeight = 20000,
        taxDue = 90000
      )
    )

  val anAmendsSubmissionRequest =
    ReturnsSubmissionRequest(returnType = ReturnType.AMEND,
      submissionId = Some("someId"),
      periodKey = "AA22",
      returnDetails = EisReturnDetails(manufacturedWeight = 12000,
        importedWeight = 1000,
        totalNotLiable = 2000,
        humanMedicines = 3000,
        directExports = 4000,
        recycledPlastic = 5000,
        creditForPeriod = 10000,
        totalWeight = 20000,
        taxDue = 90000
      )
    )
}
