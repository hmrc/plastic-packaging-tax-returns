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

import org.mockito.ArgumentMatchersSugar.any
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json.obj
import play.api.libs.json.{JsObject, JsPath, Json}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.SubscriptionsConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.SubscriptionDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.returns.{ConvertedCreditWeightGettable, ConvertedCreditYesNoGettable, ExportedCreditWeightGettable, ExportedCreditYesNoGettable}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.CreditRangeOption
import uk.gov.hmrc.plasticpackagingtaxreturns.services.UserAnswersCleaner.CleaningUserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.support.ReturnTestHelper.returnWithLegacyCreditData

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserAnswersCleanerSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach {

  private implicit val headerCarrier: HeaderCarrier = mock[HeaderCarrier]
  private val subscriptionConnector = mock[SubscriptionsConnector]
  private val mockAvailService = mock[AvailableCreditDateRangesService]

  private val sut = new UserAnswersCleaner(mockAvailService, subscriptionConnector)

  private val userAnswers = UserAnswers("id", data = obj(
    "obligation" -> obj(
      "fromDate" -> "2023-01-01",
      "toDate" -> "2023-03-31",
      "dueDate" -> "2023-05-31",
      "periodKey" -> "23C1"
    ),
    "isFirstReturn" -> false
  ))

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAvailService)

    when(mockAvailService.calculate(any, any)) thenReturn Seq(
      CreditRangeOption(LocalDate.of(2022, 4, 1), LocalDate.of(2022, 12, 31))
    )

    val subscription = mock[SubscriptionDisplayResponse]
    when(subscription.taxStartDate()) thenReturn LocalDate.of(1, 2, 3)
    when(subscriptionConnector.getSubscriptionFuture(any)(any)) thenReturn Future.successful(subscription)
  }


  "clean" must {
    "do nothing" when {
      "return has not been started" in {
        val unchangedUserAnswers = UserAnswers("test-id").setOrFail(JsPath \ "blah", "bloop")

        val (userAnswers, hasBeenCleaned) = await(sut.clean(unchangedUserAnswers, "ppt-ref"))
        userAnswers mustBe unchangedUserAnswers
        hasBeenCleaned mustBe false

        withClue("don't call the subscriptions api if we don't have to") {
          verifyNoInteractions(mockAvailService)
          verifyNoInteractions(subscriptionConnector)
        }
      }

      "a return, using the current journey, has started" in {
        await(sut.clean(userAnswers, "ppt-ref")) mustBe(userAnswers, false)
        verifyNoInteractions(mockAvailService)
        verifyNoInteractions(subscriptionConnector)
      }
    }
    
    "convert and old userAnswers in to a new one" in {
      val oldUserAnswers = UserAnswers("test-id", Json.parse(returnWithLegacyCreditData).as[JsObject])
      val expectedUserAnswers = oldUserAnswers.copy(data = Json.parse(newUserAnswerData).as[JsObject])

      val (userAnswers, hasBeenCleaned) = await(sut.clean(oldUserAnswers, "ppt-ref"))
      userAnswers mustBe expectedUserAnswers
      hasBeenCleaned mustBe true

      verify(subscriptionConnector).getSubscriptionFuture("ppt-ref")(headerCarrier)
      verify(mockAvailService).calculate(LocalDate.of(2023, 3, 31), LocalDate.of(1, 2, 3))
    }

    "error" when {
      "available years is 0" in {
        val userAnswers = UserAnswers("test-id", Json.parse(returnWithLegacyCreditData).as[JsObject])
        when(mockAvailService.calculate(any, any)) thenReturn Seq.empty

        val (newUserAnswers, hasBeenCleaned) = await(sut.clean(userAnswers, "ppt-ref"))
        newUserAnswers mustBe userAnswers
        hasBeenCleaned mustBe false
      }

      "available years is 2+" in {
        val userAnswers = UserAnswers("test-id", Json.parse(returnWithLegacyCreditData).as[JsObject])
        val opt = CreditRangeOption(LocalDate.of(2022, 4, 1), LocalDate.of(2022, 12, 31))
        when(mockAvailService.calculate(any, any))
          .thenReturn(Seq(opt, opt))

        val (newUserAnswers, hasBeenCleaned) = await(sut.clean(userAnswers, "ppt-ref"))
        newUserAnswers mustBe userAnswers
        hasBeenCleaned mustBe false
      }
    }
  }

  "migrate" must {
    val to = JsPath \ "to"
    val from = JsPath \ "from"

    "do nothing" when {
      "there is no old value" in {
        val userAnswers = UserAnswers("test")
        val result = userAnswers.migrate(from, to)

        result.get[String](from) mustBe None
        result.get[String](to) mustBe None
      }

      "there is only new" in {
        val userAnswers = UserAnswers("test").setOrFail(to, "DO_NOT_OVERRIDE")
        val result = userAnswers.migrate(from, to)

        result.get[String](from) mustBe None
        result.get[String](to) mustBe Some("DO_NOT_OVERRIDE")
      }
    }

    "move the old to the new" when {
      "the new is empty" in {
        val userAnswers = UserAnswers("test").setOrFail(from, "TEST")
        val result = userAnswers.migrate(from, to)

        result.get[String](to) mustBe Some("TEST")
      }
    }
    "not override existing new value with old" in {
      val userAnswers = UserAnswers("test").setOrFail(from, "TEST").setOrFail(to, "DO_NOT_OVERRIDE")
      val result = userAnswers.migrate(from, to)

      result.get[String](to) mustBe Some("DO_NOT_OVERRIDE")
    }

    "remove the old value" when {
      "it has been moved to the new" in {
        val userAnswers = UserAnswers("test").setOrFail(from, "TEST")
        val result = userAnswers.migrate(from, to)

        result.get[String](from) mustBe None
        result.get[String](to) mustBe Some("TEST")
      }
      "the new is already populated" in {
        val userAnswers = UserAnswers("test").setOrFail(from, "TEST").setOrFail(to, "DO_NOT_OVERRIDE")
        val result = userAnswers.migrate(from, to)

        result.get[String](from) mustBe None
        result.get[String](to) mustBe Some("DO_NOT_OVERRIDE")
      }
    }
  }

  "hasOldAnswers" must {

    val userAnswers = UserAnswers("id", data = obj(
      "obligation" -> obj(
        "fromDate" -> "2022-10-01",
        "toDate" -> "2022-12-31",
        "dueDate" -> "2023-02-28",
        "periodKey" -> "22C4"
      ),
      "isFirstReturn" -> false
    ))

    "be false if there's no old answers" in {
      sut.hasOldAnswers(userAnswers) mustBe false
    }

    "be true if there's an exported credit answer" in {
      sut.hasOldAnswers(userAnswers.setOrFail(ExportedCreditWeightGettable.path, 11L)) mustBe true
      sut.hasOldAnswers(userAnswers.setOrFail(ExportedCreditYesNoGettable.path, false)) mustBe true
    }

    "be true if there's a converted credit answer" in {
      sut.hasOldAnswers(userAnswers.setOrFail(ConvertedCreditWeightGettable.path, 11L)) mustBe true
      sut.hasOldAnswers(userAnswers.setOrFail(ConvertedCreditYesNoGettable.path, false)) mustBe true
    }
  }

  private def newUserAnswerData =
    """{
      |        "obligation" : {
      |            "fromDate" : "2023-01-01",
      |            "toDate" : "2023-03-31",
      |            "dueDate" : "2023-05-31",
      |            "periodKey" : "23C1"
      |        },
      |        "isFirstReturn" : false,
      |        "startYourReturn" : true,
      |        "whatDoYouWantToDo" : true,
      |        "credit" : {
      |            "2022-04-01-2022-12-31" : {
      |                "toDate" : "2022-12-31",
      |                "fromDate": "2022-04-01",
      |                "exportedCredits" : {
      |                    "yesNo" : true,
      |                    "weight" : 12
      |                },
      |                "convertedCredits" : {
      |                    "yesNo" : true,
      |                    "weight" : 34
      |                }
      |            }
      |        },
      |        "creditAvailableYears" : [
      |            {
      |                "from" : "2022-04-01",
      |                "to" : "2022-12-31"
      |            }
      |        ],
      |        "manufacturedPlasticPackaging" : false,
      |        "manufacturedPlasticPackagingWeight" : 0,
      |        "importedPlasticPackaging" : false,
      |        "importedPlasticPackagingWeight" : 0,
      |        "directlyExportedComponents" : false,
      |        "exportedPlasticPackagingWeight" : 0,
      |        "plasticExportedByAnotherBusiness" : false,
      |        "anotherBusinessExportWeight" : 0,
      |        "nonExportedHumanMedicinesPlasticPackaging" : false,
      |        "nonExportedHumanMedicinesPlasticPackagingWeight" : 0,
      |        "nonExportRecycledPlasticPackaging" : false,
      |        "nonExportRecycledPlasticPackagingWeight" : 0
      |    }""".stripMargin

}
