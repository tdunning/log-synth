package com.mapr.synth;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

/**
 * Log lines can be formated in different ways
 */
public abstract class LogLineFormatter {
    private PrintWriter log;

    public LogLineFormatter(Writer log) {
        this.log = new PrintWriter(log);
    }

    public static LogLineFormatter create(BufferedWriter log, Main.Format format) {
        switch (format) {
            case JSON:
                return new JsonFormatter(log);
            case LOG:
            case CSV:
                return new CsvFormatter(log);
        }
        // can't happen
        return null;
    }

    public abstract void write(LogLine sample) throws IOException;

    public PrintWriter getLog() {
        return log;
    }

    private static class CsvFormatter extends LogLineFormatter {
        public CsvFormatter(BufferedWriter log) {
            super(log);
        }

        public void write(LogLine sample) throws IOException {
            getLog().printf("%.3f,%08x,%s,", sample.getT(), sample.getCookie(), sample.getIp().getHostAddress());
            String sep = "\"";
            for (String term : sample.getQuery()) {
                getLog().format("%s%s", sep, term);
                sep = " ";
            }
            getLog().format("\"\n");
        }
    }

    private static class JsonFormatter extends LogLineFormatter {
        public JsonFormatter(BufferedWriter log) {
            super(log);
        }

        public void write(LogLine sample) throws IOException {
            getLog().printf("{t: %.3f, cookie:\"%08x\", ip:\"%s\", query:", sample.getT(), sample.getCookie(), sample.getIp().getHostAddress());
            String sep = "[";
            for (String term : sample.getQuery()) {
                getLog().format("%s\"%s\"", sep, term);
                sep = ", ";
            }
            getLog().format("]}\n");
        }
    }
}
