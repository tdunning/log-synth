package org.apache.drill.synth;

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
            sep = ", ";
        }
        r.format("]}");
        return r.toString();
    }

    public Long getCookie() {
        return cookie;
    }

    public Integer getId() {
        return id;
    }

    public InetAddress getIp() {
        return ip;
    }

    public List<String> getQuery() {
        return query;
    }

    public Double getT() {
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
