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

package uk.gov.hmrc.plasticpackagingtaxreturns.service.nonRepudiation

import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.{Authorization, HeaderCarrier}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.unit.MockConnectors
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.{NrsTestData, SubscriptionTestData}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation.{
  NonRepudiationMetadata,
  NonRepudiationSubmissionAccepted
}
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService.nonRepudiationIdentityRetrievals

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import scala.concurrent.ExecutionContext

class NonRepudiationServiceSpec
    extends AnyWordSpec with GuiceOneAppPerSuite with AuthTestSupport with NrsTestData with BeforeAndAfterEach
    with ScalaFutures with Matchers with MockConnectors with SubscriptionTestData {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  implicit val hc: HeaderCarrier =
    HeaderCarrier(authorization = Some(Authorization(testAuthToken)))

  val nonRepudiationService: NonRepudiationService =
    NonRepudiationService(mockNonRepudiationConnector, mockAuthConnector)

  implicit val request: Request[AnyContent] = FakeRequest()

  "submitNonRepudiation" should {
    val testPayloadString = "testPayloadString"

    "call the nonRepudiationConnector with the correctly formatted metadata" in {
      val testSubmissionId = "testSubmissionId"

      val testPayloadChecksum = MessageDigest.getInstance("SHA-256")
        .digest(testPayloadString.getBytes(StandardCharsets.UTF_8))
        .map("%02x".format(_)).mkString

      val testEncodedPayload =
        Base64.getEncoder.encodeToString(testPayloadString.getBytes(StandardCharsets.UTF_8))

      val expectedMetadata = NonRepudiationMetadata(businessId = "ppt",
                                                    notableEvent = "ppt-subscription",
                                                    payloadContentType = "application/json",
                                                    payloadSha256Checksum = testPayloadChecksum,
                                                    userSubmissionTimestamp = testDateTime,
                                                    identityData = testNonRepudiationIdentityData,
                                                    userAuthToken = testAuthToken,
                                                    headerData = testUserHeaders,
                                                    searchKeys =
                                                      Map("pptReference" -> testPPTReference)
      )
      mockAuthorization(nonRepudiationIdentityRetrievals, testAuthRetrievals)
      mockNonRepudiationSubmission(testEncodedPayload,
                                   expectedMetadata,
                                   NonRepudiationSubmissionAccepted(testSubmissionId)
      )

      val res =
        nonRepudiationService.submitNonRepudiation(testPayloadString, testDateTime, testPPTReference, testUserHeaders)

      await(res) mustBe NonRepudiationSubmissionAccepted(testSubmissionId)
    }

    "throw an exception when the NRS call fails" in {
      val testExceptionMessage = "testExceptionMessage"

      mockAuthorization(nonRepudiationIdentityRetrievals, testAuthRetrievals)
      mockNonRepudiationSubmissionFailure(new RuntimeException(testExceptionMessage))

      val res =
        nonRepudiationService.submitNonRepudiation(testPayloadString, testDateTime, testPPTReference, testUserHeaders)

      intercept[RuntimeException](await(res))
    }
  }
}
