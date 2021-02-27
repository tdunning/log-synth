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

package com.mapr.synth.drive;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.mapr.synth.samplers.SchemaSampler;
import org.junit.Test;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Random;
import java.util.TimeZone;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CommuterTest {
    private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");

    @Test
    public void testSampleTrips() throws IOException {
        SchemaSampler s = SchemaSampler.fromResource("schema027.json");
        for (int i = 0; i < 20; i++) {
            JsonNode r = s.sample();
            verifyFields(r, ImmutableList.of("vehicle", "trip"));
            verifyFields(r.get("trip"), ImmutableList.of("home", "work", "trips"));
            verifyFields(r.get("trip").get("home"), ImmutableList.of("zip", "longitude", "latitude"));
            verifyFields(r.get("trip").get("work"), ImmutableList.of("longitude", "latitude"));
            verifyFields(r.get("trip").get("trips").get(0),
                    ImmutableList.of("data", "t", "start", "timestamp", "type", "distance_km", "duration"));
            verifyFields(r.get("trip").get("trips").get(0).get("data").get(0),
                    ImmutableList.of("longitude", "latitude", "t", "timestamp", "mph", "rpm", "throttle"));

            int total = 0;
            JsonNode trips = r.get("trip").get("trips");
            for (JsonNode trip : trips) {
                total += trip.get("data").size();
            }
            int n = r.get("trip").get("trips").size();
            System.out.printf("%d,%d\n", total, n);
            assertTrue(String.format("Expected lots of detail but only saw %d records", total), total > 500 && total < 100000);
            assertTrue(String.format("But only a few trips (saw %d)", n), n > 2 && n < 40);
        }
    }

    @Test
    public void testFlattened() throws IOException {
        SchemaSampler s = SchemaSampler.fromResource("schema028.json");
        Multiset<String> counts = HashMultiset.create();
        Multiset<Integer> runs = HashMultiset.create();

        String oldVin = "";
        int run = 0;
        for (int i = 0; i < 500000; i++) {
            JsonNode r = s.sample();
            verifyFields(r, Lists.newArrayList("sample", "vehicle"));
            verifyFields(r.get("sample"), Lists.newArrayList("home", "work", "t", "start", "timestamp", "type", "distance_km", "duration", "latitude", "longitude", "mph", "rpm", "throttle"));
            String vin = r.get("vehicle").asText();
            counts.add(vin);
            if (oldVin.equals(vin)) {
                run++;
            } else {
                if (oldVin.length() > 0) {
                    runs.add(run);
                }
                run = 0;
                oldVin = vin;
            }
        }
        if (run > 0) {
            runs.add(run);
        }

        for (String vin : counts.elementSet()) {
            int n = counts.count(vin);
            assertTrue(String.format("Vehicle samples = %d", n), n > 10 && n < 100000);
        }

        int max = 0;
        for (Integer r : runs.elementSet()) {
            max = Math.max(max, r);
        }

        assertEquals(100, counts.elementSet().size(), 20);
        assertEquals(counts.elementSet().size(), runs.size());
        int total = 0;
        for (int i = 0; i <= max + 10; i++) {
            if (i % 1000 == 999) {
                System.out.printf("%d\t%d\n", i - 999, total);
                total = 0;
            }
            total += runs.count(i);
        }
    }

    private void verifyFields(JsonNode jsonNode, Collection<String> expectedFields) {
        TreeSet<String> c = Sets.newTreeSet(Lists.newArrayList(jsonNode.fieldNames()));
        assertEquals(String.format("Expected fields %s but got %s", expectedFields.toString(), c.toString()),
                expectedFields.size(), Sets.intersection(c, Sets.newTreeSet(expectedFields)).size());
    }

    @Test
    public void testEvenHour() throws ParseException {
        Commuter c = new Commuter();
        double t1 = df.parse("2015-09-23 23:15:06 PDT").getTime() / 1000.0;
        double t2 = df.parse("2015-09-23 23:00:00 PDT").getTime() / 1000.0;
        assertEquals(String.format("Got %s", df.format(new Date(Util.evenHour(t1) * 1000))), t2, Util.evenHour(t1), 0);
    }

    @Test
    public void testHourOfDay() throws ParseException {
        Commuter c = new Commuter();
        double t1 = df.parse("2015-09-23 23:15:06 CDT").getTime() / 1000.0;
        assertEquals(23, c.hourOfDay(t1));
    }

    @Test
    public void testDriveHome() throws ParseException {
        Random rand = new Random();
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTimeZone(TimeZone.getTimeZone("US/Central"));

        Commuter c = new Commuter();
        double t = df.parse("2015-09-23 23:15:06 CDT").getTime() / 1000.0;
        int[] counts = new int[24];
        for (int i = 0; i < 100000; ) {
            double bump = -Math.log(1 - rand.nextDouble());
            t = c.search(false, t, bump);
            if (!Commuter.isWeekend(t)) {
                cal.setTimeInMillis((long) (t * 1000.0));
                counts[cal.get(GregorianCalendar.HOUR_OF_DAY)]++;
                i++;
            }
        }

        for (int i = 0; i < counts.length; i++) {
            int count = counts[i];
            if (i != 16 && i != 17) {
                assertEquals(100e3 * 2 / 64, count, 200);
            } else {
                assertEquals(100e3 * 10 / 64, count, 400);
            }
        }
    }

    @Test
    public void testToWork() throws ParseException {
        Random rand = new Random();
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTimeZone(TimeZone.getTimeZone("US/Central"));

        Commuter c = new Commuter();
        double t = df.parse("2015-09-23 23:15:06 CDT").getTime() / 1000.0;
        int[] counts = new int[24];
        for (int i = 0; i < 100000; ) {
            double bump = -Math.log(1 - rand.nextDouble());
            t = c.search(true, t, bump);
            if (!Commuter.isWeekend(t)) {
                cal.setTimeInMillis((long) (t * 1000.0));
                counts[cal.get(GregorianCalendar.HOUR_OF_DAY)]++;
                i++;
            }
        }

        for (int i = 0; i < counts.length; i++) {
            int count = counts[i];
            if (i != 7 && i != 8) {
                assertEquals(100e3 * 2 / 64, count, 200);
            } else {
                assertEquals(100e3 * 10 / 64, count, 400);
            }
        }
    }

    @Test
    public void testWeekend() throws ParseException {
        Random rand = new Random();
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTimeZone(TimeZone.getTimeZone("US/Central"));

        Commuter c = new Commuter();
        double t0 = df.parse("2015-09-26 0:0:06 CDT").getTime() / 1000.0;
        double t1 = df.parse("2015-09-28 0:0:00 CDT").getTime() / 1000.0;
        double t = t1 + 1;

        int[] counts = new int[24];
        for (int i = 0; i < 10000; ) {
            if (t >= t1) {
                t = t0 - (2 + rand.nextDouble()) * (t1 - t0);
            }
            double bump = -Math.log(1 - rand.nextDouble());
            t = c.search(false, t, bump);
            if (t >= t0 && t < t1) {
                cal.setTimeInMillis((long) (t * 1000.0));
                counts[cal.get(GregorianCalendar.HOUR_OF_DAY)]++;
                i++;
            }
        }

        for (int count : counts) {
            assertEquals(10e3 / 24.0, count, 400);
        }
    }
}