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

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Ordering;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LogGeneratorTest {
    @Test
    public void testSample() {
        LogGenerator gen = new LogGenerator(500);
        Multiset<String> ipCounter = HashMultiset.create();
        Multiset<String> wordCounter = HashMultiset.create();
        Multiset<Long> cookieCounter = HashMultiset.create();
        for (int i = 0; i < 100000; i++) {
            LogLine sample = gen.sample();
            ipCounter.add(sample.getIp().toString());
            wordCounter.addAll(sample.getQuery());
            cookieCounter.add(sample.getCookie());
        }

        // the values for these tests were derived empirically and examined for plausibility
        // there is nothing particularly principled about them

        List<Integer> k1 = count(wordCounter);
        assertTrue(String.format("Bad number of words %d", k1.size()), k1.size() > 1000 && k1.size() < 15000);
        assertTrue(k1.get(0) > 1000);
        assertTrue(k1.get(500) < 100);

        k1 = count(ipCounter);
        assertTrue(String.format("Bad number of IP addresses %d", k1.size()), k1.size() >= 200 && k1.size() < 400);
        assertEquals(25, (double) k1.get(0) / k1.get(k1.size() / 2), 22.);
        double r = (double) k1.get(0) / k1.get(k1.size() - 1);
        // very long-tailed distribution here
        assertEquals(75.0, r, 65.0);

        k1 = count(cookieCounter);
        assertEquals(String.format("Bad number of cookies %d", k1.size()), gen.getUserCount(), k1.size());
        double ratio = (double) k1.get(0) / k1.get(k1.size() - 1);
        assertEquals(5.0, ratio, 4.1);
    }

    private static <T> List<Integer> count(Multiset<T> counter) {
        return counter.elementSet().stream()
                .map(counter::count).sorted(Ordering.natural().reversed())
                .collect(Collectors.toList());
    }
}
