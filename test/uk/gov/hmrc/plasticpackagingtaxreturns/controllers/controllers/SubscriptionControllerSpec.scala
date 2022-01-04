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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.controllers

import com.codahale.metrics.SharedMetricRegistries
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, verify, verifyNoInteractions, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.json.Json.toJson
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{route, status, _}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, HttpException}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.SubscriptionsConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate.{
  SubscriptionUpdateRequest,
  SubscriptionUpdateSuccessfulResponse,
  SubscriptionUpdateWithNrsFailureResponse,
  SubscriptionUpdateWithNrsSuccessfulResponse
}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.unit.MockConnectors
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.SubscriptionTestData
import uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation.NonRepudiationSubmissionAccepted
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService

import java.time.{ZoneOffset, ZonedDateTime}
import scala.concurrent.Future

class SubscriptionControllerSpec
    extends AnyWordSpec with GuiceOneAppPerSuite with BeforeAndAfterEach with ScalaFutures with Matchers
    with AuthTestSupport with SubscriptionTestData with MockConnectors {

  SharedMetricRegistries.clear()
  protected val mockNonRepudiationService: NonRepudiationService = mock[NonRepudiationService]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[AuthConnector].to(mockAuthConnector),
               bind[SubscriptionsConnector].to(mockSubscriptionsConnector),
               bind[NonRepudiationService].to(mockNonRepudiationService)
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector)
    reset(mockSubscriptionsConnector)
    reset(mockNonRepudiationService)
  }

  val subscriptionUpdateResponse: SubscriptionUpdateSuccessfulResponse = SubscriptionUpdateSuccessfulResponse(
    pptReferenceNumber = pptReference,
    processingDate = ZonedDateTime.now(ZoneOffset.UTC),
    formBundleNumber = "12345678901"
  )

  private def getRequest(pptReference: String) = FakeRequest("GET", s"/subscriptions/$pptReference")

  "GET subscription" should {

    "return 200" when {
      "request for uk limited company subscription is valid" in {
        withAuthorizedUser()
        val subscriptionDisplayResponse = createSubscriptionDisplayResponse(ukLimitedCompanySubscription)
        mockGetSubscription(pptReference, subscriptionDisplayResponse)

        val result: Future[Result] = route(app, getRequest(pptReference)).get

        status(result) must be(OK)
        contentAsJson(result) mustBe toJson(subscriptionDisplayResponse)
      }
    }

    "return 200" when {
      "request for sole trader subscription is valid" in {
        withAuthorizedUser()
        val subscriptionDisplayResponse = createSubscriptionDisplayResponse(soleTraderSubscription)
        mockGetSubscription(pptReference, subscriptionDisplayResponse)

        val result: Future[Result] = route(app, getRequest(pptReference)).get

        status(result) must be(OK)
        contentAsJson(result) mustBe toJson(subscriptionDisplayResponse)
      }
    }

    "return 404" when {
      "registration not found" in {
        withAuthorizedUser()
        mockGetSubscriptionFailure(pptReference, 404)

        val result: Future[Result] = route(app, getRequest(pptReference)).get

        status(result) mustBe NOT_FOUND
      }
    }

    "return 401" when {
      "unauthorized" in {
        withUnauthorizedUser(new RuntimeException())

        val result: Future[Result] = route(app, getRequest(pptReference)).get

        status(result) must be(UNAUTHORIZED)
        verifyNoInteractions(mockSubscriptionsConnector)
      }
    }
  }

  "PUT subscription" should {
    val put = FakeRequest("PUT", "/subscriptions/" + pptReference)
    "return 200 for UK Limited Company " when {
      "EIS update subscription is successful " when {
        "and NRS submission is successful " in {
          val nrSubmissionId = "nrSubmissionId"
          withAuthorizedUser()
          val request: SubscriptionUpdateRequest =
            createSubscriptionUpdateRequest(ukLimitedCompanySubscription)
          mockSubscriptionUpdate(pptReference, request, subscriptionUpdateResponse)
          when(mockNonRepudiationService.submitNonRepudiation(any(), any(), any(), any())(any())).thenReturn(
            Future.successful(NonRepudiationSubmissionAccepted(nrSubmissionId))
          )

          val result: Future[Result] = route(app, put.withJsonBody(toJson(request))).get

          status(result) must be(OK)
          val response = contentAsJson(result).as[SubscriptionUpdateWithNrsSuccessfulResponse]
          response.pptReference mustBe subscriptionUpdateResponse.pptReferenceNumber
          response.formBundleNumber mustBe subscriptionUpdateResponse.formBundleNumber
          response.processingDate mustBe subscriptionUpdateResponse.processingDate
          response.nrSubmissionId mustBe nrSubmissionId
          verify(mockNonRepudiationService).submitNonRepudiation(
            ArgumentMatchers.eq(Json.toJson(request.toSubscription).toString),
            any[ZonedDateTime],
            ArgumentMatchers.eq(subscriptionUpdateResponse.pptReferenceNumber),
            ArgumentMatchers.eq(pptUserHeaders)
          )(any[HeaderCarrier])
        }

        "and NRS submission is not successful " in {
          val nrsErrorMessage = "Service unavailable"
          withAuthorizedUser(user = newUser())
          val request: SubscriptionUpdateRequest =
            createSubscriptionUpdateRequest(ukLimitedCompanySubscription)
          mockSubscriptionUpdate(pptReference, request, subscriptionUpdateResponse)
          when(mockNonRepudiationService.submitNonRepudiation(any(), any(), any(), any())(any())).thenReturn(
            Future.failed(new HttpException(nrsErrorMessage, SERVICE_UNAVAILABLE))
          )

          val result: Future[Result] = route(app, put.withJsonBody(toJson(request))).get

          status(result) must be(OK)
          val response = contentAsJson(result).as[SubscriptionUpdateWithNrsFailureResponse]
          response.pptReference mustBe subscriptionUpdateResponse.pptReferenceNumber
          response.formBundleNumber mustBe subscriptionUpdateResponse.formBundleNumber
          response.processingDate mustBe subscriptionUpdateResponse.processingDate
          response.nrsFailureReason mustBe nrsErrorMessage

          verify(mockNonRepudiationService).submitNonRepudiation(
            ArgumentMatchers.contains(Json.toJson(request.toSubscription).toString),
            any[ZonedDateTime],
            ArgumentMatchers.eq(subscriptionUpdateResponse.pptReferenceNumber),
            ArgumentMatchers.eq(pptUserHeaders)
          )(any[HeaderCarrier])
        }
      }
    }

    "return 200 for Sole Trader " when {
      "EIS update subscription is successful" when {
        "and NRS submission is successful " in {

          val nrSubmissionId = "nrSubmissionId"
          withAuthorizedUser()
          val request: SubscriptionUpdateRequest =
            createSubscriptionUpdateRequest(soleTraderSubscription)

          mockSubscriptionUpdate(pptReference, request, subscriptionUpdateResponse)
          when(mockNonRepudiationService.submitNonRepudiation(any(), any(), any(), any())(any())).thenReturn(
            Future.successful(NonRepudiationSubmissionAccepted(nrSubmissionId))
          )

          val result: Future[Result] = route(app, put.withJsonBody(toJson(request))).get

          status(result) must be(OK)
          val response = contentAsJson(result).as[SubscriptionUpdateWithNrsSuccessfulResponse]
          response.pptReference mustBe subscriptionUpdateResponse.pptReferenceNumber
          response.formBundleNumber mustBe subscriptionUpdateResponse.formBundleNumber
          response.processingDate mustBe subscriptionUpdateResponse.processingDate
          response.nrSubmissionId mustBe nrSubmissionId
          verify(mockNonRepudiationService).submitNonRepudiation(
            ArgumentMatchers.eq(Json.toJson(request.toSubscription).toString()),
            any[ZonedDateTime],
            ArgumentMatchers.eq(subscriptionUpdateResponse.pptReferenceNumber),
            ArgumentMatchers.eq(pptUserHeaders)
          )(any[HeaderCarrier])
        }

        "and NRS submission is not successful " in {
          val nrsErrorMessage = "Service unavailable"
          withAuthorizedUser(user = newUser())
          val request: SubscriptionUpdateRequest =
            createSubscriptionUpdateRequest(soleTraderSubscription)
          mockSubscriptionUpdate(pptReference, request, subscriptionUpdateResponse)
          when(mockNonRepudiationService.submitNonRepudiation(any(), any(), any(), any())(any())).thenReturn(
            Future.failed(new HttpException(nrsErrorMessage, SERVICE_UNAVAILABLE))
          )

          val result: Future[Result] = route(app, put.withJsonBody(toJson(request))).get

          status(result) must be(OK)
          val response = contentAsJson(result).as[SubscriptionUpdateWithNrsFailureResponse]
          response.pptReference mustBe subscriptionUpdateResponse.pptReferenceNumber
          response.formBundleNumber mustBe subscriptionUpdateResponse.formBundleNumber
          response.processingDate mustBe subscriptionUpdateResponse.processingDate
          response.nrsFailureReason mustBe nrsErrorMessage

          verify(mockNonRepudiationService).submitNonRepudiation(
            ArgumentMatchers.contains(Json.toJson(request.toSubscription).toString),
            any[ZonedDateTime],
            ArgumentMatchers.eq(subscriptionUpdateResponse.pptReferenceNumber),
            ArgumentMatchers.eq(pptUserHeaders)
          )(any[HeaderCarrier])
        }
      }
    }

    "return 200 for group subscription" when {
      "EIS update subscription is successful" in {
        val nrSubmissionId = "nrSubmissionId"
        withAuthorizedUser()
        val request: SubscriptionUpdateRequest =
          createSubscriptionUpdateRequest(ukLimitedCompanyGroupSubscription)
        mockSubscriptionUpdate(pptReference, request, subscriptionUpdateResponse)
        when(mockNonRepudiationService.submitNonRepudiation(any(), any(), any(), any())(any())).thenReturn(
          Future.successful(NonRepudiationSubmissionAccepted(nrSubmissionId))
        )

        val result: Future[Result] = route(app, put.withJsonBody(toJson(request))).get

        status(result) must be(OK)
        val response = contentAsJson(result).as[SubscriptionUpdateWithNrsSuccessfulResponse]
        response.pptReference mustBe subscriptionUpdateResponse.pptReferenceNumber
        response.formBundleNumber mustBe subscriptionUpdateResponse.formBundleNumber
        response.processingDate mustBe subscriptionUpdateResponse.processingDate
        response.nrSubmissionId mustBe nrSubmissionId
        verify(mockNonRepudiationService).submitNonRepudiation(
          ArgumentMatchers.eq(Json.toJson(request.toSubscription).toString),
          any[ZonedDateTime],
          ArgumentMatchers.eq(subscriptionUpdateResponse.pptReferenceNumber),
          ArgumentMatchers.eq(pptUserHeaders)
        )(any[HeaderCarrier])
      }
    }

    "return 401" when {
      "unauthorized" in {
        withUnauthorizedUser(new RuntimeException())
        val request: SubscriptionUpdateRequest =
          createSubscriptionUpdateRequest(soleTraderSubscription)

        val result: Future[Result] = route(app, put.withJsonBody(toJson(request))).get

        status(result) must be(UNAUTHORIZED)
        verifyNoInteractions(mockSubscriptionsConnector)
      }
    }

    "return 500" when {
      "EIS/IF subscription call returns an exception" in {
        withAuthorizedUser()
        val request: SubscriptionUpdateRequest =
          createSubscriptionUpdateRequest(ukLimitedCompanySubscription)
        mockSubscriptionSubmitFailure(new RuntimeException("error"))
        intercept[Exception] {
          val result: Future[Result] = route(app, put.withJsonBody(toJson(request))).get
          status(result)
        }
      }
    }
  }

}
