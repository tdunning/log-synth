package com.mapr.anomaly;

import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Represents individual log lines in a web-ish format.  For production use, it would be preferable to
 * use a protobuf based logging system.
 */
class LogLine implements Comparable<LogLine> {
    private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    double t;
    String line;
    PrintWriter where;

    LogLine(PrintWriter where, String line, double delay) {
        this.line = line;
        this.t = System.nanoTime() * 1e-9 + delay;
        this.where = where;
    }

    public void emit() {
        where.println("[" + df.format(new Date()) + "] " + line);
    }


    @Override
    public int compareTo(LogLine o) {
        int r = Double.compare(t, o.t);
        if (r != 0) {
            return r;
        }

        r = line.compareTo(o.line);
        if (r != 0) {
            return r;
        }

        return (where.hashCode() - o.where.hashCode());
    }
}
