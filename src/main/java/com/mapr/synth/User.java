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

import com.google.common.collect.Lists;
import com.mapr.synth.distributions.TermGenerator;
import org.apache.mahout.common.RandomUtils;
import org.apache.mahout.common.RandomWrapper;
import org.apache.mahout.math.jet.random.AbstractContinousDistribution;
import org.apache.mahout.math.jet.random.Exponential;

import java.net.InetAddress;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Samples from a user space.  Each user has a variety of internal characteristics.
 *
 * Users are sorted according to the time of their next session and then by id to break ties.
 */
public class User implements Comparable<User> {
    private static final AtomicInteger idCounter = new AtomicInteger();
    private static final Random cookieGenerator = RandomUtils.getRandom();
    private int id;

    private AbstractContinousDistribution queryLengthDistribution = new Exponential(0.4, RandomUtils.getRandom());
    private AbstractContinousDistribution sessionTimeDistribution;

    private long cookie;

    private TermGenerator terms;
    private InetAddress address;
    private String geoCode;
    private double rate;

    private double nextSession;
    private AbstractContinousDistribution queryTimeDistribution = new Exponential(1.0 / 120, RandomUtils.getRandom());
    private AbstractContinousDistribution sessionLengthDistribution = new Exponential(1.0 / 4, RandomUtils.getRandom());

    public User(InetAddress address, String geoCode, TermGenerator terms, double period) {
        this.terms = terms;
        this.geoCode = geoCode;
        this.address = address;
        this.rate = period;
        cookie = cookieGenerator.nextLong();

        this.sessionTimeDistribution = new Exponential(period, RandomUtils.getRandom());

        id = idCounter.addAndGet(1);
        nextSession = sessionTimeDistribution.nextDouble();
    }

    public InetAddress getAddress() {
        return address;
    }

    public long getCookie() {
        return cookie;
    }

    public List<String> getQuery() {
        int n = queryLengthDistribution.nextInt() + 1;
        List<String> r = Lists.newArrayList();
        for (int i = 0; i < n; i++) {
            r.add(terms.sample());
        }
        return r;
    }

    public String getGeoCode() {
        return geoCode;
    }

    public double getNextSession() {
        return nextSession;
    }

    public void session(PriorityQueue<LogLine> eventBuffer) {
        int sessionLength = (int) (sessionLengthDistribution.nextDouble() + 1);
        double t = nextSession;
        for (int i = 0; i < sessionLength; i++) {
            eventBuffer.add(new LogLine(t, this));
            t += queryTimeDistribution.nextDouble();
        }
        nextSession += sessionTimeDistribution.nextDouble();
    }

    @Override
    public String toString() {
        return String.format("{\"ip\":\"%s\", \"cookie\":\"%08x\", \"geo\":\"%s\"}", address.getHostAddress(), cookie, geoCode);
    }

    public int compareTo(User o) {
        int r = Double.compare(this.nextSession, o.nextSession);
        if (r != 0) {
            return r;
        } else {
            return this.id - o.id;
        }
    }
}
