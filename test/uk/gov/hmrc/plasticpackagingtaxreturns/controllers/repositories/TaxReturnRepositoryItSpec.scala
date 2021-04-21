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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.repositories

import com.codahale.metrics.{MetricFilter, SharedMetricRegistries, Timer}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.inject.guice.GuiceApplicationBuilder
import reactivemongo.api.ReadConcern
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.builders.TaxReturnBuilder
import uk.gov.hmrc.plasticpackagingtaxreturns.models.TaxReturn
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.TaxReturnRepository

import scala.concurrent.ExecutionContext.Implicits.global

class TaxReturnRepositoryItSpec
    extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterEach with IntegrationPatience
    with TaxReturnBuilder {

  private val injector = {
    SharedMetricRegistries.clear()
    GuiceApplicationBuilder().injector()
  }

  private val repository = injector.instanceOf[TaxReturnRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    SharedMetricRegistries.clear()
    repository.removeAll().futureValue
  }

  private def collectionSize: Int =
    repository.collection
      .count(selector = None, limit = Some(0), skip = 0, hint = None, readConcern = ReadConcern.Local)
      .futureValue
      .toInt

  private def givenTaxReturnExists(taxReturns: TaxReturn*): Unit =
    repository.collection.insert(ordered = true).many(taxReturns).futureValue

  "Create" should {
    "persist the tax return" in {
      val taxReturn = aTaxReturn()
      repository.create(taxReturn).futureValue mustBe taxReturn

      collectionSize mustBe 1
    }
  }

  "Update" should {
    "update the tax return" in {
      givenTaxReturnExists(aTaxReturn())
      val taxReturn = aTaxReturn(withManufacturedPlasticWeight(totalKg = 5, totalKgBelowThreshold = 4),
                                 withImportedPlasticWeight(totalKg = 3, totalKgBelowThreshold = 2),
                                 withHumanMedicinesPlasticWeight(totalKg = 1),
                                 withConvertedPlasticPackagingCredit(totalPence = 1010)
      )

      repository.update(taxReturn).futureValue mustBe Some(taxReturn)

      // this indicates that a timer has started and has been stopped
      getTimer("ppt.returns.mongo.update").getCount mustBe 1

      collectionSize mustBe 1
    }

    "do nothing for missing tax return" in {
      val taxReturn = aTaxReturn()

      repository.update(taxReturn).futureValue mustBe None

      collectionSize mustBe 0

      getTimer("ppt.returns.mongo.update").getCount mustBe 1
    }
  }

  "Find by ID" should {
    "return the persisted tax return" when {
      "one exists with ID" in {
        val taxReturn = aTaxReturn(withManufacturedPlasticWeight(totalKg = 5, totalKgBelowThreshold = 4),
                                   withImportedPlasticWeight(totalKg = 3, totalKgBelowThreshold = 2),
                                   withHumanMedicinesPlasticWeight(totalKg = 1),
                                   withConvertedPlasticPackagingCredit(totalPence = 1010),
                                   withDirectExportDetails(totalKg = 8, totalValueForCreditInPence = 99),
                                   withMetadata(returnCompleted = true)
        )

        givenTaxReturnExists(taxReturn)

        repository.findById(taxReturn.id).futureValue mustBe Some(taxReturn)

        // this indicates that a timer has started and has been stopped
        getTimer("ppt.returns.mongo.find").getCount mustBe 1
      }
    }

    "return None" when {
      "none exist with id" in {
        val taxReturn1 = aTaxReturn(withId("some-other-id"))
        val taxReturn2 = aTaxReturn()
        givenTaxReturnExists(taxReturn1, taxReturn2)

        repository.findById("non-existing-id").futureValue mustBe None

        getTimer("ppt.returns.mongo.find").getCount mustBe 1
      }
    }
  }

  "Delete" should {
    "remove the tax return" in {
      val taxReturn = aTaxReturn()
      givenTaxReturnExists(taxReturn)

      repository.delete(taxReturn).futureValue

      collectionSize mustBe 0
    }

    "maintain other tax return" when {
      "they have a different ID" in {
        val taxReturn1 = aTaxReturn()
        val taxReturn2 = aTaxReturn(withId("id1"))
        val taxReturn3 = aTaxReturn(withId("id2"))
        givenTaxReturnExists(taxReturn2, taxReturn3)

        repository.delete(taxReturn1).futureValue

        collectionSize mustBe 2
      }
    }
  }

  private def getTimer(name: String): Timer =
    SharedMetricRegistries
      .getOrCreate("plastic-packaging-tax-returns")
      .getTimers(MetricFilter.startsWith(name))
      .get(name)

}
