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

package uk.gov.hmrc.plasticpackagingtaxreturns.services

import com.google.inject.Inject
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.FinancialDataConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.FinancialDataResponse

import java.time.LocalDate
import scala.concurrent.Future

class FinancialDataService @Inject() (connector: FinancialDataConnector) {

  def getRaw(
    pptReference: String,
    fromDate: LocalDate,
    toDate: LocalDate,
    onlyOpenItems: Option[Boolean],
    includeLocks: Option[Boolean],
    calculateAccruedInterest: Option[Boolean],
    customerPaymentInformation: Option[Boolean],
    internalId: String
  )(implicit hc: HeaderCarrier): Future[Either[Int, FinancialDataResponse]] =
    connector
      .get(
        pptReference,
        Some(fromDate),
        Some(toDate),
        onlyOpenItems,
        includeLocks,
        calculateAccruedInterest,
        customerPaymentInformation,
        internalId
      )

  def getFinancials(pptReference: String, internalId: String)(implicit
    hc: HeaderCarrier
  ): Future[Either[Int, FinancialDataResponse]] =
    connector.get(
      pptReference = pptReference,
      fromDate = None,
      toDate = None,
      onlyOpenItems = Some(true),
      includeLocks = Some(true),
      calculateAccruedInterest = Some(true),
      customerPaymentInformation = None,
      internalId
    )

}
