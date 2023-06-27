package uk.gov.hmrc.plasticpackagingtaxreturns.services

import play.api.mvc.Result
import play.api.mvc.Results.UnprocessableEntity
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.UserAnswersService.notFoundMsg

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}


class UserAnswersService @Inject()(
  sessionRepository: SessionRepository
)(implicit ce: ExecutionContext) {

  def get(cachekey: String): Future[Either[Result, UserAnswers]] = {

    sessionRepository.get(cachekey).map(userAnswer => {
      userAnswer match {
        case Some(answer) => Right(answer)
        case _ => Left(UnprocessableEntity(notFoundMsg))
      }
    })
  }
}

object UserAnswersService {
  val notFoundMsg = "No user answers found"
}
