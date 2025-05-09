/*
 * Copyright 2025 HM Revenue & Customs
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

import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.MockitoSugar.{mock, reset, spyLambda, verify, verifyNoMoreInteractions, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.mvc.Result
import play.api.mvc.Results.{Ok, UnprocessableEntity}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository

import scala.concurrent.{ExecutionContext, Future}

class UserAnswersServiceSpec extends PlaySpec with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  val sessionRepository             = mock[SessionRepository]
  val service                       = new UserAnswersService(sessionRepository)(ec)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(sessionRepository)
  }

  "get with function parameter" should {
    val block: UserAnswers => Future[Result] = _ => Future.successful(Ok("blah"))
    val spyBlock                             = spyLambda(block)

    "execute the block if userAnswer found" in {
      val ans = UserAnswers("123")
      when(sessionRepository.get(any)).thenReturn(Future.successful(Some(ans)))

      val result = await(service.get("123")(spyBlock))

      result mustBe Ok("blah")
      verify(spyBlock)(ans)
      verify(sessionRepository).get(eqTo("123"))
    }

    "not execute the block if userAnswer not found" in {
      when(sessionRepository.get(any)).thenReturn(Future.successful(None))

      val result = await(service.get("123")(spyBlock))

      result mustBe UnprocessableEntity("No user answers found")
      verifyNoMoreInteractions(spyBlock)
      verify(sessionRepository).get(eqTo("123"))
    }
  }
}
