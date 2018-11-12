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
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class IntegerSamplerTest {
    @Test
    public void testStringSetter() throws Exception {
        IntegerSampler s = new IntegerSampler();
        s.setMinAsInt(10);
        s.setMax(new TextNode("1K"));
        assertEquals(10, s.getMin());
        assertEquals(1000, s.getMax());
    }

    @Test
    public void testFormatAndUniform() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema030.json"), Charsets.UTF_8).read());
        int[] count1 = new int[1000], count2 = new int[1000];
        for (int i = 0; i < 1000000; i++) {
            JsonNode f = s.sample();
            JsonNode x1 = f.get("formatted");
            assertTrue(x1.isTextual());
            count1[Integer.parseInt(x1.asText(), 16)]++;

            JsonNode x2 = f.get("raw");
            assertTrue(x2.isInt());
            count2[x2.asInt()]++;
        }

        int total1 = 0;
        int total2 = 0;
        for (int i = 0; i < 1000; i++) {
            assertEquals(1000, count1[i], 5 * 30);
            total1 += count1[i];
            assertEquals(1000, count2[i], 5 * 30);
            total2 += count2[i];
        }
        assertEquals(1000000, total1);
        assertEquals(1000000, total2);
    }
}