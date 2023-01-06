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

import uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation.{IdentityData, NonRepudiationMetadata}
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EdgeOfSystem

import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime

class NrsPayload(edgeOfSystem: EdgeOfSystem, payload: Array[Byte]) {

  def createMetaData(notableEvent: String, pptReference: String, userHeaders: Map[String, String], identityData: IdentityData,
    userAuthToken: String, submissionTimestamp: ZonedDateTime): NonRepudiationMetadata = {
    
    val payloadChecksum = calculatePayloadChecksum()
    NonRepudiationMetadata.create(notableEvent, pptReference, userHeaders, identityData,
      userAuthToken, payloadChecksum, submissionTimestamp)
  }

  def encodePayload(): String =
    edgeOfSystem.createEncoder
      .encodeToString(payload)

  def calculatePayloadChecksum(): String =
    edgeOfSystem.getMessageDigestSingleton
      .digest(payload)
      .map("%02x".format(_))
      .mkString

}

object NrsPayload {

  def apply(edgeOfSystem: EdgeOfSystem, payloadString: String): NrsPayload = {
    val payloadBytes: Array[Byte] = payloadString.getBytes(StandardCharsets.UTF_8)
    new NrsPayload(edgeOfSystem, payloadBytes)
  }
}
