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

package com.mapr;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.mapr.synth.samplers.SchemaSampler;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Generate a bunch of web purchase log records.  These will be out of order with respect to time and should be sorted.
 * <p>
 * The tab delimited output fields include:
 * <p>
 * hit_time, hit_id product_category, campaign_list, search_keywords, event_list user_id, user_category, state, browser,
 * country, language, os,
 */
public class PurchaseLog {
    public static void main(String[] args) throws IOException {
        Options opts = new Options();
        CmdLineParser parser = new CmdLineParser(opts);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println("Usage: -count <number>G|M|K [ -users number ]  log-file user-profiles");
            return;
        }

        Joiner withTab = Joiner.on("\t");

        // first generate lots of user definitions
        SchemaSampler users = SchemaSampler.fromResource("user-schema.txt");
        File userFile = File.createTempFile("user", "tsv");
        BufferedWriter out = Files.newBufferedWriter(userFile.toPath(), Charsets.UTF_8);
        for (int i = 0; i < opts.users; i++) {
            out.write(withTab.join(users.sample()));
            out.newLine();
        }
        out.close();

        // now generate a session for each user
        Pattern onComma = Pattern.compile(",");

        Pattern onTabs = Pattern.compile("\t");

        Random gen = new Random();
        SchemaSampler intermediate = SchemaSampler.fromResource("hit_step.txt");

        final int COUNTRY = Iterables.indexOf(users.getFieldNames(), "country"::equals);
        final int CAMPAIGN = Iterables.indexOf(intermediate.getFieldNames(), "campaign_list"::equals);
        final int SEARCH_TERMS = Iterables.indexOf(intermediate.getFieldNames(),"search_keywords"::equals);
        Preconditions.checkState(COUNTRY >= 0, "Need country field in user schema");
        Preconditions.checkState(CAMPAIGN >= 0, "Need campaign_list field in step schema");
        Preconditions.checkState(SEARCH_TERMS >= 0, "Need search_keywords field in step schema");

        out = Files.newBufferedWriter(new File(opts.out).toPath(), Charsets.UTF_8);

        for (String line : Files.readAllLines(userFile.toPath(), Charsets.UTF_8)) {
            long t = (long) (TimeUnit.MILLISECONDS.convert(30, TimeUnit.DAYS) * gen.nextDouble());
            List<String> user = Arrays.asList(onTabs.split(line));

            // pick session length
            int n = (int) Math.floor(-30 * Math.log(gen.nextDouble()));

            for (int i = 0; i < n; i++) {
                // time on page
                int dt = (int) Math.floor(-20000 * Math.log(gen.nextDouble()));
                t += dt;

                // hit specific values
                JsonNode step = intermediate.sample();

                // check for purchase
                double p = 0.01;
                List<String> campaigns = Arrays.asList(onComma.split(step.get("campaign_list").asText()));
                List<String> keywords = Arrays.asList(onComma.split(step.get("search_keywords").asText()));
                if ((user.get(COUNTRY).equals("us") && campaigns.contains("5")) ||
                        (user.get(COUNTRY).equals("jp") && campaigns.contains("7")) ||
                        keywords.contains("homer") || keywords.contains("simpson")) {
                    p = 0.5;
                }

                String events = gen.nextDouble() < p ? "1" : "-";

                out.write(Long.toString(t));
                out.write("\t");
                out.write(line);
                out.write("\t");
                out.write(withTab.join(step));
                out.write("\t");
                out.write(events);
                out.write("\n");
            }
        }
        out.close();
    }

    private static class Options {
        @Option(name = "-users")
        int users;

        @Option(name = "-log-file")
        String out;

    }

}
