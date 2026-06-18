/*
 * Copyright 2026 HM Revenue & Customs
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

import play.api.{Logger, MarkerContext}
import org.slf4j.LoggerFactory

class CapturingLogger extends Logger(LoggerFactory.getLogger("test")) {
  val warnings = scala.collection.mutable.ListBuffer[String]()
  val errors   = scala.collection.mutable.ListBuffer[String]()
  val infos    = scala.collection.mutable.ListBuffer[String]()

  override def warn(message: => String)(implicit mc: MarkerContext): Unit = warnings += message

  override def error(message: => String)(implicit mc: MarkerContext): Unit = errors += message

  override def info(message: => String)(implicit mc: MarkerContext): Unit = infos += message

  def clear(): Unit = {
    warnings.clear()
    errors.clear()
    infos.clear()
  }

}
