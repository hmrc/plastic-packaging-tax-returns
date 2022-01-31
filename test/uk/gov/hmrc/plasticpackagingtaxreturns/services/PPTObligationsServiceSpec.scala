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
  val today                      = LocalDate.now()

  def makeDataResponse(obligationDetail: ObligationDetail*): ObligationDataResponse =
    ObligationDataResponse(
      Seq(
        Obligation(identification =
                     Identification(incomeSourceType = "unused", referenceNumber = "unused", referenceType = "unused"),
                   obligationDetails = obligationDetail
        )
      )
    )

  def makeDetail(fromDate: LocalDate = today): ObligationDetail =
    ObligationDetail(status = ObligationStatus.OPEN,
                     inboundCorrespondenceFromDate = fromDate,
                     inboundCorrespondenceToDate = fromDate.plusMonths(1),
                     inboundCorrespondenceDateReceived = fromDate,
                     inboundCorrespondenceDueDate = fromDate.plusMonths(2),
                     periodKey = "#001"
    )

  val overdueObligation: ObligationDetail   = makeDetail(today.minusMonths(2))
  val dueObligation: ObligationDetail       = makeDetail(today.minusMonths(1).minusDays(1))
  val upcomingObligation: ObligationDetail  = makeDetail(today)
  val laterObligation: ObligationDetail = makeDetail(today.plusMonths(1))

  "get" must {

    "return no obligations" in {
      val obligationDataResponse: ObligationDataResponse = makeDataResponse()

      sut.get(obligationDataResponse).nextObligation mustBe None
    }

    "return nextObligation" when {
      "there is only one obligationDetail" in {
        val obligationDataResponse = makeDataResponse(upcomingObligation)

        sut.get(obligationDataResponse).nextObligation mustBe Some(upcomingObligation)
      }

      "there is an upcoming obligation and an overdue obligation" in {
        val obligationDataResponse = makeDataResponse(overdueObligation, upcomingObligation)

        sut.get(obligationDataResponse).nextObligation mustBe Some(upcomingObligation)
      }

      "there are two upcoming obligations" in {
        val obligationDataResponse = makeDataResponse(laterObligation, upcomingObligation)

        withClue("obligation should be the soonest") {
          sut.get(obligationDataResponse).nextObligation mustBe Some(upcomingObligation)
        }
      }

      "there are a bunch of obligations" in {

        val obligationDataResponse =
          makeDataResponse(laterObligation, upcomingObligation, overdueObligation)

        sut.get(obligationDataResponse).nextObligation mustBe Some(upcomingObligation)
      }

      "the due date is equal to today date" in {
        val obligationDataResponse = makeDataResponse(upcomingObligation)

        sut.get(obligationDataResponse).nextObligation mustBe Some(upcomingObligation)
      }
    }

    "return an oldest Overdue Obligation" when {

      "an obligation is overdue" in {
        val obligationDataResponse = makeDataResponse(overdueObligation)

        sut.get(obligationDataResponse).oldestOverdueObligation mustBe Some(overdueObligation)
      }

      "there is more than one" in {
        val veryOverdueObligation = makeDetail(today.minusMonths(5))
        val obligationDataResponse    = makeDataResponse(overdueObligation, veryOverdueObligation)

        sut.get(obligationDataResponse).oldestOverdueObligation mustBe Some(veryOverdueObligation)
      }
    }

    "return no oldestOverdueObligation" when {
      "there are no overdue obligations" in {
        val obligationDataResponse = makeDataResponse(upcomingObligation)

        sut.get(obligationDataResponse).oldestOverdueObligation mustBe None
      }

      "an obligation is today" in {
        val obligationDueToday     = makeDetail().copy(inboundCorrespondenceDueDate = LocalDate.now)
        val obligationDataResponse = makeDataResponse(obligationDueToday)

        sut.get(obligationDataResponse).oldestOverdueObligation mustBe None
      }
    }
    "return count of overdue obligations" when {
      "we have no obligations" in {
        val noObligationsResponse = makeDataResponse()

        sut.get(noObligationsResponse).overdueObligationCount mustBe 0
      }
      "we have one overdue obligation" in {
        val overdueObligationsResponse = makeDataResponse(overdueObligation)

        sut.get(overdueObligationsResponse).overdueObligationCount mustBe 1
      }
      "we have multiple overdue obligations" in {
        val overdueObligationsResponse = makeDataResponse(overdueObligation, overdueObligation, overdueObligation)

        sut.get(overdueObligationsResponse).overdueObligationCount mustBe 3
      }
      "we have both one due and one overdue obligations" in {
        val obligationDataResponse = makeDataResponse(overdueObligation, upcomingObligation)

        sut.get(obligationDataResponse).overdueObligationCount mustBe 1
      }
      "return whether there is Next Obligation Due" when {
        "we have no obligations" in {
          val noObligationsResponse = makeDataResponse()

          sut.get(noObligationsResponse).isNextObligationDue mustBe false
        }
        "we have one upcoming obligation that is not within due period" in {
          val obligationDataResponse = makeDataResponse(upcomingObligation)

          sut.get(obligationDataResponse).isNextObligationDue mustBe false
        }
        "we have one upcoming obligation that is within due period" in {
          val obligationDataResponse = makeDataResponse(dueObligation)

          sut.get(obligationDataResponse).isNextObligationDue mustBe true
        }
      }
      "return whether to display the Submit Returns Link" when {
        "there are 0 obligations" in {
          val obligationDataResponse = makeDataResponse()

          sut.get(obligationDataResponse).displaySubmitReturnsLink mustBe false
        }

        "there is 0 overdue and 0 within due period" in {
          val obligationDataResponse = makeDataResponse(upcomingObligation)

          sut.get(obligationDataResponse).displaySubmitReturnsLink mustBe false
        }
        "there is 0 overdue and 1 within due period" in {
          val obligationDataResponse = makeDataResponse(dueObligation)

          sut.get(obligationDataResponse).displaySubmitReturnsLink mustBe true
        }
        "there is 1 overdue and 0 within due period" in {
          val obligationDataResponse = makeDataResponse(overdueObligation)

          sut.get(obligationDataResponse).displaySubmitReturnsLink mustBe true
        }
        "there is 1 overdue and 1 within due period" in {
          val obligationDataResponse = makeDataResponse(overdueObligation, dueObligation)

          sut.get(obligationDataResponse).displaySubmitReturnsLink mustBe true
        }
      }
    }
  }
}
