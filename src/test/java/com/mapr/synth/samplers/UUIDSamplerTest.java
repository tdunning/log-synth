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
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.io.Resources;
import org.junit.Test;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UUIDSamplerTest {
    private static final int N = 100000;
    static Pattern uuidFormat = Pattern.compile("\\p{XDigit}{8}-\\p{XDigit}{4}-(\\p{XDigit}{4})-(\\p{XDigit}{4})-\\p{XDigit}{12}");

    @Test
    public void testBasics() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema017.json"), Charsets.UTF_8).read());
        Multiset<String> counts = HashMultiset.create();
        for (int i = 0; i < N; i++) {
            JsonNode x = s.sample();
            // u1 has a specified seed, u2 does not
            checkUUID(counts, x.get("u1").asText());
            checkUUID(counts, x.get("u2").asText());
        }

        // verify uniqueness
        assertEquals(2 * N, counts.elementSet().size());
    }

    private void checkUUID(Multiset<String> counts, String uuid) {
        counts.add(uuid);
        Matcher m = uuidFormat.matcher(uuid);
        assertTrue(m.matches());
        assertEquals("4", m.group(1).substring(0, 1));
        assertTrue(m.group(2).matches("[89ab].*"));
    }
}