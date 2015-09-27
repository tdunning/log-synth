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
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.mapr.synth.samplers.SchemaSampler;
import org.junit.Test;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Created by tdunning on 9/25/15.
 */
public class CommuterTest {
    private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");

    @Test
    public void testSampleTrips() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.toString(Resources.getResource("schema027.json"), Charsets.UTF_8));
        JsonNode r = s.sample();
        verifyFields(r, ImmutableList.of("vehicle", "trip"));
        verifyFields(r.get("trip"), ImmutableList.of("home", "work", "data", "trips"));
        verifyFields(r.get("trip").get("home"), ImmutableList.of("zip", "longitude", "latitude"));
        verifyFields(r.get("trip").get("work"), ImmutableList.of("longitude", "latitude"));
        verifyFields(r.get("trip").get("data").get(0), ImmutableList.of("longitude", "latitude", "t", "timestamp", "mph", "rpm","throttle"));
        verifyFields(r.get("trip").get("trips").get(0), ImmutableList.of("distance_km", "duration", "t", "timestamp", "type"));

        assertTrue(String.format("Expected lots of detail but only saw %d records",
                r.get("trip").get("data").size()), r.get("trip").get("data").size() > 5000);
        assertTrue("But only a few trips", r.get("trip").get("trips").size() > 0);
        assertTrue("But only a few trips", r.get("trip").get("trips").size() < 50);
    }

    private void verifyFields(JsonNode jsonNode, Collection<String> expectedFields) {
        TreeSet<String> c = Sets.newTreeSet(Lists.newArrayList(jsonNode.fieldNames()));
        assertEquals(String.format("Expected fields %s but got %s", c.toString(), expectedFields.toString()),
                expectedFields.size(), Sets.intersection(c, Sets.newTreeSet(expectedFields)).size());
    }

    @Test
    public void testEvenHour() throws ParseException {
        Commuter c = new Commuter();
        double t1 = df.parse("2015-09-23 23:15:06 PDT").getTime() / 1000.0;
        double t2 = df.parse("2015-09-23 23:00:00 PDT").getTime() / 1000.0;
        assertEquals(String.format("Got %s", df.format(new Date((long) (c.evenHour(t1) * 1000)))), t2, c.evenHour(t1), 0);
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
            if (!c.isWeekend(t)) {
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
            if (!c.isWeekend(t)) {
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

        for (int i = 0; i < counts.length; i++) {
            int count = counts[i];
            assertEquals(10e3 / 24.0, count, 400);
        }
    }
}