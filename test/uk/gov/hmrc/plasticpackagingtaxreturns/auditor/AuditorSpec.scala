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

package uk.gov.hmrc.plasticpackagingtaxreturns.auditor

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.client.{VerificationException, WireMock}
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import play.api.http.Status
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.{Auditor, ChangeSubscriptionEvent}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.Subscription
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.{ConnectorISpec, Injector}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.SubscriptionTestData

class AuditorSpec extends ConnectorISpec with Injector with ScalaFutures with SubscriptionTestData {

  val auditor: Auditor = app.injector.instanceOf[Auditor]
  val auditUrl         = "/write/audit"

  override def overrideConfig: Map[String, Any] =
    Map("auditing.enabled" -> true, "auditing.consumer.baseUri.port" -> wirePort)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    WireMock.configureFor(wireHost, wirePort)
    wireMockServer.start()
  }

  override protected def afterAll(): Unit = {
    wireMockServer.stop()
    super.afterAll()
  }

  "Auditor" should {
    "post registration change event" when {
      "subscriptionUpdate invoked" in {
        givenAuditReturns(Status.NO_CONTENT)
        val subscriptionUpdate: Subscription =
          createSubscriptionUpdateRequest(ukLimitedCompanySubscription).toSubscription

        auditor.subscriptionUpdated(subscriptionUpdate, pptReference = Some("pptReference"))

        eventually(timeout(Span(5, Seconds))) {
          eventSendToAudit(auditUrl, subscriptionUpdate) mustBe true
        }
      }
    }

    "not throw exception" when {
      "submit registration audit event fails" in {
        givenAuditReturns(Status.BAD_REQUEST)
        val subscriptionUpdate: Subscription =
          createSubscriptionUpdateRequest(ukLimitedCompanySubscription).toSubscription

        auditor.subscriptionUpdated(subscriptionUpdate)

        eventually(timeout(Span(5, Seconds))) {
          eventSendToAudit(auditUrl, subscriptionUpdate) mustBe true
        }
      }
    }

  }

  private def givenAuditReturns(statusCode: Int): Unit =
    stubFor(
      post(auditUrl)
        .willReturn(
          aResponse()
            .withStatus(statusCode)
        )
    )

  private def eventSendToAudit(url: String, subscriptionUpdate: Subscription): Boolean =
    eventSendToAudit(url, ChangeSubscriptionEvent.eventType, Subscription.format.writes(subscriptionUpdate).toString())

  private def eventSendToAudit(url: String, eventType: String, body: String): Boolean =
    try {
      verify(
        postRequestedFor(urlEqualTo(url))
          .withRequestBody(equalToJson(s"""{
                                          |                  "auditSource": "plastic-packaging-tax-returns",
                                          |                  "auditType": "$eventType",
                                          |                  "eventId": "$${json-unit.any-string}",
                                          |                  "tags": {
                                          |                    "clientIP": "-",
                                          |                    "path": "-",
                                          |                    "X-Session-ID": "-",
                                          |                    "Akamai-Reputation": "-",
                                          |                    "X-Request-ID": "-",
                                          |                    "deviceID": "-",
                                          |                    "clientPort": "-"
                                          |                  },
                                          |                  "detail": $body,
                                          |                  "generatedAt": "$${json-unit.any-string}",
                                          |                  "metadata": {
                                          |                    "sendAttemptAt": "$${json-unit.any-string}",
                                          |                    "instanceID": "$${json-unit.any-string}",
                                          |                    "sequence": "$${json-unit.any-number}"
                                          |                  }
                                          |                }""".stripMargin, true, true))
      )
      true
    } catch {
      case _: VerificationException => false
    }

}
