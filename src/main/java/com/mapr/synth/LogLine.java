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

import java.net.InetAddress;
import java.util.Formatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A log line contains a user id, an IP address and a query.
 */
public class LogLine implements Comparable<LogLine> {
    private static AtomicInteger counter = new AtomicInteger();

    private InetAddress ip;
    private long cookie;
    private List<String> query;
    private double t;
    private int id = counter.addAndGet(1);

    public LogLine(double t, InetAddress ip, long cookie, List<String> query) {
        this.t = t;
        this.cookie = cookie;
        this.ip = ip;
        this.query = query;
    }

    public LogLine(double t, User user) {
        this(t, user.getAddress(), user.getCookie(), user.getQuery());
    }

    @Override
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
}
