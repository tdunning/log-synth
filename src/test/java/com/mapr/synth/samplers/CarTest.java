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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
        double meanDelay = 0;
        double exponentialMean = 0;
        double sd = 0;
        double previous = 0;
        for (int i = 0; i < 10000; i++) {
            // time to first sensor should show steady increase
            JsonNode x = s.sample();
            double t = x.get("delayed").get(0).get("time").asDouble();
            double delay = t - previous;
            double expt = Math.exp(-delay / 60);
            assertTrue(expt >= 0);
            assertTrue(expt <= 1);
            exponentialMean += expt;
            sd += (expt - 0.5) * (expt - 0.5);
            previous = t;
            meanDelay += delay;
        }
        meanDelay /= 10000;
        exponentialMean /= 10000;
        sd /= 10000;
        // exponential distribution should have correct mean
        assertEquals(60, meanDelay, 3);
        // and exp(delay) should be uniformly distributed
        assertEquals(0.5, exponentialMean, 0.02);
        assertEquals(1 / 12.0, sd, 0.002);
    }

    @Test
    public void testSlowdown() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.toString(Resources.getResource("schema033.json"), Charsets.UTF_8));

        Histogram[] histograms = new Histogram[7];
        for (int j = 0; j < 6; j++) {
            histograms[j] = new Histogram();
        }

        for (int i = 0; i < 500; i++) {
            // times between sensors should be tightly clustered
            // outside of the traffic jam, the clustering is all around a single value
            // in the traffic jam area, there should be two values.
            JsonNode x = s.sample();

            for (int j = 0; j < 6; j++) {
                double t0 = x.get("traffic").get(j).get("time").asDouble();
                double t1 = x.get("traffic").get(j + 1).get("time").asDouble();
                histograms[j].add(t1 - t0);
            }
        }
        assertEquals("Times from 1 to 2 km", 500, histograms[0].counts[60]);
        assertEquals("Times from 2 to 3 km", 500, histograms[1].counts[60]);
        assertEquals("Times from 3 to 4 km", 450.0, histograms[2].counts[60], 20);
        assertEquals("Times from 3 to 4 km", 50.0, histograms[2].counts[120], 20);
        assertEquals("Times from 4 to 5 km", 450.0, histograms[3].counts[60], 20);
        assertEquals("Times from 4 to 5 km", 50.0, histograms[3].counts[120], 20);
        assertEquals("Times from 5 to 6 km", 500, histograms[4].counts[60]);
        assertEquals("Times from 6 to 7 km", 500, histograms[5].counts[60]);
    }

    private class Histogram {
        int[] counts = new int[180];

        void add(double x) {
            int bin = (int) Math.rint(x);
            if (bin < 0) {
                counts[0]++;
            } else if (bin >= counts.length) {
                counts[counts.length - 1]++;
            } else {
                counts[bin]++;
            }
        }
    }

    @Test
    public void testSlowdownConstructor() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode j1 = mapper.readTree("{\"speed\":\"30 kph\", \"location\":\"3 km - 5 km\", \"time\": \"5min - 10min\"}");
        CarOnRoadSampler.SlowDown s1 = mapper.convertValue(j1, CarOnRoadSampler.SlowDown.class);
        assertEquals(30_000.0 / 3600, s1.getSpeed(), 1e-11);

        JsonNode j2 = mapper.readTree("[{\"speed\":\"30 kph\", \"location\":\"3 km - 5 km\", \"time\": \"5min - 10min\"}]");
        List<CarOnRoadSampler.SlowDown> s2 = mapper.convertValue(j2, new TypeReference<List<CarOnRoadSampler.SlowDown>>() {
        });
        assertEquals(s1, s2.get(0));
    }
}
