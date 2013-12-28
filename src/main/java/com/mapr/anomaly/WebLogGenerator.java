package com.mapr.anomaly;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * Generate log file containing virtual log file entries for images with user id's, and referring URL's.
 * Also generate log file entries that contain user login events.
 * <p/>
 * If a flag file exists, these events will occasionally mimic an account take-over due to a phishing attack.
 * In such an attack, two scenarios may happen:
 * <p/>
 * a) in one case, the first login attempt will come from the attacker's IP address without associated image requests
 * followed shortly by a second attempt from the victim's IP address with associated image requests which are also
 * from the victim's IP address.  This emulates the case where the attacker has directed the victim to a fake login
 * page where the user name and password are harvested, but where the victim is sent to a simulated login failed page
 * with a try-again link pointing to real login.  The anomaly to be detected is the login from two IP's in short
 * succession, the first being used by many such logins.
 * <p/>
 * b) in the second case, there will be a set of image requests from the attacker's IP with no login page request
 * followed shortly by a user login from the victim's IP.  This simulates the scenario where the attacker's
 * emulation of the login page directly uses image elements from the real login page but where the attacker avoids
 * using the intercepted credentials right away.  The anomalies to be detected include image requests without a login
 * attempt
 * <p/>
 * Without an attack, users's are assumed to log in roughly once per day.  A normal
 * login consists of a login page with associated image requests.
 */
public class WebLogGenerator {
    private static Options opts = new Options();
    private static PrintWriter userLog;
    private static PrintWriter imageLog;
    private static Random gen = new Random();
    private static PrintWriter fraudLog;

    public static void main(String[] args) throws FileNotFoundException, InterruptedException, CmdLineException {
        CmdLineParser parser = new CmdLineParser(opts);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println("Usage: [-count number of users] [-rate fraud rate] -user user log file -image image log file -flag name of flag file");
            throw e;
        }

        userLog = new PrintWriter(opts.user);
        imageLog = new PrintWriter(opts.image);
        fraudLog = new PrintWriter(opts.fraudLog);

        double averageDelay = 86400.0 / opts.count;
        int fraudIP = gen.nextInt();
        int[] userIP = new int[opts.count];

        System.out.printf("Starting generator.  Average interval between logins = %.2f\n", averageDelay);

        PriorityQueue<LogLine> events = new PriorityQueue<>();

        boolean oldFraud = false;

        long t0 = System.nanoTime();
        double nextLogin = t0 / 1e9;
        double nextFlush = nextLogin + 1;
        int k1 = 0, k2 = 0, k3 = 0;
        try {
            while (true) {
                double now = System.nanoTime() / 1e9;

                if (oldFraud != opts.flag.exists()) {
                    oldFraud = opts.flag.exists();
                    System.out.printf("Fraud generator = %s\n", oldFraud);
                    if (oldFraud) {
                        System.out.printf("     rate = %.3f\n", opts.fraudRate);
                    }
                }

                // insert some new events if it is time for another user to show up
                if (nextLogin <= now) {
                    int user = Math.abs(gen.nextInt() % opts.count);
                    if (userIP[user] == 0) {
                        userIP[user] = gen.nextInt();
                    }

                    if (opts.flag.exists() && gen.nextDouble() < opts.fraudRate) {
                        if (gen.nextDouble() < 0.5) {
                            k2++;
                            fraudLog.printf("%d, %s\n", user, asIP(userIP[user]));
                            fraudLog.flush();
                            generateFraud1(events, user, userIP[user], fraudIP);
                        } else {
                            k3++;
                            generateFraud2(events, user, userIP[user], fraudIP);
                        }
                    } else {
                        k1++;
                        generateLogin(events, user, userIP[user], 0);
                    }

                    nextLogin += -averageDelay * Math.log(1 - gen.nextDouble());
                }

                if (nextFlush <= now) {
                    userLog.flush();
                    imageLog.flush();
                    nextFlush = now + 1;
                    System.out.printf("Queue size = %d, non-fraud = %d, fraud1 = %d, fraud2 = %d\n", events.size(), k1, k2, k3);
                    k1 = k2 = k3 = 0;
                }

                // now process any events that have come due
                LogLine event = events.peek();
                while (event != null && event.t <= now) {
                    event.emit();
                    events.remove();
                    event = events.peek();
                }

                // then wait for the next pending event or next login
                double wakeUp = Math.min(Math.min(nextFlush, nextLogin), event == null ? nextLogin : event.t);
                if (wakeUp > now) {
                    Thread.sleep(Math.round((wakeUp - now) * 1e3));
                }
            }

        } catch (InterruptedException e) {
            System.err.printf("Interrupted\n");
            userLog.close();
            imageLog.close();
        }
    }

    /**
     * Simulates a human logging in.  A key element here is that the login occurs a few seconds after the image
     * requests.
     */
    private static void generateLogin(PriorityQueue<LogLine> events, int user, int ip, double delay) {
        // the login page load
        images(events, user, ip, delay);
        // and then the login a few seconds later
        loginLine(events, user, ip, delay + 2 + 3 * gen.nextDouble());
    }

    /**
     * Emulates a type (a) attack where a login occurs without associated images.
     */
    private static void generateFraud1(PriorityQueue<LogLine> events, int user, int ip, int fraudIP) {
        // the sting comes without image elements
        loginLine(events, user, fraudIP, 0);
        // and then the real user comes in a bit later
        generateLogin(events, user, ip, 20 + gen.nextDouble() * 30);
    }

    /**
     * Emulates a type (b) attack where an attacker uses image elements to fake a login page.  The actual
     * fraudulent login comes 20 minutes after the original hack.
     */
    private static void generateFraud2(PriorityQueue<LogLine> events, int user, int ip, int fraudIP) {
        // the phished login page
        images(events, user, fraudIP, 0);
        // the user comes and does a real login
        generateLogin(events, user, ip, 20 + gen.nextDouble() * 30);
        // and then the sting
        generateLogin(events, user, fraudIP, 1200);
    }

    /**
     * Generates a login log record after a specified delay.
     */
    private static void loginLine(PriorityQueue<LogLine> events, int user, int ip, double delay) {
        events.add(new LogLine(userLog, String.format("/login?user=%08x %s", user, asIP(ip)), delay));
    }

    /**
     * Generates a set of image requests after a delay with some additional jitter.
     */
    private static void images(PriorityQueue<LogLine> events, int user, int ip, double delay) {
        for (int i = 0; i < 5; i++) {
            events.add(
                    new LogLine(
                            imageLog, String.format("/static/image-%d?user=%08x %s", i, user, asIP(ip)),
                            gen.nextDouble() * 600e-3 + delay));
        }
    }

    private static String asIP(int ip) {
        return String.format("%d.%d.%d.%d", (ip >> 24) & 0xff, (ip >> 16) & 0xff, (ip >> 8) & 0xff, ip & 0xff);
    }

    private static class Options {
        @Option(name = "-count")
        int count = 100000;

        @Option(name = "-user", required = true)
        File user;

        @Option(name = "-image", required = true)
        File image;

        @Option(name = "-frauds")
        File fraudLog = new File("fraud.log");

        @Option(name = "-flag", required = true)
        File flag;

        @Option(name = "-rate")
        public double fraudRate = 0.001;
    }

}
