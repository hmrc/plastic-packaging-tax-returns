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
