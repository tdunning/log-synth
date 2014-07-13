package org.apache.drill.synth;

import com.google.common.collect.Queues;
import org.apache.drill.synth.distributions.LongTail;
import org.apache.drill.synth.distributions.TermGenerator;
import org.apache.drill.synth.distributions.WordGenerator;
import org.apache.mahout.common.RandomUtils;
import org.apache.mahout.math.jet.random.AbstractContinousDistribution;
import org.apache.mahout.math.jet.random.Uniform;
import org.apache.mahout.math.random.Sampler;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * Generates kind of realistic log lines consisting of a user id (a cookie), an IP address and a query.
 */
public class LogGenerator implements Sampler<LogLine> {
    private PriorityQueue<LogLine> eventBuffer = Queues.newPriorityQueue();
    private PriorityQueue<User> users = Queues.newPriorityQueue();

    private LongTail<InetAddress> ipGenerator = new LongTail<InetAddress>(1, 0.5) {
        Random gen = new Random();

        @Override
        protected InetAddress createThing() {
            int address = gen.nextInt();
            try {
                return Inet4Address.getByAddress(new byte[]{
                        (byte) (address >>> 24),
                        (byte) (0xff & (address >>> 16)),
                        (byte) (0xff & (address >>> 8)),
                        (byte) (0xff & (address))
                });
            } catch (UnknownHostException e) {
                throw new RuntimeException("Can't happen with numeric IP address", e);
            }
        }
    };

    private WordGenerator words = new WordGenerator("word-frequency-seed", "other-words");
    private TermGenerator terms = new TermGenerator(words, 1, 0.8);
    private TermGenerator geo = new TermGenerator(new WordGenerator(null, "geo-codes"), 10, 0);

    // the average user visits once per day, but there is a LOT of variation between users
    private AbstractContinousDistribution sessionRateDistribution = new Uniform(1.0 / 24 / 3600, 1.0 / 24 / 3600, RandomUtils.getRandom());

    public Iterable<User> getUsers() {
        return users;
    }

    public LogGenerator(int userCount) {
        for (int i = 0; i < userCount; i++) {
            users.add(new User(ipGenerator.sample(), geo.sample(), terms, sessionRateDistribution.nextDouble()));
        }
    }

    public LogLine sample() {
        LogLine firstEvent = eventBuffer.peek();
        double t1 = firstEvent != null ? firstEvent.getT() : Double.POSITIVE_INFINITY;
        double t2 = users.peek().getNextSession();

        while (t2 < t1) {
            User u = users.poll();

            // generate a session
            u.session(eventBuffer);

            // user now has new time for next session
            users.add(u);

            // if u.session() schedules an event immediately, then this will never
            // allow another loop
            firstEvent = eventBuffer.peek();
            t1 = firstEvent != null ? firstEvent.getT() : Double.POSITIVE_INFINITY;
            t2 = users.peek().getNextSession();
        }
        return eventBuffer.poll();
    }

    public int getUserCount() {
        return users.size();
    }
}
