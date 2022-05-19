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

import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results.{InternalServerError, Ok}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{Obligation, ObligationDataResponse, ObligationDetail}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.PPTObligations

import java.time.{LocalDate, ZoneOffset}
import javax.inject.Inject

class PPTObligationsService @Inject()(
   appConfig: AppConfig
  ) extends Logging {

  def constructPPTFulfilled(data: ObligationDataResponse): Either[String, Seq[ObligationDetail]] =
    data.obligations match {
      case Seq(obligation) => Right(obligation.obligationDetails)
      case obligations =>
        Left(s"Error constructing Obligations, expected 1 found ${obligations.size}")
  }

  def constructPPTObligations(data: ObligationDataResponse): Either[String, PPTObligations] =
    data.obligations match {
      case Seq(obligation) =>
        logger.info("Constructing Obligations.")
        Right(construct(obligation))
      case obligations =>
        Left(s"Error constructing Obligations, expected 1 found ${obligations.size}")
    }

  private def construct(obligation: Obligation): PPTObligations = {
    val today: LocalDate = LocalDate.now(ZoneOffset.UTC)
    val nextObligation: Option[ObligationDetail] =
      obligation.obligationDetails.filter(_.inboundCorrespondenceDueDate.isEqualOrAfterToday).sortBy(
        _.inboundCorrespondenceDueDate
      ).headOption

    val overdueObligations: Seq[ObligationDetail] =
      obligation.obligationDetails.filter(_.inboundCorrespondenceDueDate.isBeforeToday).sortBy(
        _.inboundCorrespondenceDueDate
      )

    val isNextObligationDue: Boolean = {
      appConfig.qaTestingInProgress ||
        nextObligation.exists(_.inboundCorrespondenceToDate.isBefore(today))
    }

    val displaySubmitReturnsLink: Boolean = overdueObligations.nonEmpty || isNextObligationDue

    PPTObligations(nextObligation,
                   overdueObligations.headOption,
                   overdueObligations.length,
                   isNextObligationDue,
                   displaySubmitReturnsLink
    )
  }

  def createFulfilledResponse(obligationDataResponse: ObligationDataResponse) =
    constructPPTFulfilled(obligationDataResponse) match {
      case Left(error) =>
        logger.error(s"Error constructing Obligation response: $error.")
        InternalServerError("{}")
      case Right(response) =>
        Ok(Json.toJson(response))
    }

  def createOpenResponse(obligationDataResponse: ObligationDataResponse) =
    constructPPTObligations(obligationDataResponse) match {
      case Left(error) =>
        logger.error(s"Error constructing Obligation response: $error.")
        InternalServerError("{}")
      case Right(response) =>
        Ok(Json.toJson(response))
    }

  def createEmptyFulfilledResponse: Result = {
    createFulfilledResponse(ObligationDataResponse.empty)
  }

  def createEmptyOpenResponse: Result = {
    createOpenResponse(ObligationDataResponse.empty)
  }
}
