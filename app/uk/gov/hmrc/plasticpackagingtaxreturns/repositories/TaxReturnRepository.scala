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

package uk.gov.hmrc.plasticpackagingtaxreturns.repositories

import java.util.concurrent.TimeUnit

import com.codahale.metrics.Timer
import com.google.inject.ImplementedBy
import com.kenshoo.play.metrics.Metrics
import com.mongodb.client.model.Indexes.ascending
import javax.inject.Inject
import org.joda.time.DateTime
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.models.TaxReturn

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[TaxReturnRepositoryImpl])
trait TaxReturnRepository {
  def findById(id: String): Future[Option[TaxReturn]]
  def create(taxReturn: TaxReturn): Future[TaxReturn]
  def update(taxReturn: TaxReturn): Future[Option[TaxReturn]]
  def delete(taxReturn: TaxReturn): Future[Unit]
}

class TaxReturnRepositoryImpl @Inject() (mongoComponent: MongoComponent, appConfig: AppConfig, metrics: Metrics)(
  implicit ec: ExecutionContext
) extends PlayMongoRepository[TaxReturn](collectionName = "taxReturns",
                                           mongoComponent = mongoComponent,
                                           domainFormat = MongoSerialisers.format,
                                           indexes = Seq(
                                             IndexModel(ascending("lastModifiedDateTime"),
                                                        IndexOptions().name("ttlIndexReturns").expireAfter(
                                                          appConfig.dbTimeToLiveInSeconds,
                                                          TimeUnit.SECONDS
                                                        )
                                             ),
                                             IndexModel(ascending("id"), IndexOptions().name("idIdx").unique(true))
                                           ),
                                           replaceIndexes = true
    ) with TaxReturnRepository {

  private def filter(id: String) =
    equal("id", Codecs.toBson(id))

  private def newMongoDBTimer(name: String): Timer = metrics.defaultRegistry.timer(name)

  override def findById(id: String): Future[Option[TaxReturn]] = {
    val findStopwatch = newMongoDBTimer("ppt.returns.mongo.find").time()
    collection.find(filter(id)).headOption().andThen {
      case _ => findStopwatch.stop()
    }
  }

  override def create(taxReturn: TaxReturn): Future[TaxReturn] = {
    val createStopwatch  = newMongoDBTimer("ppt.returns.mongo.create").time()
    val updatedTaxReturn = taxReturn.updateLastModified()
    collection.insertOne(updatedTaxReturn).toFuture()
      .andThen {
        case _ => createStopwatch.stop()
      }
      .map(_ => updatedTaxReturn)
  }

  override def update(taxReturn: TaxReturn): Future[Option[TaxReturn]] = {
    val updateStopwatch  = newMongoDBTimer("ppt.returns.mongo.update").time()
    val updatedTaxReturn = taxReturn.updateLastModified()
    collection.replaceOne(filter(taxReturn.id), updatedTaxReturn).toFuture().map(
      updateResult => if (updateResult.getModifiedCount == 1) Some(updatedTaxReturn) else None
    ).andThen {
      case _ => updateStopwatch.stop()
    }
  }

  override def delete(taxReturn: TaxReturn): Future[Unit] = {
    val deleteStopwatch = newMongoDBTimer("ppt.returns.mongo.delete").time()
    collection.deleteOne(filter(taxReturn.id)).toFuture()
      .andThen {
        case _ => deleteStopwatch.stop()
      }.map(_ => Unit)
  }

}

object MongoSerialisers {

  implicit val mongoDateTimeFormat: Format[DateTime] =
    uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.dateTimeFormat

  implicit val format: Format[TaxReturn] = Json.format[TaxReturn]
}
