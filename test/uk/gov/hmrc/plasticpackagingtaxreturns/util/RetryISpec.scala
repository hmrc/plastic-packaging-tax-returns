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

package uk.gov.hmrc.plasticpackagingtaxreturns.util

import akka.actor.ActorSystem
import com.codahale.metrics.SharedMetricRegistries
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.DefaultAwaitTimeout
import play.api.test.Helpers.await
import uk.gov.hmrc.plasticpackagingtaxreturns.util.RetryISpec.{neverRetry, noParticularReason, retryFailures, someRetries}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

class RetryISpec extends AnyWordSpec with Matchers with DefaultAwaitTimeout with BeforeAndAfterEach {

  private implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  private val retryable = new Retry() {
    override def actorSystem: ActorSystem = ActorSystem()
  }

  override protected def beforeEach(): Unit =
    SharedMetricRegistries.clear()

  "A retryable" should {

    "not retry" when {
      "configured as such" in {
        val consistentlyFailingOperation = ConsistentlyFailingOperation()

        intercept[IllegalStateException] {
          await(retryable.retry()(retryFailures, noParticularReason)(consistentlyFailingOperation.doIt()))
        }

        consistentlyFailingOperation.invocationCount mustBe 1
      }
    }

    "retry a number of times" when {
      "failure occurs" in {

        val consistentlyFailingOperation = ConsistentlyFailingOperation()

        intercept[IllegalStateException] {
          await(retryable.retry(someRetries: _*)(retryFailures, noParticularReason)(consistentlyFailingOperation.doIt()))
        }

        consistentlyFailingOperation.invocationCount mustBe (someRetries.length + 1)
      }
    }

    "retry until successful" when {
      "failure occurs initially" in {

        val temporarilyFailingOperation = TemporarilyFailingOperation(1, "All Good")

        await(retryable.retry(someRetries: _*)(retryFailures, noParticularReason)(temporarilyFailingOperation.doIt()))

        temporarilyFailingOperation.invocationCount mustBe (temporarilyFailingOperation.numFailures + 1)
      }
    }

    "fail immediately" when {
      "no retry is required" in {
        val consistentlyFailingOperation = ConsistentlyFailingOperation()

        intercept[IllegalStateException] {
          await(retryable.retry(someRetries: _*)(neverRetry, noParticularReason)(consistentlyFailingOperation.doIt()))
        }

        consistentlyFailingOperation.invocationCount mustBe 1
      }
    }

    "delay before retrying" in {
      val consistentlyFailingOperation = ConsistentlyFailingOperation()

      val retryDelayMs         = 1000
      val timeBeforeInvocation = System.currentTimeMillis()

      intercept[IllegalStateException] {
        await(
          retryable.retry(FiniteDuration(retryDelayMs, TimeUnit.MILLISECONDS))(retryFailures, noParticularReason)(consistentlyFailingOperation.doIt())
        )
      }

      consistentlyFailingOperation.lastInvocationTime should be > (timeBeforeInvocation + retryDelayMs)
    }
  }
}

class TemporarilyFailingOperation[A](val numFailures: Int, val successVal: A) {
  var invocationCount          = 0
  var lastInvocationTime: Long = -1

  def doIt(): Future[A] = {
    invocationCount += 1
    lastInvocationTime = System.currentTimeMillis()
    if (numFailures == -1 || invocationCount <= numFailures)
      Future.failed(new IllegalStateException("BANG!"))
    else
      Future.successful(successVal)
  }

}

object RetryISpec {

  def retryFailures[A](value: Try[A]): Boolean =
    value match {
      case Success(_) => false
      case _          => true
    }

  //noinspection ScalaUnusedSymbol
  def neverRetry[A](value: Try[A]): Boolean = false

  //noinspection ScalaUnusedSymbol
  def noParticularReason[A](value: Try[A]): String = "no particular reason"

  def someRetries =
    List(FiniteDuration(1, TimeUnit.MILLISECONDS), FiniteDuration(2, TimeUnit.MILLISECONDS), FiniteDuration(3, TimeUnit.MILLISECONDS))

}

object TemporarilyFailingOperation {

  def apply[A](numFailures: Int, successVal: A): TemporarilyFailingOperation[A] =
    new TemporarilyFailingOperation(numFailures, successVal)

}

class ConsistentlyFailingOperation extends TemporarilyFailingOperation(-1, "All good")

object ConsistentlyFailingOperation {
  def apply[A](): ConsistentlyFailingOperation = new ConsistentlyFailingOperation()
}
