package org.apache.drill.synth;

import com.google.common.collect.Lists;
import org.apache.mahout.common.RandomUtils;
import org.apache.mahout.math.jet.random.AbstractContinousDistribution;
import org.apache.mahout.math.jet.random.Exponential;

import java.net.InetAddress;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Samples from a user space.  Each user has a variety of internal characteristics.
 */
public class User implements Comparable<User> {
    private static AtomicInteger idCounter = new AtomicInteger();

    private int id;

    private AbstractContinousDistribution queryLengthDistribution = new Exponential(0.4, RandomUtils.getRandom());
    private AbstractContinousDistribution sessionTimeDistribution;

    private long cookie = RandomUtils.getRandom().nextLong();

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
        return String.format("{ip:\"%s\", cookie:\"%08x\", geo:\"%s\"}", address, cookie, geoCode);
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
