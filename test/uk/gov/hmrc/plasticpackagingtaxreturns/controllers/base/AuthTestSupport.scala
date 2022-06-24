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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.stubbing.OngoingStubbing
import org.mockito.{ArgumentMatcher, ArgumentMatchers}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Logger
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.{EmptyPredicate, Predicate}
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{allEnrolments, internalId}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.AuthAction
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.AuthAction._
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.SignedInUser
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService.NonRepudiationIdentityRetrievals

import scala.concurrent.{ExecutionContext, Future}

trait AuthTestSupport extends MockitoSugar {

  lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  lazy val mockLogger: Logger               = mock[Logger]

  val pptReference = "7777777"

  def enrolmentWithDelegatedAuth(pptReference: String) =
    Enrolment(pptEnrolmentKey).withIdentifier(pptEnrolmentIdentifierName, pptReference).withDelegatedAuthRule(
      "ppt-auth"
    )

  def withAuthorizedUser(user: SignedInUser = newUser(Some(pptEnrolment(pptReference)))): Unit = {
    val fetch = allEnrolments and internalId

    when(
      mockAuthConnector.authorise(ArgumentMatchers.argThat(pptEnrollmentMatcherForPptUser(user)),
                                  ArgumentMatchers.eq(fetch)
      )(any(), any())
    )
      .thenReturn(Future.successful(new ~(user.enrolments, user.internalId)))
  }

  def withUnauthorizedUser(error: Throwable): Unit =
    when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.failed(error))

  def pptEnrollmentMatcherForPptUser(user: SignedInUser): ArgumentMatcher[Predicate] = {
    val pptEnrolment = user.enrolments.getEnrolment(pptEnrolmentKey).get
    val pptReference = pptEnrolment.getIdentifier(pptEnrolmentIdentifierName).get.value
    (p: Predicate) =>
      p == enrolmentWithDelegatedAuth(pptReference) && user.enrolments.getEnrolment(pptEnrolmentKey).isDefined
  }

  def newUser(enrolments: Option[Enrolments] = Some(pptEnrolment("123"))): SignedInUser =
    SignedInUser(Credentials("123123123", "Plastic Limited"),
                 name = Name(Some("Aldo"), Some("Rain")),
                 email = Some("amina@hmrc.co.uk"),
                 externalId = "123",
                 internalId = Some("Int-ba17b467-90f3-42b6-9570-73be7b78eb2b"),
                 cacheId = "Int-ba17b467-90f3-42b6-9570-73be7b78eb2b-test02",
                 affinityGroup = Some(AffinityGroup.Organisation),
                 enrolments = enrolments.getOrElse(Enrolments(Set()))
    )

  def pptEnrolment(pptEnrolmentId: String) =
    newEnrolments(newEnrolment(AuthAction.pptEnrolmentKey, AuthAction.pptEnrolmentIdentifierName, pptEnrolmentId))

  def newEnrolments(enrolment: Enrolment*): Enrolments =
    Enrolments(enrolment.toSet)

  def newEnrolment(key: String, identifierName: String, identifierValue: String): Enrolment =
    Enrolment(key).withIdentifier(identifierName, identifierValue)

  def mockAuthorization(
    nrsIdentityRetrievals: Retrieval[NonRepudiationIdentityRetrievals],
    authRetrievalsResponse: NonRepudiationIdentityRetrievals
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): OngoingStubbing[Future[NonRepudiationIdentityRetrievals]] =
    when(
      mockAuthConnector.authorise(ArgumentMatchers.eq(EmptyPredicate), ArgumentMatchers.eq(nrsIdentityRetrievals))(
        ArgumentMatchers.eq(hc),
        ArgumentMatchers.eq(ec)
      )
    ).thenReturn(Future.successful(authRetrievalsResponse))

}
