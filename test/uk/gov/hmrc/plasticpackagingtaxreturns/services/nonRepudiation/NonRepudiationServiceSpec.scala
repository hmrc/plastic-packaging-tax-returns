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

package uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation

import org.mockito.ArgumentMatchersSugar.{any, contains}
import org.mockito.MockitoSugar.{reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.enablers.Messaging
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Logger
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, HttpException, InternalServerException}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.unit.MockConnectors
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.{NrsTestData, SubscriptionTestData}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation.{NonRepudiationMetadata, NonRepudiationSubmissionAccepted}
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService.{NonRepudiationIdentityRetrievals, nonRepudiationIdentityRetrievals}
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EdgeOfSystem

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.util.Base64
import scala.concurrent.{ExecutionContext, Future}

class NonRepudiationServiceSpec
    extends AnyWordSpec with AuthTestSupport with NrsTestData with BeforeAndAfterEach
    with ScalaFutures with Matchers with MockConnectors with SubscriptionTestData {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  implicit val hc: HeaderCarrier = HeaderCarrier(authorization = Some(Authorization(testAuthToken)))

  private val appConfig = mock[AppConfig]
  private val headerCarrier = mock[HeaderCarrier]
  private val edgeOfSystem = mock[EdgeOfSystem]

  implicit val request: Request[AnyContent] = FakeRequest()
  implicit val resolveImplicitAmbiguity: Messaging[InternalServerException] = Messaging.messagingNatureOfThrowable

  val nonRepudiationService: NonRepudiationService = new NonRepudiationService(mockNonRepudiationConnector, 
    mockAuthConnector, appConfig, edgeOfSystem) {
    override val logger: Logger = mockLogger
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(appConfig, headerCarrier, mockLogger)
    when(headerCarrier.authorization) thenReturn Some(Authorization("yeah go right ahead"))
    when(mockAuthConnector.authorise[NonRepudiationIdentityRetrievals](any, any)(any, any)) thenReturn 
      Future.successful(testAuthRetrievals)
    
    when(edgeOfSystem.createEncoder) thenReturn Base64.getEncoder // test uses real encoder
    when(edgeOfSystem.getMessageDigestSingleton) thenReturn MessageDigest.getInstance("SHA-256") // test uses real digest
  }
  
  
  "submitNonRepudiation" should {
    
    "retrieveUserAuthToken" in {
      nonRepudiationService.retrieveUserAuthToken(headerCarrier) mustBe "yeah go right ahead"
    }
    
    "complain if there is no auth token" in {
      when(headerCarrier.authorization) thenReturn None
      the [InternalServerException] thrownBy {
        nonRepudiationService.retrieveUserAuthToken(headerCarrier)
      } must have message "No auth token available for NRS"
    }
    
    "handle failures from NRS service" in {
      when(appConfig.errorLogAlertTag) thenReturn "magic-alert-string"
      when(mockNonRepudiationConnector.submitNonRepudiation(any, any)(any)) thenReturn 
        Future.failed(new HttpException("oops", 479))
      val eventualResponse = nonRepudiationService.submitNonRepudiation("", "", ZonedDateTime.now(), "", Map())(headerCarrier)
      the [Exception] thrownBy await(eventualResponse) must have message "oops"
      verify(mockLogger).error(contains("magic-alert-string"))(any)
      verify(mockLogger).error(contains("oops"))(any)
      verify(mockLogger).error(contains("479"))(any)
    }
    
    val testPayloadString = "testPayloadString"

    "call the nonRepudiationConnector with the correctly formatted metadata" in {
      val testPayloadChecksum = MessageDigest.getInstance("SHA-256")
        .digest(testPayloadString.getBytes(StandardCharsets.UTF_8))
        .map("%02x".format(_)).mkString 
      
      mockAuthorization(nonRepudiationIdentityRetrievals, testAuthRetrievals)
      when(mockNonRepudiationConnector.submitNonRepudiation(any, any) (any)) thenReturn Future.successful(
        NonRepudiationSubmissionAccepted("testSubmissionId"))

      val res = nonRepudiationService.submitNonRepudiation("an-event", testPayloadString, testDateTime, testPPTReference, 
        testUserHeaders)
      await(res) mustBe NonRepudiationSubmissionAccepted("testSubmissionId")

      val testEncodedPayload = Base64.getEncoder.encodeToString(testPayloadString.getBytes(StandardCharsets.UTF_8))
      val expectedMetadata = NonRepudiationMetadata(businessId = "ppt", notableEvent = "an-event",
        "application/json", testPayloadChecksum, testDateTimeString, testNonRepudiationIdentityData, testAuthToken, 
        testUserHeaders, searchKeys = Map("pptReference" -> testPPTReference)
      )
      verify(mockNonRepudiationConnector).submitNonRepudiation(testEncodedPayload, expectedMetadata)(hc)
    }

    "throw an exception when the NRS call fails" in {
      val testExceptionMessage = "testExceptionMessage"

      mockAuthorization(nonRepudiationIdentityRetrievals, testAuthRetrievals)
      mockNonRepudiationSubmissionFailure(new RuntimeException(testExceptionMessage))

      val res =
        nonRepudiationService.submitNonRepudiation("an-event", testPayloadString, testDateTime, testPPTReference, testUserHeaders)

      intercept[RuntimeException](await(res))
    }
  }
}
