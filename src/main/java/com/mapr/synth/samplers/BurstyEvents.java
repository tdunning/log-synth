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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.mapr.synth.FancyTimeFormatter;
import com.mapr.synth.Util;
import com.mapr.synth.distributions.WrappedNormal;
import org.apache.mahout.math.jet.random.Exponential;
import org.apache.mahout.math.jet.random.Gamma;

import java.text.ParseException;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class BurstyEvents extends FieldSampler {
    // this gives a 5 hour active day per user
    private static final long NIGHT_DURATION = TimeUnit.HOURS.toMillis(19);

    private JsonNodeFactory factory = JsonNodeFactory.withExactBigDecimals(false);
    private Random base = new Random();

    private FieldSampler value;

    // most internal parameters will be resampled on each restart
    // in some cases, these are actually a distribution that is reparametrized on each restart and sampled each transaction
    private Gamma dilationDistribution = new Gamma(6, 1, base);
    private double dilation;

    // distribution of active times
    private Exponential activeDistribution = new Exponential(1.0 / TimeUnit.MINUTES.toMillis(10), base);
    private Exponential active;

    // distribution of inactive times
    private Exponential inActiveDistribution = new Exponential(1.0 / TimeUnit.HOURS.toMillis(4), base);
    private Exponential inActive;

    // distribution of query times
    private Exponential meanIntervalDistribution = new Exponential(1.0 / TimeUnit.MINUTES.toMillis(1), base);
    private Exponential interval;   // interval sampled from this

    // distribution of how much slower queries go slower when inactive
    private Gamma idleDistribution = new Gamma(6, 1.0, base);
    private double idle;

    private FancyTimeFormatter df = new FancyTimeFormatter();

    private double start = System.currentTimeMillis() - 3 * Util.ONE_DAY;
    private double end = Double.MAX_VALUE;
    private double now = start;

    private boolean isActive = false;

    // transitions to active happen in dilated time so that active sessions occur less often at night
    // but transition to inactive happen in real time so sessions at night are not longer than normal
    private double nextTransition = 0;

    // next query is always in real-time. This time is reset if a session ends before the next query
    private double nextQuery = 0;

    // emulates roughly a US dominated audience with almost a 4:1 peak to valley ratio for the distribution
    // of sunrise times. The actual times of the bursts will be more spread out than this due the length of
    // days. Note that we are talking about GMT here so 1900GMT = 0800 EST = 1100 PST
    private WrappedNormal sunriseGenerator = new WrappedNormal(
            Util.ONE_DAY,
            TimeUnit.HOURS.toMillis(19),
            TimeUnit.HOURS.toMillis(5));
    private double sunriseTime = sunriseGenerator.nextDouble();
    private double sunsetTime = sunriseTime < NIGHT_DURATION ? sunriseTime - NIGHT_DURATION + Util.ONE_DAY : sunriseTime - NIGHT_DURATION;

    private boolean isDaytime = Util.isDaytime(now, sunriseTime, sunsetTime);

    public BurstyEvents() {
        restart();
    }

    public void setValue(FieldSampler value) {
        this.value = value;
    }

    enum Event {
        SUNRISE, SUNSET,
        ACTIVATE, DEACTIVATE,
        ACTION, END
    }

    /**
     * Step to the next event. This hard-coded method is simpler than the normal sort of priority queue of events
     * because of the time dilation that we have at night.
     *
     * @return The next event.
     */
    private Event step() {
        if (now > end) {
            return Event.END;
        }
        double timeOfDay = Util.timeOfDay(now);
        isDaytime = Util.isDaytime(timeOfDay, sunriseTime, sunsetTime);
        if (isDaytime) {
            // during the day, time passes in real-time
            double sunset = Util.dayOrigin(now) + sunsetTime;
            while (sunset <= now) {
                sunset += Util.ONE_DAY;
            }
            if (sunset < nextTransition) {
                return handleSunEvent(sunset, Event.SUNSET, dilation);
            } else {
                return getNextEvent();
            }
        } else {
            double sunrise = Util.dayOrigin(now) + sunriseTime;
            if (sunrise <= now) {
                sunrise += Util.ONE_DAY;
            }

            // if inactive at night, time is dilated
            if (sunrise < nextTransition) {
                return handleSunEvent(sunrise, Event.SUNRISE, 1.0 / dilation);
            } else {
                return getNextEvent();
            }
        }
    }

    private Event handleSunEvent(double t, Event event, double scale) {
        if (t < nextQuery) {
            now = t + 1;
            if (!isActive) {
                // start dilating time for query and state transition
                double remainder = nextQuery - now;
                nextQuery = now + scale * remainder;

                remainder = nextTransition - now;
                nextTransition = now + scale * remainder;
            }
            isDaytime = !isDaytime;
            return event;
        } else {
            now = nextQuery;
            nextQuery = getNextQueryTime();
            return Event.ACTION;
        }
    }

    private Event getNextEvent() {
        if (nextTransition < nextQuery) {
            // state transition
            now = nextTransition;

            // change of state gives us an entirely new next query time
            return flipActivation();
        } else {
            // query comes first
            now = nextQuery;
            nextQuery = getNextQueryTime();
            return Event.ACTION;
        }
    }

    private Event flipActivation() {
        if (isActive) {
            isActive = false;
            nextQuery = getNextQueryTime();
            nextTransition = getNextTransition();
            return Event.DEACTIVATE;
        } else {
            isActive = true;
            nextQuery = getNextQueryTime();
            nextTransition = getNextTransition();
            return Event.ACTIVATE;
        }
    }

    private double getNextTransition() {
        if (isActive) {
            return now + active.nextDouble();
        } else {
            double delay = inActive.nextDouble();
            if (!isDaytime) {
                delay *= dilation;
            }
            return now + delay;
        }
    }

    private double getNextQueryTime() {
        double delay;
        delay = interval.nextDouble();
        if (!isActive) {
            delay = interval.nextDouble() * idle;
        }
        if (!isDaytime) {
            delay = interval.nextDouble() * dilation;
        }
        return now + delay;
    }

    @Override
    public void restart() {
        now = start;
        dilation = Math.exp(dilationDistribution.nextDouble());
        if (Double.isInfinite(dilation) || dilation > 1e4) {
            System.out.printf("Extreme dilation: %.2f\n", dilation);
        }
        active = new Exponential(1 / activeDistribution.nextDouble(), base);
        inActive = new Exponential(1 / inActiveDistribution.nextDouble(), base);
        interval = new Exponential(1.0 / meanIntervalDistribution.nextDouble(), base);
        idle = Math.exp(idleDistribution.nextDouble());
        if (Double.isInfinite(idle) || idle > 1e4) {
            System.out.printf("Extreme idle factor: %.2f\n", dilation);
        }

        sunriseTime = sunriseGenerator.nextDouble();
        sunsetTime = sunriseTime < NIGHT_DURATION ? sunriseTime - NIGHT_DURATION + Util.ONE_DAY : sunriseTime - NIGHT_DURATION;

        // we start inactive
        isDaytime = Util.isDaytime(Util.timeOfDay(now), sunriseTime, sunsetTime);
        isActive = false;
        nextTransition = getNextTransition();
        nextQuery = getNextQueryTime();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setRate(String rate) {
        meanIntervalDistribution = new Exponential(Util.parseRateAsInterval(rate), base);
    }

    @SuppressWarnings("unused")
    public void setFormat(String format) {
        df = new FancyTimeFormatter(format);
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setStart(String start) throws ParseException {
        this.start = df.parse(start).getTime();
    }

    public void setEnd(String end) throws ParseException {
        this.end = df.parse(end).getTime();
    }

    @Override
    public void setSeed(long seed) {
        base.setSeed(seed + 1);
        sunriseGenerator.setSeed(seed);
        restart();
        if (value != null) {
            value.setSeed(seed);
        }
    }

    @Override
    public void getNames(Set<String> fields) {
        if (value != null) {
            value.getNames(fields);
        }
        fields.add("time");
        fields.add("timestamp_s");
        fields.add("timestamp_ms");
    }


    @Override
    public JsonNode sample() {
        JsonNode r;
        if (value != null) {
            r = value.sample();
        } else {
            r = new ObjectNode(factory);
        }

        if (r instanceof ObjectNode) {
            if (!addTimeFields((ObjectNode) r)) {
                restart();
            }
        } else if (r instanceof ArrayNode) {
            for (JsonNode x : r) {
                if (!addTimeFields((ObjectNode) x)) {
                    restart();
                    break;
                }
            }
        } else {
            throw new IllegalStateException("Impossible case with JSON value: " + r.toString());
        }
        return r;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean addTimeFields(ObjectNode x) {
        Event e = null;
        while (e != Event.ACTION) {
            e = step();
//            System.out.printf("%8s/%-9s %11s %s %8.2f %8.2f %8.2f %8.2f\n",
//                    Util.isDaytime(Util.timeOfDay(now), sunriseTime, sunsetTime) ? "day" : "night",
//                    isActive ? "active" : "inactive",
//                    e, df.format((long) now),
//                    (nextQuery - now) / TimeUnit.HOURS.toMillis(1),
//                    (nextTransition - now) / TimeUnit.HOURS.toMillis(1),
//                    24 * Util.fractionalPart((sunriseTime - Util.timeOfDay(now)) / TimeUnit.HOURS.toMillis(24)),
//                    24 * Util.fractionalPart((sunsetTime - Util.timeOfDay(now)) / TimeUnit.HOURS.toMillis(24))
//            );
            if (e == Event.END) {
                return false;
            }
        }
        x.set("time", new TextNode(df.format((long) now)));
        x.set("timestamp_ms", new LongNode((long) now));
        x.set("timestamp_s", new LongNode((long) (now / 1000)));
        return true;
    }
}
