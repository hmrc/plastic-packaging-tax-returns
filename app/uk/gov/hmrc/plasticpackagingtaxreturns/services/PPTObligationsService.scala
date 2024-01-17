/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.Logging
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{Obligation, ObligationDataResponse, ObligationDetail}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.PPTObligations
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EdgeOfSystem

import java.time.LocalDate
import javax.inject.Inject

class PPTObligationsService @Inject() (implicit edgeOfSystem: EdgeOfSystem) extends Logging {

  def constructPPTFulfilled(data: ObligationDataResponse): Either[String, Seq[ObligationDetail]] =
    data.obligations match {
      case Seq(obligation) => Right(obligation.obligationDetails)
      case obligations =>
        Left(s"Error constructing Obligations, expected 1 found ${obligations.size}")
    }

  def constructPPTObligations(data: ObligationDataResponse): Either[String, PPTObligations] =
    data.obligations match {
      case Seq(obligation) =>
        Right(construct(obligation))
      case obligations =>
        Left(s"Error constructing Obligations, expected 1 found ${obligations.size}")
    }

  private def construct(obligation: Obligation): PPTObligations = {
    val today: LocalDate = edgeOfSystem.today
    val nextObligation: Option[ObligationDetail] =
      obligation.obligationDetails.filter(_.inboundCorrespondenceDueDate.isEqualOrAfterToday).sortBy(_.inboundCorrespondenceDueDate).headOption

    val overdueObligations: Seq[ObligationDetail] =
      obligation.obligationDetails.filter(_.inboundCorrespondenceDueDate.isBeforeToday).sortBy(_.inboundCorrespondenceDueDate)

    val isNextObligationDue: Boolean = nextObligation.exists(_.inboundCorrespondenceToDate.isBefore(today))

    val displaySubmitReturnsLink: Boolean = overdueObligations.nonEmpty || isNextObligationDue

    PPTObligations(nextObligation, overdueObligations.headOption, overdueObligations.length, isNextObligationDue, displaySubmitReturnsLink)
  }

}
