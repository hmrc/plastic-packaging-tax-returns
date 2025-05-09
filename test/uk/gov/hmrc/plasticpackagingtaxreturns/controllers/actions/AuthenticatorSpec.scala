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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions

import org.mockito.Mockito.reset
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.Helpers.await
import play.api.test.{DefaultAwaitTimeout, FakeRequest}
import uk.gov.hmrc.auth.core.{InsufficientEnrolments, InternalError}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.AuthAction.{
  pptEnrolmentIdentifierName,
  pptEnrolmentKey
}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents

import scala.concurrent.ExecutionContext

class AuthenticatorSpec
    extends AnyWordSpec with Matchers with MockitoSugar with AuthTestSupport with DefaultAwaitTimeout with EitherValues
    with BeforeAndAfterEach {

  private val mcc           = stubMessagesControllerComponents()
  private val hc            = HeaderCarrier()
  private val request       = FakeRequest()
  private val authenticator = new AuthenticatorImpl(mockAuthConnector, mcc)(ExecutionContext.global)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector)
  }

  "Authenticator" should {
    "return left" when {
      "auth returns Insufficient enrolments" in {
        withUnauthorizedUser(InsufficientEnrolments())

        val result = await(authenticator.authorisedWithPptReference("someone-elses-ppt-account")(hc, request))

        result.left.value.statusCode mustBe 401
      }

      "auth fails generally" in {
        withUnauthorizedUser(InternalError("A general auth failure"))

        val result = await(authenticator.authorisedWithPptReference("val1")(hc, request))

        result.left.value.statusCode mustBe 401
      }

      "internal id is missing" in {
        withAuthorizedUser(
          newUser(Some(newEnrolments(newEnrolment(pptEnrolmentKey, pptEnrolmentIdentifierName, "val1"))))
            .copy(internalId = None)
        )

        val result = await(authenticator.authorisedWithPptReference("val1")(hc, request))

        result.left.value.statusCode mustBe 500
        result.left.value.message mustBe "Internal server error is AuthenticatorImpl::authorisedWithPptReference -  internalId is required"
      }
    }

    "return right and populate verified ppt reference" when {
      "ppt enrolment exists" in {
        withAuthorizedUser(newUser(Some(newEnrolments(newEnrolment(
          pptEnrolmentKey,
          pptEnrolmentIdentifierName,
          "val1"
        )))))

        val result = await(authenticator.authorisedWithPptReference("val1")(hc, request))

        result.value.pptReference mustBe "val1"
      }
    }
  }

}
