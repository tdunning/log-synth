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

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.mapr.synth.samplers.SchemaSampler;
import org.apache.mahout.math.stats.LogLikelihood;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class CommonPointOfCompromiseTest {

    private static final int DAYS_COUNTED = 200;
    private static final int USER_COUNT = 50000;

    // this isn't quite a unit test.  It produces files to be visualized with R
    @Test
    public void testCompromise() throws IOException, ParseException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long start = df.parse("2014-01-01 00:00:00").getTime();
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema013.json"), Charsets.UTF_8).read());

        long exploitStart = df.parse("2014-01-20 00:00:00").getTime();
        long exploitEnd = df.parse("2014-02-20 00:00:00").getTime();
        int exploitStartDay = (int) TimeUnit.DAYS.convert(exploitStart - start, TimeUnit.MILLISECONDS);

        int[] transactionsByDay = new int[DAYS_COUNTED];
        int[] compromiseByDay = new int[DAYS_COUNTED];
        int[] fraudByDay = new int[DAYS_COUNTED];

        Multiset<Integer> fraudUserCounts = HashMultiset.create();
        Multiset<Integer> nonfraudUserCounts = HashMultiset.create();
        Multiset<Integer> allMerchantCounts = HashMultiset.create();
        int fraudAccounts = 0;
        Set<Integer> merchantHistory = Sets.newHashSet();

        // these collect the evolution of the contingency table for just merchant 0 and are indexed by time relative to exploit window.
        int exploitLength = (int) (TimeUnit.DAYS.convert(exploitEnd - exploitStart, TimeUnit.MILLISECONDS)) + 1;
//        exploitLength = 5;
        int[] atmTotal = new int[exploitLength];
        int[] atmFraud = new int[exploitLength];
        int[] atmNonFraud = new int[exploitLength];
        int[] nonAtmFraud = new int[exploitLength];
        int[] nonAtmNonFraud = new int[exploitLength];

        for (int userId = 0; userId < USER_COUNT; userId++) {
            JsonNode sample = s.sample();
            merchantHistory.clear();
            boolean userHasFraud = false;

            int[] hasFraudPerUser = new int[exploitLength];

            for (JsonNode record : sample.get("history")) {
                long timestamp = record.get("timestamp").asLong() * 1000;
                int day = (int) ((timestamp - start) / TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS));
                if (day >= DAYS_COUNTED || day >= exploitStartDay + exploitLength) {
                    break;
                }
                if (record.get("compromise").asInt() > 0) {
                    compromiseByDay[day]++;
                }
                boolean isFraud = record.get("fraud").asInt() > 0;
                if (isFraud) {
                    fraudByDay[day]++;
                }
                transactionsByDay[day]++;

                // only record history up to the beginning of the exploit window
                int merchant = record.get("merchant").asInt();
                if (timestamp < exploitStart) {
                    merchantHistory.add(merchant);
                }

                // only consider fraud indicators during the exploit window
                if (timestamp >= exploitStart && timestamp <= exploitEnd) {
                    // any fraud in the window marks the user
                    if (isFraud) {
                        // first time we see fraud indication in exploit window, we set flags for the rest of the window
                        if (!userHasFraud) {
                            int eday = day - exploitStartDay;
                            for (int i = eday; i < exploitLength; i++) {
                                hasFraudPerUser[i] = 1;
                            }
                        }
                        userHasFraud = true;
                    }
                }

            }
            // we collect flags for each day and then only count this user once.  Necessary because multiple
            // transactions can occur on each day and we don't want to count all of them.
            int atmInHistory = merchantHistory.contains(0) ? 1 : 0;
            for (int day = 0; day < exploitLength; day++) {
                atmTotal[day] += atmInHistory;
                atmFraud[day] += atmInHistory * hasFraudPerUser[day];
                atmNonFraud[day] += atmInHistory * (1 - hasFraudPerUser[day]);
                nonAtmFraud[day] += (1 - atmInHistory) * hasFraudPerUser[day];
                nonAtmNonFraud[day] += (1 - atmInHistory) * (1 - hasFraudPerUser[day]);
            }

            if (userHasFraud) {
                fraudAccounts++;
                for (Integer merchant : merchantHistory) {
                    fraudUserCounts.add(merchant);
                    allMerchantCounts.add(merchant);
                }
            } else {
                for (Integer merchant : merchantHistory) {
                    nonfraudUserCounts.add(merchant);
                    allMerchantCounts.add(merchant);
                }
            }
        }

        int k1 = fraudAccounts;
        int k2 = USER_COUNT - k1;

        try (PrintStream out = new PrintStream(new FileOutputStream("scores.tsv"))) {
            out.printf("merchant\tk11\tk12\tk21\tk22\tk.1\tscore\n");
            for (Integer merchant : allMerchantCounts.elementSet()) {
                int k11 = fraudUserCounts.count(merchant);
                int k12 = k1 - k11;
                int k21 = nonfraudUserCounts.count(merchant);
                int k22 = k2 - k21;
                out.printf("%d\t%d\t%d\t%d\t%d\t%d\t%.1f\n", merchant, k11, k12, k21, k22, allMerchantCounts.count(merchant),
                        LogLikelihood.rootLogLikelihoodRatio(k11, k12, k21, k22));
            }
        }

        try (PrintStream out = new PrintStream(new FileOutputStream("counts.tsv"))) {
            out.printf("day\tcompromises\tfrauds\ttransactions\n");

            for (int i = 0; i < compromiseByDay.length; i++) {
                out.printf("%d\t%d\t%d\t%d\n", i, compromiseByDay[i], fraudByDay[i], transactionsByDay[i]);
            }
        }

        try (PrintStream out = new PrintStream(new FileOutputStream("growth.tsv"))) {
            out.printf("day\tatm.total\tk11\tk12\tk21\tk22\tscore\n");

            for (int i = 0; i < exploitLength; i++) {
                int k11 = atmFraud[i];
                int k12 = nonAtmFraud[i];
                int k21 = atmNonFraud[i];
                int k22 = nonAtmNonFraud[i];
                out.printf("%d\t%d\t%d\t%d\t%d\t%d\t%.1f\n", i, atmTotal[i], k11, k12, k21, k22,
                        LogLikelihood.rootLogLikelihoodRatio(k11, k12, k21, k22));
            }
        }

    }
}