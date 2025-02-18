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

package org.apache.hadoop.ipc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.hadoop.ipc.WeightedRoundRobinMultiplexer.IPC_CALLQUEUE_WRRMUX_WEIGHTS_KEY;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestWeightedRoundRobinMultiplexer {
  public static final Logger LOG =
      LoggerFactory.getLogger(TestWeightedRoundRobinMultiplexer.class);

  private WeightedRoundRobinMultiplexer mux;

  @Test
  public void testInstantiateNegativeMux() {
    assertThrows(IllegalArgumentException.class, () -> {
      mux = new WeightedRoundRobinMultiplexer(-1, "", new Configuration());
    });
  }

  @Test
  public void testInstantiateZeroMux() {
    assertThrows(IllegalArgumentException.class, () -> {
      mux = new WeightedRoundRobinMultiplexer(0, "", new Configuration());
    });
  }

  @Test
  public void testInstantiateIllegalMux() {
    assertThrows(IllegalArgumentException.class, ()->{
      Configuration conf = new Configuration();
      conf.setStrings("namespace." + IPC_CALLQUEUE_WRRMUX_WEIGHTS_KEY,
          "1", "2", "3");
      // ask for 3 weights with 2 queues
      mux = new WeightedRoundRobinMultiplexer(2, "namespace", conf);
    });
  }

  @Test
  public void testLegalInstantiation() {
    Configuration conf = new Configuration();
    conf.setStrings("namespace." + IPC_CALLQUEUE_WRRMUX_WEIGHTS_KEY,
      "1", "2", "3");

    // ask for 3 weights with 3 queues
    mux = new WeightedRoundRobinMultiplexer(3, "namespace.", conf);
  }

  @Test
  public void testDefaultPattern() {
    // Mux of size 1: 0 0 0 0 0, etc
    mux = new WeightedRoundRobinMultiplexer(1, "", new Configuration());
    for(int i = 0; i < 10; i++) {
      assertThat(mux.getAndAdvanceCurrentIndex()).isZero();
    }

    // Mux of size 2: 0 0 1 0 0 1 0 0 1, etc
    mux = new WeightedRoundRobinMultiplexer(2, "", new Configuration());
    assertThat(mux.getAndAdvanceCurrentIndex()).isZero();
    assertThat(mux.getAndAdvanceCurrentIndex()).isZero();
    assertThat(mux.getAndAdvanceCurrentIndex()).isOne();
    assertThat(mux.getAndAdvanceCurrentIndex()).isZero();
    assertThat(mux.getAndAdvanceCurrentIndex()).isZero();
    assertThat(mux.getAndAdvanceCurrentIndex()).isOne();

    // Size 3: 4x0 2x1 1x2, etc
    mux = new WeightedRoundRobinMultiplexer(3, "", new Configuration());
    assertThat(mux.getAndAdvanceCurrentIndex()).isZero();
    assertThat(mux.getAndAdvanceCurrentIndex()).isZero();
    assertThat(mux.getAndAdvanceCurrentIndex()).isZero();
    assertThat(mux.getAndAdvanceCurrentIndex()).isZero();
    assertThat(mux.getAndAdvanceCurrentIndex()).isOne();
    assertThat(mux.getAndAdvanceCurrentIndex()).isOne();
    assertThat(mux.getAndAdvanceCurrentIndex()).isEqualTo(2);
    assertThat(mux.getAndAdvanceCurrentIndex()).isZero();

    // Size 4: 8x0 4x1 2x2 1x3
    mux = new WeightedRoundRobinMultiplexer(4, "", new Configuration());
    assertThat(mux.getAndAdvanceCurrentIndex()).isZero();
    assertThat(mux.getAndAdvanceCurrentIndex()).isZero();
    assertThat(mux.getAndAdvanceCurrentIndex()).isZero();
    assertThat(mux.getAndAdvanceCurrentIndex()).isZero();
    assertThat(mux.getAndAdvanceCurrentIndex()).isZero();
    assertThat(mux.getAndAdvanceCurrentIndex()).isZero();
    assertThat(mux.getAndAdvanceCurrentIndex()).isZero();
    assertThat(mux.getAndAdvanceCurrentIndex()).isZero();
    assertThat(mux.getAndAdvanceCurrentIndex()).isOne();
    assertThat(mux.getAndAdvanceCurrentIndex()).isOne();
    assertThat(mux.getAndAdvanceCurrentIndex()).isOne();
    assertThat(mux.getAndAdvanceCurrentIndex()).isOne();
    assertThat(mux.getAndAdvanceCurrentIndex()).isEqualTo(2);
    assertThat(mux.getAndAdvanceCurrentIndex()).isEqualTo(2);
    assertThat(mux.getAndAdvanceCurrentIndex()).isEqualTo(3);
    assertThat(mux.getAndAdvanceCurrentIndex()).isZero();
  }

  @Test
  public void testCustomPattern() {
    // 1x0 1x1
    Configuration conf = new Configuration();
    conf.setStrings("test.custom." + IPC_CALLQUEUE_WRRMUX_WEIGHTS_KEY,
      "1", "1");

    mux = new WeightedRoundRobinMultiplexer(2, "test.custom", conf);
    assertThat(mux.getAndAdvanceCurrentIndex()).isZero();
    assertThat(mux.getAndAdvanceCurrentIndex()).isOne();
    assertThat(mux.getAndAdvanceCurrentIndex()).isZero();
    assertThat(mux.getAndAdvanceCurrentIndex()).isOne();

    // 1x0 3x1 2x2
    conf.setStrings("test.custom." + IPC_CALLQUEUE_WRRMUX_WEIGHTS_KEY,
      "1", "3", "2");

    mux = new WeightedRoundRobinMultiplexer(3, "test.custom", conf);

    for(int i = 0; i < 5; i++) {
      assertThat(mux.getAndAdvanceCurrentIndex()).isZero();
      assertThat(mux.getAndAdvanceCurrentIndex()).isOne();
      assertThat(mux.getAndAdvanceCurrentIndex()).isOne();
      assertThat(mux.getAndAdvanceCurrentIndex()).isOne();
      assertThat(mux.getAndAdvanceCurrentIndex()).isEqualTo(2);
      assertThat(mux.getAndAdvanceCurrentIndex()).isEqualTo(2);
    } // Ensure pattern repeats

  }
}