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
  val sut: PPTObligationsService = new PPTObligationsService()

  def makeDataResponse(obligationDetail: ObligationDetail*): ObligationDataResponse =
    ObligationDataResponse(
      Seq(
        Obligation(identification =
                     Identification(incomeSourceType = "unused", referenceNumber = "unused", referenceType = "unused"),
                   obligationDetails = obligationDetail
        )
      )
    )

  def makeDetail(fromDate: LocalDate = LocalDate.now): ObligationDetail =
    ObligationDetail(status = ObligationStatus.OPEN,
                     inboundCorrespondenceFromDate = fromDate,
                     inboundCorrespondenceToDate = fromDate.plusMonths(1),
                     inboundCorrespondenceDateReceived = fromDate,
                     inboundCorrespondenceDueDate = fromDate.plusMonths(1).minusDays(1),
                     periodKey = "#001"
    )

  "get" must {

    "return no obligations" in {

      val obligationDataResponse: ObligationDataResponse = makeDataResponse()

      sut.get(obligationDataResponse).nextObligation mustBe None

    }
    "return nextObligation" when {
      "there is only one obligationDetail" in {

        val upcomingObligationDate = makeDetail()
        val obligationDataResponse = makeDataResponse(upcomingObligationDate)

        sut.get(obligationDataResponse).nextObligation mustBe Some(upcomingObligationDate)

      }

      "there is an upcoming obligation and an overdue obligation" in {

        val upcomingObligationDate = makeDetail()
        val overdueObligationDate  = makeDetail(LocalDate.now().minusMonths(1))
        val obligationDataResponse = makeDataResponse(overdueObligationDate, upcomingObligationDate)

        sut.get(obligationDataResponse).nextObligation mustBe Some(upcomingObligationDate)
      }


      "there are two upcoming obligations" in {

        val upcomingObligationDate = makeDetail()
        val laterObligationDate    = makeDetail(LocalDate.now().plusMonths(1))
        val obligationDataResponse = makeDataResponse(laterObligationDate, upcomingObligationDate)

        withClue("obligation should be the soonest") {
          sut.get(obligationDataResponse).nextObligation mustBe Some(upcomingObligationDate)
        }
      }

      "there are a bunch of obligations" in {
        val upcomingObligationDate = makeDetail()
        val overdueObligationDate  = makeDetail(LocalDate.now().minusMonths(1))
        val laterObligationDate    = makeDetail(LocalDate.now().plusMonths(1))

        val obligationDataResponse =
          makeDataResponse(laterObligationDate, upcomingObligationDate, overdueObligationDate)

        sut.get(obligationDataResponse).nextObligation mustBe Some(upcomingObligationDate)
      }

      "the due date is equal to today date" in {
        val upcomingObligationDate = makeDetail().copy(inboundCorrespondenceDueDate = LocalDate.now())
        val obligationDataResponse = makeDataResponse(upcomingObligationDate)

        sut.get(obligationDataResponse).nextObligation mustBe Some(upcomingObligationDate)
      }
    }

    "return an oldest Overdue Obligation" when {

      "an obligation is overdue" in {

        val overdueObligationDate = makeDetail(LocalDate.now().minusMonths(1))
        val obligationDataResponse = makeDataResponse(overdueObligationDate)

        sut.get(obligationDataResponse).oldestOverdueObligation mustBe Some(overdueObligationDate)
      }

      "there is more than one" in {
        val overdueObligationDate = makeDetail(LocalDate.now().minusMonths(1))
        val overdueObligationDate1 = makeDetail(LocalDate.now().minusMonths(2))
        val obligationDataResponse = makeDataResponse(overdueObligationDate, overdueObligationDate1)

        sut.get(obligationDataResponse).oldestOverdueObligation mustBe Some(overdueObligationDate1)
      }
    }

    "return no oldestOverdueObligation" when {
      "there are no overdue obligation" in {
        val upcomingObligationDate = makeDetail()
        val obligationDataResponse = makeDataResponse(upcomingObligationDate)

        sut.get(obligationDataResponse).oldestOverdueObligation mustBe None
      }

      "an obligation is today" in {
        val obligationDueToday = makeDetail().copy(inboundCorrespondenceDueDate = LocalDate.now)
        val obligationDataResponse = makeDataResponse(obligationDueToday)

        sut.get(obligationDataResponse).oldestOverdueObligation mustBe None
      }
    }
  }
}
