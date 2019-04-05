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
import com.google.common.collect.Multiset;
import com.google.common.collect.TreeMultiset;
import com.google.common.io.Resources;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class VinSamplerTest {
    @Test
    public void testSchema() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema014.json"), Charsets.UTF_8).read());

        Multiset<String> prefixCounts = TreeMultiset.create();
        Multiset<String> otherCounts = TreeMultiset.create();
        for (int i = 0; i < 100; i++) {
            JsonNode r = s.sample();
            assertEquals(r.get("v1").asText(), r.get("v2").get("VIN").asText());
            prefixCounts.add(r.get("v1").asText().substring(0, 2));
            otherCounts.add(r.get("v3").asText().substring(0, 2));
            System.out.printf("%s\n", r);
        }
        assertEquals("[1F, 2F, 3F]", prefixCounts.elementSet().toString());
        assertEquals("[2F, 3F]", otherCounts.elementSet().toString());
    }

    @Test
    public void testDump() throws FileNotFoundException {
        VinSampler vs = new VinSampler();
        vs.setMakes("ford");
        vs.setYears("2007-2011");
        vs.setCountries("us");
        vs.setVerbose(true);
        vs.setSeed(13);

        for (int i = 0; i < 10; i++) {
            JsonNode vin = vs.sample();
            assertTrue(vin.get("manufacturer").asText().contains("Ford"));
            assertTrue(vin.get("VIN").asText().startsWith("1F"));
            int year = vin.get("year").asInt();
            assertTrue(year >= 2007 && year <= 2011);
        }

        vs = new VinSampler();
        vs.setMakes("ford");
        vs.setYears("2007-2011");
        vs.setCountries("north_america");
        vs.setVerbose(true);
        vs.setSeed(13);

        Multiset<String> prefixCounts = TreeMultiset.create();

        for (int i = 0; i < 100; i++) {
            JsonNode vin = vs.sample();
            assertTrue(vin.get("manufacturer").asText().contains("Ford"));
            prefixCounts.add(vin.get("VIN").asText().substring(0, 2));
            int year = vin.get("year").asInt();
            assertTrue(year >= 2007 && year <= 2011);
        }

        assertEquals("[1F, 2F, 3F]", prefixCounts.elementSet().toString());
    }


    @Test
    public void testCheckDigit() throws FileNotFoundException {
        // test cases from http://introcs.cs.princeton.edu/java/31datatype/VIN.java.html
        // % java VIN 1B4YEM9P4KP186543
        //         Invalid
        //
        // % java VIN 1FA-CP45E-X-LF192944
        // Valid
        //
        //         % java VIN 1FA-CP45E-6-LF192944
        // Invalid
        //
        //         % java VIN QFA-CP45E-X-LF192944
        // Exception in thread "main" java.lang.RuntimeException: Illegal character: Q
        //
        //         % java VIN 1FA-CP45E-G-LF192944
        // Exception in thread "main" java.lang.RuntimeException: Illegal check digit: G
        //
        //         % java VIN 1FA-CP45E-X-LF19294
        // Exception in thread "main" java.lang.RuntimeException: VIN number must be 17 characters


        VinSampler vs = new VinSampler();
        String[] vins = {"1B4YEM9P4KP186543", "1FACP45EXLF192944", "1FACP45E6LF192944", "1FACP45EGLF192944", "11111111111111111"};
        boolean[] check = {false, true, false, false, true, true};

        for (int i = 0; i < vins.length; i++) {
            assertEquals(check[i], vins[i].equals(vs.addCheckDigit(vins[i])));
        }

        try {
            vs.addCheckDigit("QFACP45EXLF192944");
            fail("Should have failed with IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Invalid character"));
        }
    }
}