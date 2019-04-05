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


import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.IntOptionHandler;
import org.kohsuke.args4j.spi.Setter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Create a query log with a specified number of log lines and an associated user profile database.
 *
 * Command line args include number of log lines to generate, the name of the log file to generate and the
 * name of the file to store the user profile database in.
 *
 * Log lines and user profile entries are single line JSON.
 */
public class Main {

    public static void main(String[] args) throws IOException {
        Options opts = new Options();
        CmdLineParser parser = new CmdLineParser(opts);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println("Usage: -count <number>G|M|K [ -users number ] [-format JSON|LOG|CSV ] [-with-response-time] log-file user-profiles");
            System.exit(1);
        }


        LogGenerator lg = new LogGenerator(opts.users);
        BufferedWriter log = Files.newWriter(new File(opts.files.get(0)), Charsets.UTF_8);
        LogLineFormatter out = LogLineFormatter.create(log, opts.format, opts.withResponseTimes);
        long t0 = System.nanoTime();
        for (int i = 0; i < opts.count; i++) {
            if (i % 50000 == 0) {
                long t1 = System.nanoTime();
                System.out.printf("%d\t%.3f\n", i, (t1 - t0) / 1e9);
                t0 = t1;
            }
            LogLine sample = lg.sample();
            out.write(sample);
        }
        log.close();

        BufferedWriter profile = Files.newWriter(new File(opts.files.get(1)), Charsets.UTF_8);
        for (User user : lg.getUsers()) {
            profile.write(user.toString());
            profile.newLine();
        }
        profile.close();
    }

    public enum Format {
        JSON, LOG, CSV
    }

    private static class Options {
        @Option(name="-users")
        int users = 100000;

        @Option(name = "-count", handler = SizeParser.class)
        int count = 1000000;

        @Option(name = "-format")
        Format format = Format.LOG;

        @Option(name = "-with-response-times")
        boolean withResponseTimes = false;

        @Argument()
        List<String> files;

        public static class SizeParser extends IntOptionHandler {
            public SizeParser(CmdLineParser parser, OptionDef option, Setter<? super Integer> setter) {
                super(parser, option, setter);
            }

            @Override
            protected Integer parse(String argument) throws NumberFormatException {
                return Util.parseInteger(argument);
            }
        }
    }
}
