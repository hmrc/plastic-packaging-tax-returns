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

package uk.gov.hmrc.plasticpackagingtaxreturns.models

import uk.gov.hmrc.plasticpackagingtaxreturns.util.EdgeOfSystem

import java.nio.charset.StandardCharsets
import java.util.Base64

class NrsPayload(edgeOfSystem: EdgeOfSystem, payloadString: String) {

  def encodePayload(): String = Base64.getEncoder.encodeToString(payloadString.getBytes(StandardCharsets.UTF_8))

  def retrievePayloadChecksum(): String =
    edgeOfSystem.getMessageDigestSingleton
      .digest(payloadString.getBytes(StandardCharsets.UTF_8))
      .map("%02x".format(_))
      .mkString

}
