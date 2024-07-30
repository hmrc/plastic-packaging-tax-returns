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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.pattern.after
import play.api.Logger

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait Retry {
  private val logger = Logger(getClass)
  implicit protected def actorSystem: ActorSystem

  def retry[A](
    intervals: FiniteDuration*
  )(shouldRetry: Try[A] => Boolean, retryReason: Try[A] => String)(block: => Future[A])(implicit
    ec: ExecutionContext
  ): Future[A] = {
    def loop(remainingIntervals: Seq[FiniteDuration])(block: => Future[A]): Future[A] =
      block.flatMap(result =>
        if (remainingIntervals.nonEmpty && shouldRetry(Success(result))) {
          val delay = remainingIntervals.head
          logger.warn(s"Retrying in $delay due to ${retryReason(Success(result))}")
          after(delay, actorSystem.scheduler)(loop(remainingIntervals.tail)(block))
        } else {
          logger.info("Success!")
          Future.successful(result)
        }
      )
        .recoverWith {
          case e: Throwable =>
            if (remainingIntervals.nonEmpty && shouldRetry(Failure(e))) {
              val delay = remainingIntervals.head
              logger.warn(s"Retrying in $delay due to ${retryReason(Failure(e))}")
              after(delay, actorSystem.scheduler)(loop(remainingIntervals.tail)(block))
            } else
              Future.failed(e)
        }
    loop(intervals)(block)
  }

}
