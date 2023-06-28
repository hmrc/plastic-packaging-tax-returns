/*
 * Copyright 2023 HM Revenue & Customs
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
import org.mockito.MockitoSugar.{mock, times, verify, when}
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.Futures.whenReady
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatestplus.play.PlaySpec
import play.api.mvc.Results.UnprocessableEntity
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.UserAnswersService.notFoundMsg

import scala.concurrent.{ExecutionContext, Future}

class UserAnswersServiceSpec extends PlaySpec {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  val sessionRepository = mock[SessionRepository]
  val service = new UserAnswersService(sessionRepository)(ec)

//  behavior of "get"

  "get" should {
    "return a userAnswers from repository" in {
      val ans = UserAnswers("123")
      when(sessionRepository.get(any)).thenReturn(Future.successful(Some(ans)))

      val result = await(service.get("123"))

      result mustBe Right(ans)
      verify(sessionRepository).get(eqTo("123"))
    }

    "return a UnprocessableEntity" in {
      when(sessionRepository.get(any)).thenReturn(Future.successful(None))

      val result = await(service.get("123"))
      result mustBe Left(UnprocessableEntity(notFoundMsg))
    }
  }
}
