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

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class NormalSamplerTest {
    @Test
    public void meanAndSd() throws IOException {
        SchemaSampler s = SchemaSampler.fromResource("schema039.json");

        double[] s1 = new double[]{0, 0, 0}, s2 = new double[]{0, 0, 0};
        for (int i = 0; i < 10000; i++) {
            JsonNode record = s.sample();
            update(s1, record.get("x").asDouble());
            update(s2, record.get("y").asDouble());
        }
        assertEquals(10000, s1[0], 0);
        assertEquals(1, s1[1], 0.1);
        assertEquals(3.1, Math.sqrt(s1[2] / s1[0]), 0.1);

        assertEquals(10000, s2[0], 0);
        assertEquals(1, s2[1], 0.1);
        assertEquals(1 / 3.1, Math.sqrt(s2[2] / s2[0]), 0.01);
    }

    @Test
    public void seed() throws Exception {
        SchemaSampler s = SchemaSampler.fromResource("schema040.json");
        JsonNode record = s.sample();
        assertEquals(-0.8853660817212665, record.get("x").asDouble(), 1e-9);
        assertEquals(2.484814790861627, record.get("y").asDouble(), 1e-6);
    }

    private void update(double[] s, double x) {
        s[0]++;
        double m = s[1] + (x - s[1]) / s[0];
        s[2] += (x - m) * (x - s[1]);
        s[1] = m;
    }

    @Test
    public void trim() throws Exception {
        SchemaSampler s = SchemaSampler.fromResource("schema041.json");
        for (int i = 0; i < 10000; i++) {
            JsonNode record = s.sample();
            double x = record.get("x").asDouble();
            assertTrue(x >= 0);
            assertTrue(x <= 2);
        }
    }

    @Test
    public void illegalParams() throws Exception {
        try {
            SchemaSampler.fromResource("schema042.json");
            fail();
        } catch (JsonMappingException e) {
            assertTrue(e.getMessage().startsWith("Parameter max must be greater than min"));
        }
        try {
            SchemaSampler s = SchemaSampler.fromResource("schema043.json");
            fail();
        } catch (JsonMappingException e) {
            assertTrue(e.getMessage().startsWith("Value of max-min is too small"));
        }
    }
}