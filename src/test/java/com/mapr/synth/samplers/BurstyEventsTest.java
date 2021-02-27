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

package com.mapr.synth.samplers;

import com.fasterxml.jackson.databind.JsonNode;
import com.mapr.synth.Util;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertTrue;

public class BurstyEventsTest {
    @Test
    public void roughDistribution() throws IOException {
        SchemaSampler s = SchemaSampler.fromResource("schema038.json");

        int[] counts = new int[24];
        for (int i = 0; i < 10000; i++) {
            JsonNode x = s.sample().get("b1");
            double t = x.get("timestamp_ms").asDouble();
            counts[(int) (24 * Util.fractionalPart(t / Util.ONE_DAY))]++;
        }
        for (int i = 0; i < 24; i++) {
            System.out.printf("%5d,%5d ", i, counts[i]);
            if (i >= 1 && i < 19) {
                System.out.print("N\n");
                assertTrue("Too much activity in the night", counts[i] < 100);
            } else             if (i < 1 || i >= 20) {
                System.out.print("D\n");
                assertTrue("Too little activity during the day", counts[i] > 500);
            } else {
                System.out.print("\n");
            }
        }
    }
}