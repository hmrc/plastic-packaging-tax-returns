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

import org.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, EitherValues}
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise._
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EdgeOfSystem

import java.time.{LocalDate, LocalDateTime}

class PPTObligationsServiceSpec extends PlaySpec with MockitoSugar with EitherValues with BeforeAndAfterEach {

  private val today: LocalDate                     = LocalDate.now()
  private val overdueObligation: ObligationDetail  = makeDetail(today.minusDays(20), "overdue")
  private val dueObligation: ObligationDetail      = makeDetail(today.minusDays(11), "due")
  private val upcomingObligation: ObligationDetail = makeDetail(today, "upcoming")
  private val laterObligation: ObligationDetail    = makeDetail(today.plusDays(10), "later")
  private val edgeOfSystem                         = mock[EdgeOfSystem]
  private val sut                                  = new PPTObligationsService()(edgeOfSystem)

  def makeDataResponse(obligationDetail: ObligationDetail*): ObligationDataResponse =
    ObligationDataResponse(
      Seq(
        Obligation(
          identification =
            Some(Identification(
              incomeSourceType = Some("unused"),
              referenceNumber = "unused",
              referenceType = "unused"
            )),
          obligationDetails = obligationDetail
        )
      )
    )

  def makeDetail(fromDate: LocalDate = today, periodKey: String = "#001"): ObligationDetail =
    ObligationDetail(
      status = ObligationStatus.OPEN,
      inboundCorrespondenceFromDate = fromDate,
      inboundCorrespondenceToDate = fromDate.plusDays(10),
      inboundCorrespondenceDateReceived = Some(fromDate),
      inboundCorrespondenceDueDate = fromDate.plusDays(19),
      periodKey = periodKey
    )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(edgeOfSystem)

    when(edgeOfSystem.localDateTimeNow) thenReturn LocalDateTime.now()
    when(edgeOfSystem.today) thenReturn LocalDate.now()
  }

  "constructPPTFulfilled" must {
    "return an error message" when {

      "there are no obligations in data" in {
        val obligationDataResponse: ObligationDataResponse = ObligationDataResponse(Seq.empty)
        sut.constructPPTFulfilled(obligationDataResponse) mustBe Left(
          "Error constructing Obligations, expected 1 found 0"
        )
      }

      "there are multiple obligations in data" in {
        val obligation = Obligation(
          identification =
            Some(Identification(
              incomeSourceType = Some("unused"),
              referenceNumber = "unused",
              referenceType = "unused"
            )),
          obligationDetails = Nil
        )
        val obligationDataResponse: ObligationDataResponse =
          ObligationDataResponse(Seq(obligation, obligation))

        sut.constructPPTFulfilled(obligationDataResponse) mustBe Left(
          "Error constructing Obligations, expected 1 found 2"
        )
      }
    }

    "return a sequence of details" when {
      "the sequence is empty" in {
        val obligation = Obligation(
          identification =
            Some(Identification(
              incomeSourceType = Some("unused"),
              referenceNumber = "unused",
              referenceType = "unused"
            )),
          obligationDetails = Nil
        )
        val obligationDataResponse: ObligationDataResponse =
          ObligationDataResponse(Seq(obligation))

        sut.constructPPTFulfilled(obligationDataResponse) mustBe Right(Seq.empty)
      }

      "the sequence is non empty" in {
        val obligationDetail = ObligationDetail(ObligationStatus.FULFILLED, today, today, Some(today), today, "PKEY")
        val obligation = Obligation(
          identification =
            Some(Identification(
              incomeSourceType = Some("unused"),
              referenceNumber = "unused",
              referenceType = "unused"
            )),
          obligationDetails = Seq(obligationDetail)
        )
        val obligationDataResponse: ObligationDataResponse =
          ObligationDataResponse(Seq(obligation))

        sut.constructPPTFulfilled(obligationDataResponse) mustBe Right(Seq(obligationDetail))
      }
    }
  }

  "constructPPTObligations" must {

    "returns an error message" when {
      "there are no obligations in data" in {
        val obligationDataResponse: ObligationDataResponse = ObligationDataResponse(Seq.empty)

        sut.constructPPTObligations(obligationDataResponse) mustBe Left(
          "Error constructing Obligations, expected 1 found 0"
        )
      }

      "there are multiple obligations in data" in {
        val obligation = Obligation(
          identification =
            Some(Identification(
              incomeSourceType = Some("unused"),
              referenceNumber = "unused",
              referenceType = "unused"
            )),
          obligationDetails = Nil
        )
        val obligationDataResponse: ObligationDataResponse =
          ObligationDataResponse(Seq(obligation, obligation))

        sut.constructPPTObligations(obligationDataResponse) mustBe Left(
          "Error constructing Obligations, expected 1 found 2"
        )
      }
    }

    "return no obligations" in {
      val obligationDataResponse: ObligationDataResponse = makeDataResponse()
      sut.constructPPTObligations(obligationDataResponse).value.nextObligation mustBe None
    }

    "return nextObligation" when {
      "there is only one obligationDetail and it is not overdue" in {
        val obligationDataResponse = makeDataResponse(upcomingObligation)
        sut.constructPPTObligations(obligationDataResponse).value.nextObligation mustBe Some(upcomingObligation)
      }

      "there is an upcoming obligation and an overdue obligation" in {
        val obligationDataResponse = makeDataResponse(overdueObligation, upcomingObligation)
        sut.constructPPTObligations(obligationDataResponse).value.nextObligation mustBe Some(upcomingObligation)
      }

      "there are two upcoming obligations" in {
        val obligationDataResponse = makeDataResponse(laterObligation, upcomingObligation)
        withClue("obligation should be the soonest") {
          sut.constructPPTObligations(obligationDataResponse).value.nextObligation mustBe Some(upcomingObligation)
        }
      }

      "there are two upcoming and one overdue" in {
        val obligationDataResponse =
          makeDataResponse(laterObligation, upcomingObligation, overdueObligation)
        sut.constructPPTObligations(obligationDataResponse).value.nextObligation mustBe Some(upcomingObligation)
      }

      "the due date is equal to today date" in {
        val obligationDataResponse = makeDataResponse(upcomingObligation)
        sut.constructPPTObligations(obligationDataResponse).value.nextObligation mustBe Some(upcomingObligation)
      }
    }

    "return an oldest Overdue Obligation" when {

      "an obligation is overdue" in {
        val obligationDataResponse = makeDataResponse(overdueObligation)
        sut.constructPPTObligations(obligationDataResponse).value.oldestOverdueObligation mustBe Some(overdueObligation)
      }

      "there is more than one overdue" in {
        val veryOverdueObligation  = makeDetail(today.minusDays(50), "very overdue")
        val obligationDataResponse = makeDataResponse(overdueObligation, veryOverdueObligation)
        sut.constructPPTObligations(obligationDataResponse).value.oldestOverdueObligation mustBe Some(
          veryOverdueObligation
        )
      }
    }

    "return no oldestOverdueObligation" when {
      "there are no overdue obligations" in {
        val obligationDataResponse = makeDataResponse(upcomingObligation)
        sut.constructPPTObligations(obligationDataResponse).value.oldestOverdueObligation mustBe None
      }

      "an obligation is due today" in {
        val obligationDueToday     = makeDetail().copy(inboundCorrespondenceDueDate = LocalDate.now)
        val obligationDataResponse = makeDataResponse(obligationDueToday)
        sut.constructPPTObligations(obligationDataResponse).value.oldestOverdueObligation mustBe None
      }
    }

    "return count of overdue obligations" when {
      "we have no obligations" in {
        val noObligationsResponse = makeDataResponse()
        sut.constructPPTObligations(noObligationsResponse).value.overdueObligationCount mustBe 0
      }

      "we have one overdue obligation" in {
        val overdueObligationsResponse = makeDataResponse(overdueObligation)
        sut.constructPPTObligations(overdueObligationsResponse).value.overdueObligationCount mustBe 1
      }

      "we have multiple overdue obligations" in {
        val veryOverdueObligation: ObligationDetail     = makeDetail(today.minusDays(120), "very-overdue")
        val tardiestOverdueObligation: ObligationDetail = makeDetail(today.minusDays(220), "tardiest-overdue")
        val overdueObligationsResponse =
          makeDataResponse(overdueObligation, veryOverdueObligation, tardiestOverdueObligation)

        sut.constructPPTObligations(overdueObligationsResponse).value.overdueObligationCount mustBe 3
      }

      "we have both one due and one overdue obligations" in {
        val obligationDataResponse = makeDataResponse(overdueObligation, upcomingObligation)
        sut.constructPPTObligations(obligationDataResponse).value.overdueObligationCount mustBe 1
      }

      "return whether there is Next Obligation Due" when {
        "we have no obligations" in {
          val noObligationsResponse = makeDataResponse()
          sut.constructPPTObligations(noObligationsResponse).value.isNextObligationDue mustBe false
        }

        "we have one upcoming obligation that is not within due period" in {
          val obligationDataResponse = makeDataResponse(upcomingObligation)
          sut.constructPPTObligations(obligationDataResponse).value.isNextObligationDue mustBe false
        }

        "we have one upcoming obligation that is within due period" in {
          val obligationDataResponse = makeDataResponse(dueObligation)
          sut.constructPPTObligations(obligationDataResponse).value.isNextObligationDue mustBe true
        }
      }

      "return whether to display the Submit Returns Link" when {

        "there are 0 obligations" in {
          val obligationDataResponse = makeDataResponse()
          sut.constructPPTObligations(obligationDataResponse).value.displaySubmitReturnsLink mustBe false
        }

        "there is an obligation but it is not yet due" in {
          val obligationDataResponse = makeDataResponse(upcomingObligation)

          sut.constructPPTObligations(obligationDataResponse).value.displaySubmitReturnsLink mustBe false
        }

        "there is 0 overdue and 1 within due period" in {
          val obligationDataResponse = makeDataResponse(dueObligation)
          sut.constructPPTObligations(obligationDataResponse).value.displaySubmitReturnsLink mustBe true
        }

        "there is 1 overdue and 0 within due period" in {
          val obligationDataResponse = makeDataResponse(overdueObligation)
          sut.constructPPTObligations(obligationDataResponse).value.displaySubmitReturnsLink mustBe true
        }

        "there is 1 overdue and 1 within due period" in {
          val obligationDataResponse = makeDataResponse(overdueObligation, dueObligation)
          sut.constructPPTObligations(obligationDataResponse).value.displaySubmitReturnsLink mustBe true
        }
      }

    }
  }
}
