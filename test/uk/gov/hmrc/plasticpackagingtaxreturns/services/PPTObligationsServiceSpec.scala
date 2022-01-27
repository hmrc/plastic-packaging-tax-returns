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

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{
  Identification,
  Obligation,
  ObligationDataResponse,
  ObligationDetail,
  ObligationStatus
}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.PPTObligations

import java.time.LocalDate

class PPTObligationsServiceSpec extends PlaySpec {

  "get" must {


    "return no obligations" in {
      val obligationService: PPTObligationsService       = new PPTObligationsService()
      val obligationDataResponse: ObligationDataResponse = ObligationDataResponse(Seq())

      obligationService.get(obligationDataResponse) mustBe PPTObligations(None)

    }
    "return one obligations" in {
      val obligationService: PPTObligationsService = new PPTObligationsService()
      val obligationDataResponse: ObligationDataResponse = ObligationDataResponse(
        Seq(
          Obligation(
            identification =
              Identification(incomeSourceType = "unused", referenceNumber = "unused", referenceType = "unused"),
            obligationDetails = Seq(
              ObligationDetail(status = ObligationStatus.OPEN,
                               inboundCorrespondenceFromDate = LocalDate.parse("2021-10-01"),
                               inboundCorrespondenceToDate = LocalDate.parse("2021-11-01"),
                               inboundCorrespondenceDateReceived = LocalDate.parse("2021-10-01"),
                               inboundCorrespondenceDueDate = LocalDate.parse("2021-10-31"),
                               periodKey = "#001"
              )
            )
          )
        )
      )

     // obligationService.get(obligationDataResponse) mustBe PPTObligations(Some(Obligation))

    }
  }
}
