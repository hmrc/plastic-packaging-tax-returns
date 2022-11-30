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

package support

import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{Identification, Obligation, ObligationDataResponse, ObligationDetail, ObligationStatus}

import java.time.LocalDate

object ObligationSpecHelper {

  def createOneObligation(pptReference: String): ObligationDataResponse =
    ObligationDataResponse(obligations =
      Seq(
        Obligation(
          identification =
            Some(Identification(incomeSourceType = Some("ITR SA"), referenceNumber = pptReference, referenceType = "PPT")),
          obligationDetails = Seq(
            ObligationDetail(
              status = ObligationStatus.UNKNOWN,                       // Don't care about this here
              inboundCorrespondenceDateReceived = Some(LocalDate.MIN), // Don't care about this here
              inboundCorrespondenceFromDate = LocalDate.now(),
              inboundCorrespondenceToDate = LocalDate.MAX,
              inboundCorrespondenceDueDate = LocalDate.now().plusMonths(1),
              periodKey = "22C2"
            )
          )
        )
      )
    )
}
