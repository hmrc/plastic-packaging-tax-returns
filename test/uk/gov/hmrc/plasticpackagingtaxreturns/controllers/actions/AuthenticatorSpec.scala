/*
 * Copyright 2022 HM Revenue & Customs
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

import org.scalatest.EitherValues
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.Helpers.await
import play.api.test.{DefaultAwaitTimeout, FakeRequest}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.AuthAction.{
  pptEnrolmentIdentifierName,
  pptEnrolmentKey
}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents

import scala.concurrent.ExecutionContext

class AuthenticatorSpec
    extends AnyWordSpec with Matchers with MockitoSugar with AuthTestSupport with DefaultAwaitTimeout
    with EitherValues {

  private val mcc           = stubMessagesControllerComponents()
  private val hc            = HeaderCarrier()
  private val request       = FakeRequest()
  private val authenticator = new AuthenticatorImpl(mockAuthConnector, mcc)(ExecutionContext.global)

  "Authenticator" should {
    "return 401 unauthorised " when {
      "missing ppt enrolment key" in {
        withUserWithEnrolments(newUser(Some(newEnrolments(newEnrolment("rubbishKey", "id1", "val1")))))

        val result = await(authenticator.authorisedWithPptId(hc, request))

        result.left.value.statusCode mustBe 401
      }
      "missing ppt enrolment identifier" in {
        withUserWithEnrolments(newUser(Some(newEnrolments(newEnrolment(pptEnrolmentKey, "id1", "val1")))))

        val result = await(authenticator.authorisedWithPptId(hc, request))

        result.left.value.statusCode mustBe 401
      }
      "correct ppt enrolment identifier but no ppt enrolment key" in {
        withUserWithEnrolments(
          newUser(Some(newEnrolments(newEnrolment("someKey", pptEnrolmentIdentifierName, "val1"))))
        )

        val result = await(authenticator.authorisedWithPptId(hc, request))

        result.left.value.statusCode mustBe 401
      }
    }

    "return 200" when {
      "ppt enrolments exist" in {
        withUserWithEnrolments(
          newUser(Some(newEnrolments(newEnrolment(pptEnrolmentKey, pptEnrolmentIdentifierName, "val1"))))
        )

        val result = await(authenticator.authorisedWithPptId(hc, request))

        result.value.pptId mustBe "val1"
      }
      "ppt enrolments exist among other enrolment keys" in {
        withUserWithEnrolments(
          newUser(
            Some(
              newEnrolments(newEnrolment(pptEnrolmentKey, pptEnrolmentIdentifierName, "val1"),
                            newEnrolment("someKey", "someidentifier", "val2")
              )
            )
          )
        )

        val result = await(authenticator.authorisedWithPptId(hc, request))

        result.value.pptId mustBe "val1"
      }
      "multiple enrolment identifiers under same key" in {
        withUserWithEnrolments(
          newUser(
            Some(
              newEnrolments(newEnrolment(pptEnrolmentKey, pptEnrolmentIdentifierName, "val1"),
                            newEnrolment(pptEnrolmentKey, "someidentifier", "val2")
              )
            )
          )
        )

        val result = await(authenticator.authorisedWithPptId(hc, request))

        result.value.pptId mustBe "val1"
      }
    }
  }

}
