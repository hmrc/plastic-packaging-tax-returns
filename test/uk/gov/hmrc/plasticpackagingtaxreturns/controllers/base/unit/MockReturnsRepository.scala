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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.unit

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository

import scala.concurrent.Future

trait MockReturnsRepository extends MockitoSugar with BeforeAndAfterEach {
  self: Suite =>

  protected val mockSessionRepository = mock[SessionRepository]

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSessionRepository)
  }

  protected def mockGetCache(userAnswers: Option[UserAnswers]) =
    when(mockSessionRepository.get(any())).thenReturn(Future.successful(userAnswers))

}
