/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.builders

import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.{EisReturnDetails, IdDetails, Return}

import java.time.LocalDate

trait ReturnsSubmissionResponseBuilder {

  private type ReturnsResponseModifier = Return => Return

  def aReturn(modifiers: ReturnsResponseModifier*): Return =
    modifiers.foldLeft(modelWithDefaults)((current, modifier) => modifier(current))

  private val modelWithDefaults = Return(processingDate = LocalDate.now().toString,
                                         idDetails = IdDetails(pptReferenceNumber =
                                                                 "XMPPT0000000123",
                                                               submissionId = "1234567890AA"
                                         ),
                                         chargeDetails = None,
                                         exportChargeDetails = None,
                                         returnDetails = None
  )

  def withReturnDetails(returnDetails: Option[EisReturnDetails]): ReturnsResponseModifier =
    _.copy(returnDetails = returnDetails)

}
