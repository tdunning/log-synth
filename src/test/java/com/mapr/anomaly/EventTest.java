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

package com.mapr.anomaly;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;

import static org.junit.Assert.assertEquals;

/**
 * Created with IntelliJ IDEA.
 * User: tdunning
 * Date: 10/27/13
 * Time: 8:10 PM
 * To change this template use File | Settings | File Templates.
 */
public class EventTest {
    @Test
    public void testRead() throws IOException, ParseException, Event.EventFormatException {
        BufferedReader in = new BufferedReader(new InputStreamReader(Resources.getResource("event.txt").openStream(), Charsets.UTF_8));
        Event ev = Event.read(in);
        assertEquals(444691, ev.getUid());
        assertEquals(1382920806122L, ev.getTime());
        assertEquals("static/image-4", ev.getOp());
        assertEquals(-599092377, ev.getIp());
        ev = Event.read(in);
        assertEquals(49664, ev.getUid());
        assertEquals(1382926154968L, ev.getTime());
        assertEquals("login", ev.getOp());
        assertEquals(950354974, ev.getIp());
    }
}
