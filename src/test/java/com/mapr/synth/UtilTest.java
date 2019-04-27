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

package com.mapr.synth;

import org.junit.Before;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class UtilTest {
    private static SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    static {
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private double t0;
    private double t1;
    private double t2;
    private double t3;
    private double t4;

    @Before
    public void setUp() throws Exception {
        t0 = df.parse("2019-03-09 00:00:00").getTime();
        t1 = df.parse("2019-03-09 00:00:00").getTime();
        t2 = df.parse("2019-03-09 07:32:01").getTime();
        t3 = df.parse("2019-03-09 18:32:01").getTime();
        t4 = df.parse("2019-03-09 23:59:59").getTime();
    }

    @Test
    public void isDaytime() {
        assertFalse(Util.isDaytime(t1, Util.ONE_DAY * 0.2, Util.ONE_DAY * 0.7));
        assertTrue(Util.isDaytime(Util.timeOfDay(t2), Util.ONE_DAY * 0.2, Util.ONE_DAY * 0.7));
        assertFalse(Util.isDaytime(Util.timeOfDay(t3), Util.ONE_DAY * 0.2, Util.ONE_DAY * 0.7));
        assertFalse(Util.isDaytime(Util.timeOfDay(t4), Util.ONE_DAY * 0.2, Util.ONE_DAY * 0.7));

        assertTrue(Util.isDaytime(Util.timeOfDay(t1), Util.ONE_DAY * 0.7, Util.ONE_DAY * 0.2));
        assertFalse(Util.isDaytime(Util.timeOfDay(t2), Util.ONE_DAY * 0.7, Util.ONE_DAY * 0.2));
        assertTrue(Util.isDaytime(Util.timeOfDay(t3), Util.ONE_DAY * 0.7, Util.ONE_DAY * 0.2));
        assertTrue(Util.isDaytime(Util.timeOfDay(t4), Util.ONE_DAY * 0.7, Util.ONE_DAY * 0.2));
    }

    @Test
    public void dayOrigin() {
        assertEquals(t0, Util.dayOrigin(t1), 0);
        assertEquals(t0, Util.dayOrigin(t2), 0);
        assertEquals(t0, Util.dayOrigin(t3), 0);
        assertEquals(t0, Util.dayOrigin(t4), 0);
    }

    @Test
    public void timeOfDay() {
        assertEquals(0, Util.timeOfDay(t1), 0);
        assertEquals((7.0 + (32.0 + 1.0 / 60) / 60) / 24 * Util.ONE_DAY, Util.timeOfDay(t2), 0);
        assertEquals((18.0 + (32.0 + 1.0 / 60) / 60) / 24 * Util.ONE_DAY, Util.timeOfDay(t3), 0);
        assertEquals((1 - 1.0 / 3600 / 24) * Util.ONE_DAY, Util.timeOfDay(t4), 0);
    }
}