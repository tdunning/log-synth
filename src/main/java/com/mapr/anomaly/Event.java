package com.mapr.anomaly;

import com.google.common.base.Preconditions;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents an event from a log.
 */
public class Event implements Comparable<Event> {
    private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final Pattern format = Pattern.compile("\\[(.*)] /(.+)[\\?&]user=(.*) (.*)\\.(.*)\\.(.*)\\.(.*)");

    private final int uid;
    private final long time;
    private final int ip;
    private final String op;

    public Event(int uid, long time, int ip, String op) {
        Preconditions.checkNotNull(op);
        this.uid = uid;
        this.time = time;
        this.ip = ip;
        this.op = op;
    }

    public static Event read(BufferedReader in) throws IOException, ParseException, EventFormatException {
        in.mark(1000);
        String line = in.readLine();
        if (line == null) {
            return null;
        }

        try {
            Matcher m = format.matcher(line);
            if (m.matches()) {
                int i = 1;
                Date d = df.parse(m.group(i++));
                String op = m.group(i++);
                int uid = Integer.parseInt(m.group(i++), 16);
                int ip = Integer.parseInt(m.group(i++)) << 24;
                ip += Integer.parseInt(m.group(i++)) << 16;
                ip += Integer.parseInt(m.group(i++)) << 8;
                ip += Integer.parseInt(m.group(i));
                return new Event(uid, d.getTime(), ip, op);
            } else {
                in.reset();
                return null;
            }
        } catch (ParseException e) {
            in.reset();
            return null;
        } catch (NumberFormatException e) {
            in.reset();
            return null;
        }
    }

    public int getIp() {
        return ip;
    }

    public long getTime() {
        return time;
    }

    public int getUid() {
        return uid;
    }

    public String getOp() {
        return op;
    }

    @Override
    public int compareTo(Event o) {
        int r = Integer.compare(uid, o.uid);
        if (r != 0) {
            return r;
        }

        r = Long.compare(time, o.time);
        if (r != 0) {
            return r;
        }

        r = Integer.compare(ip, o.ip);
        return r;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Event)) return false;

        Event event = (Event) o;

        if (ip != event.ip) return false;
        if (time != event.time) return false;
        if (uid != event.uid) return false;
        if (!op.equals(event.op)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = uid;
        result = 31 * result + (int) (time ^ (time >>> 32));
        result = 31 * result + ip;
        result = 31 * result + op.hashCode();
        return result;
    }

    public static class EventFormatException extends Throwable {
        public EventFormatException(String line) {
            super(String.format("Invalid event format found: \"%s\"", line));
        }
    }
}
