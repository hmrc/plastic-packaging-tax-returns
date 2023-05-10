package uk.gov.hmrc.plasticpackagingtaxreturns.controllers

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.Authenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.services.AvailableCreditDateRangesService

import javax.inject.Inject
import scala.concurrent.Future

class AvailableCreditDateRangesController @Inject()(
  availableService: AvailableCreditDateRangesService,
  authenticator: Authenticator,
  override val controllerComponents: ControllerComponents
) extends BaseController{

  def get(pptReference: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) { request =>

    val dates = availableService.get
    Future.successful(Ok(Json.toJson(dates)))
  }

}
