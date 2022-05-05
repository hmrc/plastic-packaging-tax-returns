package uk.gov.hmrc.plasticpackagingtaxreturns.controllers

import play.api.Logging
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.Authenticator
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.Future

@Singleton
class DirectDebitsController @Inject()
  (cc: ControllerComponents, authenticator: Authenticator)
  ()
  extends BackendController(cc) with Logging {

  def get(pptReference: String): Action[AnyContent] = {
      authenticator.authorisedAction(parse.default, pptReference) {
        implicit request => authenticatedGet(pptReference)
    }
  }

  def authenticatedGet(pptReference: String): Future[Result] = {
    displayDirectDebitConnector.get(pptReference).map {

    }
  }

}
