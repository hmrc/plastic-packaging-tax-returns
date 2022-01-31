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

import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{
  Obligation,
  ObligationDataResponse,
  ObligationDetail
}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.PPTObligations

import java.time.LocalDate

class PPTObligationsService {

  implicit val localDateOrdering: Ordering[LocalDate] = Ordering.by(_.toEpochDay)

  def get(data: ObligationDataResponse): PPTObligations = {

    val obligation: Obligation = //todo: what if there is none OR more than one?
      if (data.obligations.length == 1) data.obligations.head else throw new Exception("Where is my only Obligation??")

    val nextOb: Option[ObligationDetail] =
      obligation.obligationDetails.filter( o =>
        isEqualOrAfterToday(o.inboundCorrespondenceDueDate)
      ).sortBy(_.inboundCorrespondenceDueDate).headOption

    val overdueObligation: Option[ObligationDetail] =
      obligation.obligationDetails.filter(
        _.inboundCorrespondenceDueDate.isBefore(LocalDate.now())
      ).sortBy(_.inboundCorrespondenceDueDate).headOption

    PPTObligations(nextOb, overdueObligation)
  }

  private def isEqualOrAfterToday(date: LocalDate) = {
    date.compareTo(LocalDate.now()) >= 0
  }
}
