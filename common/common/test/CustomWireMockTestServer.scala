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

package common.test

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

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.{MappingBuilder, WireMock}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.stubbing.StubMapping

class CustomWireMockTestServer {
  val wireHost = "localhost"
  lazy val server = new WireMockServer(options().dynamicPort())
  lazy val wirePort = port()

  def stubFor(mappingBuilder: MappingBuilder): StubMapping =
    server.stubFor(mappingBuilder)

  def overrideConfig: Map[String, Any] = {
    Map("microservice.services.eis.host" -> wireHost,
      "microservice.services.eis.port" -> wirePort,
      "microservice.services.nrs.host" -> wireHost,
      "microservice.services.nrs.port" -> wirePort,
      "microservice.services.des.host" -> wireHost,
      "microservice.services.des.port" -> wirePort
    )
  }

  def start(): Unit = {
    if(!server.isRunning) server.start()

    WireMock.configureFor(wireHost, server.port())
  }

  def stop() = server.stop()

  private def port() =  if(!server.isRunning) {start(); server.port()} else server.port()
}

object CustomWireMockTestServer {
  def apply() = new CustomWireMockTestServer
}
