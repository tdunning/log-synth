/*
 * Licensed to the Ted Dunning under one or more contributor license
 * agreements.  See the NOTICE file that may be
 * distributed with this work for additional information
 * regarding copyright ownership.  Ted Dunning licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.mapr.stats;

import org.apache.mahout.common.RandomUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Random;

public class UpperQuantileTest {
    Logger log = LoggerFactory.getLogger(this.getClass());

    private double[] data;
    private UpperQuantile uq;

    @Test()
    public void testEmptyData() {
        IllegalStateException exception = Assertions.assertThrows(
                IllegalStateException.class, () -> new UpperQuantile(1000).quantile(0.5));
        Assertions.assertTrue(exception.getMessage().contains("no data"));
    }

    @Test()
    public void testGoofyQuantileNegative() {
        IllegalArgumentException exception = Assertions.assertThrows(
                IllegalArgumentException.class, () -> uq.quantile(-1));
        Assertions.assertTrue(exception.getMessage().contains(">= 0"));
    }

    @Test()
    public void testGoofyQuantileGreaterThan1() {
        IllegalArgumentException exception = Assertions.assertThrows(
                IllegalArgumentException.class, () -> uq.quantile(1.2));
        Assertions.assertTrue(exception.getMessage().contains("<= 1"));
    }

    @Test
    public void testGoofyQuantileTooLow() {
        IllegalArgumentException exception = Assertions.assertThrows(
                IllegalArgumentException.class, () -> uq.quantile(0.5));
        Assertions.assertTrue(exception.getMessage().contains("only retained"));
    }

    @Test
    public void testQuantiles() {
        Assertions.assertEquals(data[900], uq.quantile(0.9), 0);
        Assertions.assertEquals(data[1000], uq.quantile(1), 0);
        Assertions.assertEquals(data[950], uq.quantile(0.95), 0);
        Assertions.assertEquals(data[950] * 0.8 + data[951] * 0.2, uq.quantile(0.9502), 0);
    }

    @Test
    public void testSpeed() {
        long total = 0;
        UpperQuantile data = new UpperQuantile(5000);
        Random gen = RandomUtils.getRandom();
        for (int i = 0; i < 10000; i++) {
            data.add(gen.nextDouble());
        }
        data.clear();
        int n = 100000;
        for (int i = 0; i < n; i++) {
            double x = gen.nextDouble();
            long t0 = System.nanoTime();
            data.add(x);
            long t1 = System.nanoTime();
            total += t1 - t0;
        }
        // time per insert should be less than a micro-second.  Typically this actually comes out ~300 ns
        log.debug("t = {} us", total / 1e9 / n / 1e-6);
        Assertions.assertTrue(total / 1e9 / n < 100e-6);

        total = 0;
        for (int i = 0; i < 10; i++) {
            double q = gen.nextDouble() * 0.01 + 0.99;
            long t0 = System.nanoTime();
            double r = data.quantile(q);
            long t1 = System.nanoTime();
            Assertions.assertEquals(q, r, 0.01, String.format("q=%.3f r=%.3f i=%d", q, r, i));
            total += t1 - t0;
        }
        log.debug("t = {} us", total / 1e9 / 10 / 1e-6);
    }

    @BeforeEach
    public void generate() {
        RandomUtils.useTestSeed();
        uq = new UpperQuantile(101);
        data = new double[1001];
        Random gen = RandomUtils.getRandom();
        for (int i = 0; i < 1001; i++) {
            double x = gen.nextDouble();
            data[i] = x;
            uq.add(x);
        }
        Arrays.sort(data);
    }
}
