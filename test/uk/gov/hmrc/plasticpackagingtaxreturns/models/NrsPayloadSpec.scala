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

import org.mockito.ArgumentMatchersSugar.any
import org.mockito.MockitoSugar.{mock, reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EdgeOfSystem

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

class NrsPayloadSpec extends PlaySpec with BeforeAndAfterEach {

  private val blahInBytes = Array[Byte](0x62, 0x6c, 0x61, 0x68) // 'blah' in utf8 hex

  private val edgeOfSystem = mock[EdgeOfSystem]
  private val messageDigest = mock[MessageDigest]
  private val encoder = mock[Base64.Encoder]
  private val nrsPayload = new NrsPayload(edgeOfSystem, "blah")

  override protected def beforeEach(): Unit = { 
    super.beforeEach()
    reset(edgeOfSystem, messageDigest)
    when(edgeOfSystem.getMessageDigestSingleton) thenReturn messageDigest
    when(edgeOfSystem.createEncoder) thenReturn encoder
  }

  "NrsPayload" should {
    
    "calculatePayloadChecksum" in {
      when(messageDigest.digest(any)) thenReturn Array[Byte](1, 2, 3)
      nrsPayload.calculatePayloadChecksum() mustBe "010203"
      verify(edgeOfSystem).getMessageDigestSingleton
      verify(messageDigest).digest(blahInBytes)
    }
    
    "encodePayload" in {
      when(encoder.encodeToString(any)) thenReturn "kirby"
      nrsPayload.encodePayload() mustBe "kirby"
      verify(encoder).encodeToString(blahInBytes)
    }
    
  }
}
