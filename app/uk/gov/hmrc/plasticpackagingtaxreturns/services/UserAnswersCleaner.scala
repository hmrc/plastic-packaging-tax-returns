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

package uk.gov.hmrc.plasticpackagingtaxreturns.services

import play.api.libs.json.{JsObject, JsPath, JsValue, Writes}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.SubscriptionsConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.SubscriptionDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.CreditRangeOption
import uk.gov.hmrc.plasticpackagingtaxreturns.services.UserAnswersCleaner.CleaningUserAnswers

import java.time.LocalDate
import javax.inject.Inject
import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}


/** A cleaner that formats user answers from old JsPaths
 * to maintain backwards compatibility for users mid journey during deployments
 */

class UserAnswersCleaner @Inject()(
  availableCreditDateRangesService: AvailableCreditDateRangesService,
  subscriptionsConnector: SubscriptionsConnector,
) (implicit executionContext: ExecutionContext) {
  
  def hasOldAnswers(userAnswers: UserAnswers): Boolean = {
    userAnswers.get[JsValue](JsPath \ "exportedCredits").isDefined || 
      userAnswers.get[JsValue](JsPath \ "convertedCredits").isDefined
  }

  private def getAssumedDateRange(userAnswers: UserAnswers, subscription: SubscriptionDisplayResponse): Option[CreditRangeOption] = {
    userAnswers.get[LocalDate](JsPath \ "obligation" \ "toDate").flatMap { returnToDate =>
      val taxStartDate = subscription.taxStartDate()
      val available = availableCreditDateRangesService.calculate(returnToDate, taxStartDate)
      available match {
        case Seq(claimPeriod) => Some(claimPeriod)
        case _ => None
      }
    }
  }

  def fetchSubscription(pptReference: String)(implicit hc: HeaderCarrier): Future[SubscriptionDisplayResponse] =
    subscriptionsConnector.getSubscriptionFuture(pptReference)

  def clean(userAnswers: UserAnswers, pptReference: String)(implicit hc: HeaderCarrier): Future[(UserAnswers, Boolean)] = {
    Future.unit
      .filter(_ => hasOldAnswers(userAnswers))
      .flatMap(_ => 
        fetchSubscription(pptReference)
          .map(migrateOldAnswers(userAnswers, _))
      )
      .recover(_ => userAnswers -> false)
  }
  
  private def migrateOldAnswers(userAnswers: UserAnswers, subscription: SubscriptionDisplayResponse): (UserAnswers, Boolean) = {

    val startedAReturn = userAnswers.get[JsObject](JsPath \ "obligation").isDefined
    val assumableDateRange = getAssumedDateRange(userAnswers, subscription)

    if (startedAReturn && assumableDateRange.isDefined) { //todo map?
      val taxRange = assumableDateRange.get
      val updated = userAnswers
        .migrate(JsPath \ "exportedCredits" \ "yesNo", JsPath \ "credit" \ taxRange.key \ "exportedCredits" \ "yesNo")
        .migrate(JsPath \ "exportedCredits" \ "weight", JsPath \ "credit" \ taxRange.key \ "exportedCredits" \ "weight")
        .migrate(JsPath \ "convertedCredits" \ "yesNo", JsPath \ "credit" \ taxRange.key \ "convertedCredits" \ "yesNo")
        .migrate(JsPath \ "convertedCredits" \ "weight", JsPath \ "credit" \ taxRange.key \ "convertedCredits" \ "weight")
        .removePath(JsPath \ "exportedCredits")
        .removePath(JsPath \ "convertedCredits")

        val userAnswersChanged = updated != userAnswers

         val updatedWithMeta = if (userAnswersChanged) {
           updated
             .setOrFail(JsPath \ "credit" \ taxRange.key \ "toDate", taxRange.to)
             .setOrFail(JsPath \ "credit" \ taxRange.key \ "fromDate", taxRange.from)
         } else updated
      (updatedWithMeta, userAnswersChanged)
    } else userAnswers -> false
  }

}

object UserAnswersCleaner {

  implicit class CleaningUserAnswers(val userAnswers: UserAnswers) extends AnyVal {
    def migrate(from: JsPath, to: JsPath): UserAnswers = {
      (if (userAnswers.get[JsValue](to).isEmpty && userAnswers.get[JsValue](from).isDefined)
          userAnswers.setOrFail(to, userAnswers.get[JsValue](from))
      else userAnswers
      ).removePath(from)
    }

    def setIfDefined[A](check: JsPath, setPath: JsPath, value: A)(implicit writes: Writes[A]): UserAnswers = {
      if (userAnswers.get[JsValue](check).isDefined) userAnswers.setOrFail(setPath, value)
      else userAnswers
    }
  }
}
