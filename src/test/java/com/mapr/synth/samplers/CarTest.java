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
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class CarTest {
    @Test
    public void testBasics() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.toString(Resources.getResource("schema031.json"), Charsets.UTF_8));

        double[] mean = new double[]{0, 0, 0};
        double[] sd = new double[]{0, 0, 0};

        for (int i = 0; i < 10000; i++) {
            // time to first few sensors is what we want to see ... since all cars
            // start at the same time, these should give us good averages
            JsonNode x = s.sample();
            for (int j = 0; j < 3; j++) {
                double t = x.get("tight").get(j).get("time").asDouble();
                double oldMean = mean[j];
                mean[j] += (t - mean[j]) / (i + 1);
                sd[j] += ((t - oldMean) * (t - mean[j]) - sd[j]) / (i + 1);
            }
        }
        assertEquals(60, mean[0], 3);
        assertEquals(90, mean[1], 3);
        assertEquals(120, mean[2], 3);
        assertEquals(5, Math.sqrt(sd[0]), 2);
        assertEquals(7.5, Math.sqrt(sd[1]), 2);
        assertEquals(10, Math.sqrt(sd[2]), 2);
    }

    @Test
    public void testArrivalTimes() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.toString(Resources.getResource("schema032.json"), Charsets.UTF_8));


    }

    @Test
    public void testSlowdown() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.toString(Resources.getResource("schema033.json"), Charsets.UTF_8));


    }
}
