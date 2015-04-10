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
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FlattenSamplerTest {
    @Test
    public void testObject() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema022.json"), Charsets.UTF_8).read());

        JsonNode v = s.sample();
        Set<String> names = Sets.newTreeSet();
        Iterators.addAll(names, v.fieldNames());
        assertTrue(names.contains("q-zip"));
        assertTrue(names.contains("q-zipType"));
        assertTrue(names.contains("r-zip"));
        assertTrue(names.contains("r-zipType"));

        List<String> illegalFields = Lists.newArrayList();
        for (String name : names) {
            if (!name.matches("[qr]-.*")) {
                illegalFields.add(name);
            }
        }
        assertEquals(String.format("Expected all fields to start with q- or r-, but found %s", illegalFields),0, illegalFields.size());
    }

    @Test
    public void testEmptyPrefix() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema023.json"), Charsets.UTF_8).read());
        JsonNode v = s.sample();
        Set<String> names = Sets.newTreeSet();
        Iterators.addAll(names, v.fieldNames());
        assertEquals("[city, decommisioned, estimatedPopulation, latitude, location, locationType, longitude, state, " +
                "taxReturnsFiled, totalWages, zip, zipType]", names.toString());
    }

    @Test
    public void testFlatten() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema010.json"), Charsets.UTF_8).read());

        for (int k = 0; k < 10; k++) {
            JsonNode r = s.sample();
            assertEquals(k, r.get("id").asInt());
            assertTrue(r.get("stuff").isArray());
            assertEquals(1, r.get("stuff").get(0).asInt());
            assertEquals(2, r.get("stuff").get(1).asInt());
            assertEquals(3, r.get("stuff").get(2).asInt());
            assertEquals(4, r.get("stuff").get(3).asInt());
            assertEquals(4, r.get("stuff").size());
        }
    }
}
