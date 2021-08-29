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

import com.google.common.collect.Queues;
import com.mapr.synth.distributions.LongTail;
import com.mapr.synth.distributions.TermGenerator;
import com.mapr.synth.distributions.WordGenerator;
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

    private LongTail<InetAddress> ipGenerator = new LongTail<>(100, 0.5) {
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

    private WordGenerator words = new WordGenerator("word-frequency-seed", "/other-words");
    private TermGenerator terms = new TermGenerator(words, 1, 0.8);
    private TermGenerator geo = new TermGenerator(new WordGenerator(null, "/geo-codes"), 10, 0);

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
        assert users.peek() != null;
        double t2 = users.peek().getNextSession();

        // while some user has activity before the first buffered event generate that
        // session and fill in the event buffer
        while (t2 < t1) {
            User u = users.poll();
            assert u != null;

            // generate a session into the event buffer
            u.session(eventBuffer);

            // user now has new time for next session
            users.add(u);

            // if u.session() schedules an event at the time the users session start, then this will never
            // allow another loop. On the other hand, if the first event of a session is sometime after
            // the session starts or if a session has no events, then another user might have a session
            // start time before the next event
            firstEvent = eventBuffer.peek();
            t1 = firstEvent != null ? firstEvent.getT() : Double.POSITIVE_INFINITY;
            assert users.peek() != null;
            t2 = users.peek().getNextSession();
        }
        return eventBuffer.poll();
    }

    public int getUserCount() {
        return users.size();
    }
}
