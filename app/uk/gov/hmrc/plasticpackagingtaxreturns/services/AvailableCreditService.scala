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

package uk.gov.hmrc.plasticpackagingtaxreturns.services

import com.google.inject.Inject
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ExportCreditBalanceConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.AuthorizedRequest
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.returns.ReturnObligationFromDateGettable
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendHeaderCarrierProvider

import scala.concurrent.{ExecutionContext, Future}

class AvailableCreditService @Inject()(
  exportCreditBalanceConnector: ExportCreditBalanceConnector
)(implicit executionContext: ExecutionContext) extends BackendHeaderCarrierProvider {

  def getBalance(userAnswers: UserAnswers)(implicit request: AuthorizedRequest[_]): Future[BigDecimal] = {
    val obligationFromDate = userAnswers.get(ReturnObligationFromDateGettable).getOrElse(
      throw new IllegalStateException("Obligation fromDate not found in user-answers")
    )

    val fromDate = obligationFromDate.minusYears(2)
    val toDate = obligationFromDate.minusDays(1)

    exportCreditBalanceConnector
      .getBalance(request.pptId, fromDate, toDate, request.internalId)
      .map(
        _.fold(e => throw new Exception(s"Error calling EIS export credit, status: $e"), _.totalExportCreditAvailable)
      )
  }

}
