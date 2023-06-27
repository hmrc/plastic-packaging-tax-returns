package uk.gov.hmrc.plasticpackagingtaxreturns.services

import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.MockitoSugar.{mock, times, verify, when}
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.Futures.whenReady
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatestplus.play.PlaySpec
import play.api.mvc.Results.UnprocessableEntity
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.UserAnswersService.notFoundMsg

import scala.concurrent.{ExecutionContext, Future}

class UserAnswersServiceSpec extends AsyncFlatSpec {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  val sessionRepository = mock[SessionRepository]
  val service = new UserAnswersService(sessionRepository)(ec)

  behavior of "get"

  it should " return a userAnswers from repository" in {
    val ans = UserAnswers("123")
    when(sessionRepository.get(any)).thenReturn(Future.successful(Some(ans)))

    service.get("123").map { answer =>
      verify(sessionRepository).get(eqTo("123"))
      assertResult(Right(ans),"")( answer)
    }
  }


  it should "return a UnprocessableEntity" in {
    when(sessionRepository.get(any)).thenReturn(Future.successful(None))

    service.get("123").map { answer => {
      assertResult(Left(UnprocessableEntity(notFoundMsg)), "Should return UnprocessableEntity")(answer)
    }}
  }




}
