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
import com.mapr.synth.FancyTimeFormatter;
import com.mapr.synth.Util;
import org.apache.mahout.common.RandomUtils;

import java.text.ParseException;
import java.util.Date;
import java.util.Random;

/**
 * Samples progressive times that look like event arrival times.
 * <p>
 * You can set the
 * <p>
 * <il>
 * <li><em>rate</em> - use something like 5/m to indicate 5 events per minute.  The unit is optional, seconds are the
 * default.</li>
 * <li><em>offset</em> - the minimum time between events, default is 0</li>
 * <li><em>format </em>- the format to use when outputting the times</li>
 * <li><em>start </em>- the time of the first event</li>
 * </il>
 * <p>
 * Thread safe
 */
public class ArrivalSampler extends FieldSampler {
    private Random base;

    private double meanInterval = 1000;  // interval - offset will have this mean
    private double minInterval = 0;      // no interval can be less than this
    private FancyTimeFormatter df = new FancyTimeFormatter("yyyy-MM-dd");

    private double start = System.currentTimeMillis();
    private double now = start;

    public ArrivalSampler() {
        base = RandomUtils.getRandom();
    }

    @Override
    public void restart() {
        now = start;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setRate(String rate) {
        meanInterval = Util.parseRateAsInterval(rate);
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setOffset(double offset) {
        minInterval = offset;
    }

    @SuppressWarnings("unused")
    public void setFormat(String format) {
        df = new FancyTimeFormatter(format);
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setStart(String start) throws ParseException {
        this.start = df.parse(start).getTime();
        this.now = this.start;
    }

    public void setSeed(long seed) {
        base = RandomUtils.getRandom(seed);
    }

    @Override
    public JsonNode sample() {
        synchronized (this) {
            TextNode r = new TextNode(df.format(new Date((long) now)));
            double interval = -meanInterval * Math.log(1.0 - base.nextDouble());
            now += (minInterval + interval);
            return r;
        }
    }
}
