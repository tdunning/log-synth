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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

/**
 * Log lines can be formated in different ways
 */
public abstract class LogLineFormatter {
    private final boolean withResponseTimes;
    private PrintWriter log;

    public LogLineFormatter(Writer log, boolean withResponseTimes) {
        this.log = new PrintWriter(log);
        this.withResponseTimes = withResponseTimes;
    }

    public static LogLineFormatter create(BufferedWriter log, Main.Format format, boolean withResponseTimes) {
        switch (format) {
            case JSON:
                return new JsonFormatter(log, withResponseTimes);
            case LOG:
            case CSV:
                return new CsvFormatter(log, withResponseTimes);
        }
        // can't happen
        return null;
    }

    public abstract void write(LogLine sample) throws IOException;

    public PrintWriter getLog() {
        return log;
    }

    private static class CsvFormatter extends LogLineFormatter {
        public CsvFormatter(BufferedWriter log, boolean withResponseTimes) {
            super(log, withResponseTimes);
        }

        public void write(LogLine sample) throws IOException {
            getLog().printf("%.3f,%08x,%s,", sample.getT(), sample.getCookie(), sample.getIp().getHostAddress());
            String sep = "\"";
            for (String term : sample.getQuery()) {
                getLog().format("%s%s", sep, term);
                sep = " ";
            }
            getLog().format("\"");
            if (super.withResponseTimes) {
                getLog().printf(",%.1f", sample.getResponseTime() * 1000);
            }
            getLog().format("\n");
        }
    }

    private static class JsonFormatter extends LogLineFormatter {
        public JsonFormatter(BufferedWriter log, boolean withResponseTimes) {
            super(log, withResponseTimes);
        }

        public void write(LogLine sample) throws IOException {
            getLog().printf("{\"t\": %.3f, \"cookie\":\"%08x\", \"ip\":\"%s\", \"query\":", sample.getT(), sample.getCookie(), sample.getIp().getHostAddress());
            String sep = "[";
            for (String term : sample.getQuery()) {
                getLog().format("%s\"%s\"", sep, term);
                sep = ", ";
            }
            getLog().format("]");
            if (super.withResponseTimes) {
                getLog().printf(", \"responseTime\": %.1f", sample.getResponseTime() * 1000);
            }
            getLog().format("]}\n");
        }
    }
}
