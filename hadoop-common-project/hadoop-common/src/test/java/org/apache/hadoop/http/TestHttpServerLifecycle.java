/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.http;

import org.junit.jupiter.api.Test;

public class TestHttpServerLifecycle extends HttpServerFunctionalTest {

  /**
   * Check that a server is alive by probing the {@link HttpServer2#isAlive()} method
   * and the text of its toString() description
   * @param server server
   */
  private void assertAlive(HttpServer2 server) {
    assertTrue(server.isAlive(), "Server is not alive");
    assertToStringContains(server, HttpServer2.STATE_DESCRIPTION_ALIVE);
  }

  private void assertNotLive(HttpServer2 server) {
    assertTrue(!server.isAlive(), "Server should not be live");
    assertToStringContains(server, HttpServer2.STATE_DESCRIPTION_NOT_LIVE);
  }

  /**
   * Test that the server is alive once started
   *
   * @throws Throwable on failure
   */
  @Test public void testCreatedServerIsNotAlive() throws Throwable {
    HttpServer2 server = createTestServer();
    assertNotLive(server);
  }

  @Test public void testStopUnstartedServer() throws Throwable {
    HttpServer2 server = createTestServer();
    stop(server);
  }

  /**
   * Test that the server is alive once started
   *
   * @throws Throwable on failure
   */
  @Test
  public void testStartedServerIsAlive() throws Throwable {
    HttpServer2 server = null;
    server = createTestServer();
    assertNotLive(server);
    server.start();
    assertAlive(server);
    stop(server);
  }

  /**
   * Assert that the result of {@link HttpServer2#toString()} contains the specific text
   * @param server server to examine
   * @param text text to search for
   */
  private void assertToStringContains(HttpServer2 server, String text) {
    String description = server.toString();
    assertTrue(description.contains(text),
        "Did not find \"" + text + "\" in \"" + description + "\"");
  }

  /**
   * Test that the server is not alive once stopped
   *
   * @throws Throwable on failure
   */
  @Test public void testStoppedServerIsNotAlive() throws Throwable {
    HttpServer2 server = createAndStartTestServer();
    assertAlive(server);
    stop(server);
    assertNotLive(server);
  }

  /**
   * Test that the server is not alive once stopped
   *
   * @throws Throwable on failure
   */
  @Test public void testStoppingTwiceServerIsAllowed() throws Throwable {
    HttpServer2 server = createAndStartTestServer();
    assertAlive(server);
    stop(server);
    assertNotLive(server);
    stop(server);
    assertNotLive(server);
  }

  /**
   * Test that the server is alive once started
   * 
   * @throws Throwable
   *           on failure
   */
  @Test
  public void testWepAppContextAfterServerStop() throws Throwable {
    HttpServer2 server = null;
    String key = "test.attribute.key";
    String value = "test.attribute.value";
    server = createTestServer();
    assertNotLive(server);
    server.start();
    server.setAttribute(key, value);
    assertAlive(server);
    assertEquals(value, server.getAttribute(key));
    stop(server);
    assertNull(server.getAttribute(key), "Server context should have cleared");
  }
}
