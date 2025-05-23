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

package test

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

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK, UNAUTHORIZED}
import play.api.http.{HeaderNames, Status}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WSClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.WiremockItServer
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolments}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate.SubscriptionUpdateSuccessfulResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.{SignedInUser, SubscriptionTestData}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository

import java.time.{ZoneOffset, ZonedDateTime}
import scala.concurrent.Future

class ChangeGroupLeadItSpec
    extends PlaySpec with GuiceOneServerPerSuite with AuthTestSupport with SubscriptionTestData with BeforeAndAfterAll with BeforeAndAfterEach {

  implicit lazy val wireMock: WiremockItServer = WiremockItServer()
  private lazy val wsClient: WSClient          = app.injector.instanceOf[WSClient]

  private lazy val repository        = mock[SessionRepository]
  private val Url                    = s"http://localhost:$port/change-group-lead/$pptReference"
  private val subscriptionDisplayUrl = s"/plastic-packaging-tax/subscriptions/PPT/$pptReference/display"
  private val subscriptionUpdateUrl  = s"/plastic-packaging-tax/subscriptions/PPT/$pptReference/update"

  override lazy val app: Application = {
    wireMock.start()
    SharedMetricRegistries.clear()
    GuiceApplicationBuilder()
      .configure(wireMock.overrideConfig)
      .overrides(bind[AuthConnector].to(mockAuthConnector), bind[SessionRepository].toInstance(repository))
      .build()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector)
    wireMock.reset()

    when(repository.get(any)).thenReturn(Future.successful(Some(userAnswer)))
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    wireMock.stop()
  }

  "service" should {
    "return 200" in {
      setUpMock()

      val response = await(wsClient.url(Url).post(pptReference))

      response.status mustBe OK
    }

    "return error" when {
      "when no subscription available" in {
        withAuthorizedUser()
        stubSubscriptionDisplayRequest(Status.NOT_FOUND)
        stubSubscriptionUpdateRequest(Status.OK, createUpdateSubscriptionResponseBody)

        val response = await(wsClient.url(Url).post(pptReference))

        response.status mustBe INTERNAL_SERVER_ERROR
      }

      "when no user available" in {
        when(repository.clear(any)).thenReturn(Future.successful(false))
        withAuthorizedUser()
        stubSubscriptionDisplayRequest(Status.OK)
        stubSubscriptionUpdateRequest(Status.OK, createUpdateSubscriptionResponseBody)

        val response = await(wsClient.url(Url).post(pptReference))

        response.status mustBe INTERNAL_SERVER_ERROR
      }

      "when cannot update subscription" in {
        when(repository.clear(any)).thenReturn(Future.successful(true))
        withAuthorizedUser()
        stubSubscriptionDisplayRequest(Status.OK, Json.toJson(createSubscriptionDisplayResponse(ukLimitedCompanyGroupSubscription)).toString())
        stubSubscriptionUpdateRequest(Status.NOT_FOUND)

        val response = await(wsClient.url(Url).post(pptReference))

        response.status mustBe INTERNAL_SERVER_ERROR
      }

      "when user not authorised" in {
        withUnauthorizedUser(new Exception)

        val response = await(wsClient.url(Url).post(pptReference))

        response.status mustBe UNAUTHORIZED
      }
    }

    "retry display subscription 3 times" in {
      setUpMock(displayStatus = INTERNAL_SERVER_ERROR)

      await(wsClient.url(Url).post(pptReference))

      wireMock.verify(3, getRequestedFor(urlEqualTo(subscriptionDisplayUrl)))
    }

    "retry 1 times" in {
      setUpMock()

      await(wsClient.url(Url).post(pptReference))

      withClue("subscription display with eis bearer token") {
        wireMock.verify(
          1,
          getRequestedFor(urlEqualTo(subscriptionDisplayUrl))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo("Bearer eis-test123456"))
        )
      }

      withClue("subscription update with eis bearer token") {
        wireMock.verify(
          1,
          putRequestedFor(urlEqualTo(subscriptionUpdateUrl))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo("Bearer eis-test123456"))
        )
      }
    }

    "retry update subscription 3 times" in {
      setUpMock(updateStatus = Status.INTERNAL_SERVER_ERROR)

      await(wsClient.url(Url).post(pptReference))

      wireMock.verify(3, putRequestedFor(urlEqualTo(subscriptionUpdateUrl)))
    }
  }

  private def setUpMock(displayStatus: Int = Status.OK, updateStatus: Int = Status.OK): Unit = {
    when(repository.clear(any)).thenReturn(Future.successful(true))
    authorisedUser

    stubSubscriptionDisplayRequest(displayStatus, Json.toJson(createSubscriptionDisplayResponse(ukLimitedCompanyGroupSubscription)).toString())
    stubSubscriptionUpdateRequest(updateStatus, createUpdateSubscriptionResponseBody)
  }

  private def authorisedUser = {
    val signedInUser: SignedInUser              = newUser(Some(pptEnrolment(pptReference)))
    val enrolments: Enrolments ~ Option[String] = new ~(signedInUser.enrolments, signedInUser.internalId)
    when(mockAuthConnector.authorise[Enrolments ~ Option[String]](any, any)(any, any)) thenReturn Future.successful(enrolments)
  }

  private def stubSubscriptionUpdateRequest(status: Int, body: String = ""): Unit =
    wireMock.stubFor(
      put(subscriptionUpdateUrl)
        .willReturn(
          aResponse()
            .withStatus(status)
            .withBody(body)
        )
    )

  private def createUpdateSubscriptionResponseBody = {
    val subscriptionUpdateResponse: SubscriptionUpdateSuccessfulResponse = SubscriptionUpdateSuccessfulResponse(
      pptReferenceNumber = pptReference,
      processingDate = ZonedDateTime.now(ZoneOffset.UTC),
      formBundleNumber = "12345678901"
    )
    Json.toJson(subscriptionUpdateResponse).toString()
  }

  private def stubSubscriptionDisplayRequest(status: Int, body: String = ""): Unit =
    wireMock.stubFor(
      get(subscriptionDisplayUrl)
        .willReturn(
          aResponse()
            .withStatus(status)
            .withBody(body)
        )
    )

  private def userAnswer =
    UserAnswers(id = pptReference, data = Json.parse(s"""
      |{
      |  "obligation":
      |  {
      |    "fromDate":"2022-04-01",
      |    "toDate":"2022-06-01",
      |    "dueDate":"2022-08-31",
      |    "periodKey":"22C2"
      |  },
      |  "isFirstReturn":false,
      |  "changeGroupLead":
      |  {
      |    "chooseNewGroupLead":
      |    {
      |     "organisationName": "${ukLimitedCompanyGroupMember.organisationDetails.get.organisationName}",
      |     "crn" : "${ukLimitedCompanyGroupMember.customerIdentification1}"
      |    },
      |    "newGroupLeadEnterContactAddress":
      |    {
      |      "addressLine1":"47 Whittingham Road",
      |      "addressLine2":"newCastle",
      |      "postalCode":"NE5 4DL",
      |      "countryCode":"GB"
      |    },
      |    "mainContactName":"fdsf",
      |    "mainContactJobTitle":"fdsfsd"
      |  }
      |}
      |""".stripMargin).asInstanceOf[JsObject])

}
