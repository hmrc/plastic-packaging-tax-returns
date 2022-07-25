/*
 * Copyright 2022 HM Revenue & Customs
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

package support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.{MappingBuilder, WireMock}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.stubbing.StubMapping

class WiremockItServer {
  val wireHost                            = "localhost"
  lazy val wireMockServer: WireMockServer = new WireMockServer(options().dynamicPort())
  lazy val wirePort: Int                  = wireMockServer.port()

  def stubFor(mappingBuilder: MappingBuilder): StubMapping =
    wireMockServer.stubFor(mappingBuilder)

  def overrideConfig: Map[String, Any] =
    Map(
      "microservice.services.eis.host" -> wireHost,
      "microservice.services.eis.port" -> wirePort,
      "microservice.services.nrs.host" -> wireHost,
      "microservice.services.nrs.port" -> wirePort,
      "microservice.services.des.host" -> wireHost,
      "microservice.services.des.port" -> wirePort,
      "auditing.enabled" -> false
    )

  def start(): Unit = {
    if (!wireMockServer.isRunning) wireMockServer.start()

    WireMock.configureFor(wireHost, wireMockServer.port())
  }

  def stop(): Unit = wireMockServer.stop()

  def reset(): Unit = wireMockServer.resetAll()
}

object WiremockItServer {
  def apply() = new WiremockItServer()
}
