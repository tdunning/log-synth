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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class FlatSequenceTest {
    @Test
    public void testFlattenedNames() throws IOException {
        SchemaSampler s1 = SchemaSampler.fromResource("schema033.json");
        SchemaSampler s2 = SchemaSampler.fromResource("schema034.json");

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

        // what is most important here is the fact that sequences restart cleanly so that similarly seeded
        // sequences get the right results regardless of flattening
        for (int i = 0; i < 100; i++) {
            JsonNode actual1 = s1.sample();
            JsonNode actual2 = s2.sample();
            assertEquals(actual1.get("data").get("time"), actual2.get("time"));
            assertEquals(actual1.get("data").get("temp"), actual2.get("temp"));
        }
    }

    @Test
    public void testCrossProduct() throws IOException {
        // here we verify the cross product result when we have multiple flattened sequences in a record
        SchemaSampler s1 = SchemaSampler.fromResource("schema035.json");
        Multiset<String> count = HashMultiset.create();

        for (int i = 0; i < 150; i++) {
            JsonNode r = s1.sample();
            assertEquals(r.get("a").asInt() + 100, r.get("b").asInt());
            assertEquals(r.get("foo").get("a").asInt() + 100, r.get("foo").get("b").asInt());

            count.add(String.format("a=%d", r.get("a").asInt()));
            count.add(String.format("a=%d, b=%d", r.get("a").asInt(), r.get("b").asInt()));
            count.add(String.format("a=%d, foo.a=%d", r.get("a").asInt(), r.get("foo").get("a").asInt()));
            count.add(String.format("foo.a=%d", r.get("foo").get("a").asInt()));
            count.add(String.format("foo.a=%d, foo.b=%d", r.get("foo").get("a").asInt(), r.get("foo").get("b").asInt()));
        }

        assertEquals(50, count.count("a=1"), 1.5);
        assertEquals(50, count.count("a=2"), 1.5);
        assertEquals(0, count.count("a=3"));
        assertEquals(75, count.count("foo.a=0"));
        assertEquals(75, count.count("foo.a=1"));
        assertEquals(0, count.count("foo.a=3"));
        assertEquals(25, count.count("a=0, foo.a=0"));
        assertEquals(75, count.count("foo.a=0, foo.b=100"));
        assertEquals(25, count.count("a=0, foo.a=1"));
        assertEquals(50, count.count("a=0, b=100"));
    }
}
