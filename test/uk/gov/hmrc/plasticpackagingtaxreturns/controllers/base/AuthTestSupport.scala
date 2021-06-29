/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.{ArgumentMatcher, ArgumentMatchers}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Logger
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.allEnrolments
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.AuthAction
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.AuthAction._
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.SignedInUser

import scala.concurrent.Future

trait AuthTestSupport extends MockitoSugar {

  lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  lazy val mockLogger: Logger               = mock[Logger]

  val enrolment: Predicate = Enrolment(pptEnrolmentKey)
  val pptReference         = "7777777"

  def withAuthorizedUser(user: SignedInUser = newUser(Some(pptEnrolment("external1")))): Unit =
    when(
      mockAuthConnector.authorise(ArgumentMatchers.argThat(pptEnrollmentMatcher(user)),
                                  ArgumentMatchers.eq(allEnrolments)
      )(any(), any())
    )
      .thenReturn(Future.successful(user.enrolments))

  def withUserWithEnrolments(user: SignedInUser) =
    when(
      mockAuthConnector.authorise(ArgumentMatchers.argThat((_: Predicate) => true), ArgumentMatchers.eq(allEnrolments))(
        any(),
        any()
      )
    )
      .thenReturn(Future.successful(user.enrolments))

  def withUnauthorizedUser(error: Throwable): Unit =
    when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.failed(error))

  def userWithoutProviderId(user: SignedInUser = newUser(Some(pptEnrolment("")))): Unit =
    when(
      mockAuthConnector.authorise(ArgumentMatchers.argThat(pptEnrollmentMatcher(user)),
                                  ArgumentMatchers.eq(allEnrolments)
      )(any(), any())
    )
      .thenReturn(Future.successful(Enrolments(Set())))

  def pptEnrollmentMatcher(user: SignedInUser): ArgumentMatcher[Predicate] =
    (p: Predicate) => p == enrolment && user.enrolments.getEnrolment(pptEnrolmentKey).isDefined

  def newUser(enrolments: Option[Enrolments] = Some(pptEnrolment("123"))): SignedInUser =
    SignedInUser(Credentials("123123123", "Plastic Limited"),
                 Name(Some("Aldo"), Some("Rain")),
                 Some("amina@hmrc.co.uk"),
                 "123",
                 Some("Int-ba17b467-90f3-42b6-9570-73be7b78eb2b"),
                 Some(AffinityGroup.Organisation),
                 enrolments.getOrElse(Enrolments(Set()))
    )

  def pptEnrolment(pptEnrolmentId: String) =
    newEnrolments(newEnrolment(AuthAction.pptEnrolmentKey, AuthAction.pptEnrolmentIdentifierName, pptEnrolmentId))

  def newEnrolments(enrolment: Enrolment*): Enrolments =
    Enrolments(enrolment.toSet)

  def newEnrolment(key: String, identifierName: String, identifierValue: String): Enrolment =
    Enrolment(key).withIdentifier(identifierName, identifierValue)

}
