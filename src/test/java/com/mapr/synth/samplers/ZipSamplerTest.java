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
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import org.junit.Test;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

import static org.junit.Assert.*;

public class ZipSamplerTest {

    private static final int N = 50000;

    @Test
    public void testZips() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema016.json"), Charsets.UTF_8).read());

        JsonNode v = s.sample();
        // regression test given that we specify the seed
        assertEquals("65529", v.get("z").get("zip").asText());

        Multiset<String> laCounts = HashMultiset.create();

        double latitude = 0;
        double longitude = 0;
        double latitudeFuzzy = 0;
        double longitudeFuzzy = 0;
        boolean allInside1 = true;
        boolean allInside2 = true;
        for (int i = 0; i < N; i++) {
            v = s.sample();
            double x = v.get("z").get("longitude").asDouble();
            double y = v.get("z").get("latitude").asDouble();

            Set<String> ss = Sets.newTreeSet();
            Iterator<String> it = v.get("zLimited").fieldNames();
            while (it.hasNext()) {
                String field = it.next();
                ss.add(field);
            }
            assertEquals("[latitude, longitude, state, zip]", ss.toString());

            longitude += x;
            latitude += y;
            allInside1 &= isContinental(x, y);

            x = v.get("zContinental").get("longitude").asDouble();
            y = v.get("zContinental").get("latitude").asDouble();
            allInside2 &= isContinental(x, y);

            x = v.get("zFuzzy").get("longitude").asDouble();
            y = v.get("zFuzzy").get("latitude").asDouble();
            longitudeFuzzy += x;
            latitudeFuzzy += y;

            laCounts.add(v.get("zLosAngeles").get("zip").asText());
            assertTrue("Unexpected zip code in LA", v.get("zLosAngeles").get("zip").asText().matches("(9[0123]...)|(89...)"));
        }

        assertFalse("Expected non-continental samples", allInside1);
        assertTrue("Should not have had non-continental samples", allInside2);

        longitude = longitude / N;
        latitude = latitude / N;

        longitudeFuzzy = longitudeFuzzy / N;
        latitudeFuzzy = latitudeFuzzy / N;

        // these expected values are the true means of all zip code locations
        assertEquals(-90.88465, longitude , 2);
        assertEquals(38.47346, latitude, 2);

        assertEquals(-90.88465, longitudeFuzzy , 7);
        assertEquals(38.47346, latitudeFuzzy, 5);

        assertEquals(1365, laCounts.elementSet().size(), 50);
    }

    @Test
    public void testBogusFieldLimit() throws IOException {
        try {
            new SchemaSampler(Resources.asCharSource(Resources.getResource("schema018.json"), Charsets.UTF_8).read());
            fail("Should have failed due to invalid fields");
        } catch (JsonMappingException e) {
            Preconditions.checkState(e.getCause() instanceof IllegalArgumentException, "Wrong exception");
        }

    }

    private boolean isContinental(double x, double y) {
        return y >= 22 && y <= 50 && x >= -130 && x <= -65;
    }
}