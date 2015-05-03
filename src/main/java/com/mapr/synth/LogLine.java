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

import com.google.common.collect.ImmutableSet;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.util.Formatter;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A log line contains a user id, an IP address and a query.
 */
public class LogLine implements Comparable<LogLine> {
    private static AtomicInteger counter = new AtomicInteger();

    // these add up to enough to cause 0.3% of the queries to be 5x slower
    private static final Set<String> slowWords = ImmutableSet.of("company", "office", "boss", "law",
            "chocolate", "drinking", "table", "english");
    private final Random rand = new Random();

    private InetAddress ip;
    private long cookie;
    private List<String> query;
    private double t;
    private final double responseTime;
    private int id = counter.addAndGet(1);

    public LogLine(double t, InetAddress ip, long cookie, List<String> query) {
        this.t = t;
        this.cookie = cookie;
        this.ip = ip;
        this.query = query;
        this.responseTime = sampleResponseTime(query);
    }

    private double sampleResponseTime(List<String> query) {
        double mean = 0;
        for (String s : query) {
            if (slowWords.contains(s)) {
                mean = Math.max(mean, 50e-3 + rand.nextGaussian() * 10e-3);
            } else {
                mean = Math.max(mean, 10e-3 + rand.nextGaussian() * 2e-3);
            }
        }
        return Math.exp(Math.log(mean) + rand.nextGaussian() / 3);
    }

    public LogLine(double t, User user) {
        this(t, user.getAddress(), user.getCookie(), user.getQuery());
    }

    public String toString() {
        Formatter r = new Formatter();
        r.format("{t: %.3f, cookie:\"%08x\", ip:\"%s\", query:", t, cookie, ip.getHostAddress());
        String sep = "[";
        for (String term : query) {
            r.format("%s\"%s\"", sep, term);
            sep = ",";
        }
        r.format("]}");
        return r.toString();
    }

    public long getCookie() {
        return cookie;
    }

    public int getId() {
        return id;
    }

    public InetAddress getIp() {
        return ip;
    }

    public List<String> getQuery() {
        return query;
    }

    public double getT() {
        return t;
    }

    public int compareTo(LogLine o) {
        int r = Double.compare(this.t, o.t);
        if (r != 0) {
            return r;
        } else {
            return this.id - o.id;
        }
    }

    public double getResponseTime() {
        return responseTime;
    }
}
