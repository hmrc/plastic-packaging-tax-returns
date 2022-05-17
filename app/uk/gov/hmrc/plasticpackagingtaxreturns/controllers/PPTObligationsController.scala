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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers

import play.api.Logging
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.http.{HttpResponse, Upstream4xxResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ObligationsDataConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.ObligationEmptyDataResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{Obligation, ObligationDataResponse, ObligationStatus}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.Authenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.services.PPTObligationsService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}


@Singleton
class PPTObligationsController @Inject() (
  cc: ControllerComponents,
  authenticator: Authenticator,
  obligationsDataConnector: ObligationsDataConnector,
  obligationsService: PPTObligationsService,
  appConfig: AppConfig
)(implicit val executionContext: ExecutionContext)
    extends BackendController(cc) with Logging{

  private val internalServerError = InternalServerError("{}")

  // todo dedupe

  def getOpen(pptReference: String): Action[AnyContent] = {
    authenticator.authorisedAction(parse.default, pptReference) {
      implicit request =>
        obligationsDataConnector.get(pptReference, None, None, Some(ObligationStatus.OPEN)).map {
          case Left(Upstream4xxResponse(message, _, _, _)) => handleNotFoundResponse(message)
          case Left(_) => internalServerError
          case Right(obligationDataResponse) => createOpenResponse(obligationDataResponse)
        }
    }
  }

  val pptStartDate: Option[LocalDate] = Some(LocalDate.of(2022, 4, 1))

  def getFulfilled(pptReference: String): Action[AnyContent] = {
    authenticator.authorisedAction(parse.default, pptReference) {
      implicit request =>
        val t = obligationsDataConnector.get(pptReference, pptStartDate, Some(fulfilledToDate), Some(ObligationStatus.FULFILLED)).map {
          case Left(HttpResponse(_, body, _)) => {
            println(s"-1- => $body")
            createEmptyFulfilledResponse()
          }
          case Left(_) => internalServerError
          case Right(obligationDataResponse) => {
            println("-2-")
            createFulfilledResponse(obligationDataResponse)
          }
        }

        t.map(res => {
          println(s"##### ${res.body}")
          res
        })
    }
  }

  private def fulfilledToDate: LocalDate = {
    // If feature flag used by E2E test threads is set, then include test obligations that are in the future
    val today = LocalDate.now()
    if (appConfig.qaTestingInProgress)
      today.plusYears(1)
    else
      today
  }

  private def createFulfilledResponse(obligationDataResponse: ObligationDataResponse) = {
    obligationsService.constructPPTFulfilled(obligationDataResponse) match {
      case Left(error) =>
        logger.error(s"Error constructing Obligation response: $error.")
        internalServerError
      case Right(response) =>
        Ok(Json.toJson(response))
    }
  }

  private def createOpenResponse(obligationDataResponse: ObligationDataResponse) = {
    obligationsService.constructPPTObligations(obligationDataResponse) match {
      case Left(error) =>
        logger.error(s"Error constructing Obligation response: $error.")
        internalServerError
      case Right(response) =>
        Ok(Json.toJson(response))
    }
  }

  private def createEmptyFulfilledResponse() = {
    val emptyObligationList = ObligationDataResponse(Seq(Obligation(None, Seq())))
    createFulfilledResponse(emptyObligationList)
  }

  private def createEmptyOpenResponse() = {
    val emptyObligationList = ObligationDataResponse(Seq(Obligation(None, Seq())))
    createOpenResponse(emptyObligationList)
  }

  private def handleNotFoundResponse(message: String) = {
    val json = message.substring(message.indexOf("{"), message.lastIndexOf("}") + 1)

    Try(Json.parse(json).as[ObligationEmptyDataResponse]) match {
      case Success(_) => createEmptyOpenResponse()
      case Failure(_) => NotFound("{}")
    }
  }


}
