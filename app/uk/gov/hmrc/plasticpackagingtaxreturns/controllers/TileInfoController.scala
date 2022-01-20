package uk.gov.hmrc.plasticpackagingtaxreturns.controllers

import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import javax.inject.Inject

class TileInfoController @Inject() (cc: ControllerComponents) extends BackendController(cc){


  def get(): Action[AnyContent] = Action {
    (Ok("str"))
  }

}
