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

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ObligationDataConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{ObligationDataResponse, ObligationStatus}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.PPTObligations
import uk.gov.hmrc.plasticpackagingtaxreturns.services.PTPObligationsService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class TileInfoController @Inject()(
  cc: ControllerComponents,
  obligationDataConnector: ObligationDataConnector,
  obligationsService: PTPObligationsService
)(implicit val executionContext: ExecutionContext)
extends BackendController(cc) {

  def get(ref: String): Action[AnyContent] = Action.async {
    implicit val hc: HeaderCarrier =  HeaderCarrier()

    val data: Future[Either[Int, ObligationDataResponse]] = obligationDataConnector.get(
      ref,
      LocalDate.of(2022, 4,1),
      LocalDate.now(),
      ObligationStatus.OPEN
    )

    data.map {
        case Left(value) => ???
        case Right(value) =>
          Ok(Json.toJson(obligationsService.get(value)))
    }
  }


}
