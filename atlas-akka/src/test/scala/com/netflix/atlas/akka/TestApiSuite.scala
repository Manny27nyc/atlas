/*
 * Copyright 2014-2022 Netflix, Inc.
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
package com.netflix.atlas.akka

import akka.http.scaladsl.testkit.RouteTestTimeout
import com.netflix.atlas.akka.testkit.MUnitRouteSuite

class TestApiSuite extends MUnitRouteSuite {

  import scala.concurrent.duration._

  implicit val routeTestTimeout = RouteTestTimeout(5.second)

  val endpoint = new TestApi(system)
  val routes = RequestHandler.standardOptions(endpoint.routes)

  test("/query-parsing-directive") {
    Get("/query-parsing-directive?regex=a|b|c") ~> routes ~> check {
      assertEquals(responseAs[String], "a|b|c")
    }
  }

  test("/query-parsing-explicit") {
    Get("/query-parsing-explicit?regex=a|b|c") ~> routes ~> check {
      assertEquals(responseAs[String], "a|b|c")
    }
  }

  test("/chunked") {
    Get("/chunked") ~> routes ~> check {
      assertEquals(response.status.intValue, 200)
      assertEquals(chunks.size, 42)
    }
  }

}
