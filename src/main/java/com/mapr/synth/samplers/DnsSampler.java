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
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mapr.synth.FancyTimeFormatter;
import com.mapr.synth.Util;
import com.mapr.synth.distributions.IpAddressDistribution;
import com.mapr.synth.distributions.LongTail;
import org.apache.mahout.math.jet.random.Exponential;
import org.apache.mahout.math.jet.random.Gamma;
import org.apache.mahout.math.random.Multinomial;

import java.io.IOException;
import java.net.InetAddress;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class DnsSampler extends FieldSampler {
    private static final long NIGHT_DURATION = TimeUnit.HOURS.toMillis(10);

    private JsonNodeFactory factory = JsonNodeFactory.withExactBigDecimals(false);
    private Random base = new Random();

    private LongTail<String> domainDistribution;
    private Set<String> retainedFields = null;

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
    private double end = System.currentTimeMillis();
    private double now = start;

    private IpAddressDistribution ip = new IpAddressDistribution();

    // distribution parameters for domain names
    private double alpha = 1000;
    private double discount = 0.3;

    private boolean isActive = false;

    // transitions to active happen in dilated time so that active sessions occur less often at night
    // but transition to inactive happen in real time so sessions at night are not longer than normal
    private double nextTransition = 0;

    // next query is always in real-time. This time is reset if a session ends before the next query
    private double nextQuery = 0;

    private double sunriseTime = base.nextDouble() * Util.ONE_DAY;
    private double sunsetTime = sunriseTime < NIGHT_DURATION ? sunriseTime - NIGHT_DURATION + Util.ONE_DAY : sunriseTime - NIGHT_DURATION;

    private boolean isDaytime = sunriseTime > NIGHT_DURATION;
    private Set<String> legalFields = ImmutableSet.of(
            "ip", "ipx", "ipV4", "domain", "revDomain", "time", "timestamp_ms", "timestamp_s");

    public DnsSampler() throws IOException {
        List<String> topNames = Lists.newArrayList();
        Splitter onComma = Splitter.on(',').trimResults(CharMatcher.is('"'));
        Util.readData("f500-domains.csv", line -> {
            Iterator<String> ix = onComma.split(line).iterator();
            ix.next();
            topNames.add(ix.next());
            return null;
        });
        Multinomial<String> tld = Util.readTable(onComma, "tld.csv");
        NameSampler names = new NameSampler(NameSampler.Type.LAST);
        domainDistribution = new LongTail<String>(alpha, discount) {
            int i = 0;

            @Override
            protected String createThing() {
                if (i < topNames.size()) {
                    return topNames.get(i++);
                } else {
                    return names.sample().asText() + tld.sample();
                }
            }
        };
        restart();
    }

    enum Event {
        SUNRISE, SUNSET,
        ACTIVATE, DEACTIVATE,
        QUERY, END
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
            double sunset = Util.dayOrigin(now) + sunriseTime - NIGHT_DURATION;
            if (sunset <= now) {
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
            return Event.QUERY;
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
            return Event.QUERY;
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
        active = new Exponential(1 / activeDistribution.nextDouble(), base);
        inActive = new Exponential(1 / inActiveDistribution.nextDouble(), base);
        interval = new Exponential(1.0 / meanIntervalDistribution.nextDouble(), base);
        idle = Math.exp(idleDistribution.nextDouble());

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

    /**
     * Limits the fields that are returned to only those that are specified.
     */
    @SuppressWarnings("UnusedDeclaration")
    public void setFields(String fields) {
        retainedFields = Sets.newHashSet(Splitter.on(Pattern.compile("[\\s,;]+")).split(fields));
        for (String field : retainedFields) {
            if (!legalFields.contains(field)) {
                throw new IllegalArgumentException(String.format("Unknown field name: %s", field));
            }
        }
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
        domainDistribution.getBaseDistribution().setAlpha(alpha);
    }

    @SuppressWarnings("unused")
    public void setDiscount(double discount) {
        this.discount = discount;
        domainDistribution.getBaseDistribution().setDiscount(discount);
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

    @SuppressWarnings("unused")
    public void setV4Prob(double ipV4Probability) {
        this.ip.setIpV4Probability(ipV4Probability);
    }

    public void setSeed(long seed) {
        base.setSeed(seed);
    }

    @SuppressWarnings("unused")
    public void setFlat(boolean isFlat) {
        setFlattener(isFlat);
    }

    @Override
    public void getNames(Set<String> fields) {
        if (isFlat()) {
            //noinspection StringEquality
            if (this.getName() == null || this.getName() == SchemaSampler.FLAT_SEQUENCE_MARKER) {
                fields.addAll(legalFields);
                if (retainedFields != null) {
                    fields.retainAll(retainedFields);
                }
            } else {
                fields.add(this.getName());
            }
        }
    }

    @Override
    public JsonNode sample() {
        restart();

        InetAddress address = ip.sample();

        ObjectNode r = new ObjectNode(factory);
        // set basics ... source IP and such
        r.set("ip", new TextNode(address.toString().substring(1)));
        byte[] addressBits = address.getAddress();
        Formatter ip = new Formatter();
        for (byte x : addressBits) {
            ip.format("%02x", x);
        }
        r.set("ipx", new TextNode(ip.toString()));
        r.set("ipV4", BooleanNode.valueOf(addressBits.length == 4));
        if (retainedFields != null) {
            r.retain(retainedFields);
        }


        ArrayNode queries = new ArrayNode(factory);

        Event step;
        do {
            step = step();
            if (step == Event.QUERY) {
                ObjectNode q = new ObjectNode(factory);
                String domain = domainDistribution.sample();
                q.set("domain", new TextNode(domain));
                List<String> parts = Arrays.asList(domain.split("\\."));
                Collections.reverse(parts);
                String reversed = String.join(".", parts);
                q.set("revDomain", new TextNode(reversed));
                q.set("time", new TextNode(df.format((long) now)));
                q.set("timestamp_ms", new LongNode((long) now));
                q.set("timestamp_s", new LongNode((long) (now / 1000)));
                if (retainedFields != null) {
                    q.retain(retainedFields);
                }
                queries.add(q);
            }
        } while (step != Event.END);

        if (!isFlat()) {
            r.set("queries", queries);
            return r;
        } else {
            ArrayNode flattened = new ArrayNode(factory);
            for (JsonNode q : queries) {
                ObjectNode x = r.deepCopy();
                x.setAll((ObjectNode) q);
                flattened.add(x);
            }

            return flattened;
        }
    }
}
