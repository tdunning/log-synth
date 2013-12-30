package com.mapr.anomaly;

import com.google.common.base.Charsets;
import com.google.common.collect.*;
import com.google.common.io.Files;
import org.apache.mahout.math.stats.TDigest;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;

/**
 * Analyze the sequences of events that occur to individual users.  As a result of this analysis,
 * we can determine common sequences of events with typical timings in between.  This, in turn,
 * can be used to highlight anomalous sequences.
 */
public class UserAnomalyDetector {
    private static Options opts = new Options();
    private static boolean checkTransactions = false;
    private static TDigest scores;

    public static void main(String[] args) throws CmdLineException, IOException, ParseException, Event.EventFormatException, InterruptedException {
        CmdLineParser parser = new CmdLineParser(opts);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println("Usage: [-count number of users] [-rate fraud rate] -user user log file -image image log file -flag name of flag file");
            throw e;
        }

        while (!opts.user.exists() || !opts.image.exists()) {
            System.out.printf("Waiting for log files\n");
            Thread.sleep(1000);
        }

        BufferedReader user = Files.newReader(opts.user, Charsets.UTF_8);
        BufferedReader image = Files.newReader(opts.image, Charsets.UTF_8);

        long t0 = System.currentTimeMillis();
        long lastReport = 0;

        scores = new TDigest(1000);
        System.out.printf("\n\n\tAcquiring initial model training data\n\n");
        while ((System.currentTimeMillis() - t0) / 1e3 < opts.maxRuntime) {
            long now = System.currentTimeMillis();
            if (now - lastReport > 5000) {
                if (!checkTransactions) {
                    System.out.printf("Processing data at %.1f ... history.size = %d, live = %s\n", (now - t0) / 1e3, history.size(), checkTransactions);
                }
                lastReport = now;
            }


            if ((now - t0) / 1e3 > opts.warmup && !checkTransactions) {
                System.out.printf("\n\n\tTraining data acquired ... here is a dump of the event counts and timings\n\n");
                dumpTable();

                System.out.printf("\n\n\tBeginning to monitor for anomalous events\n\n");
                System.out.printf("event1 => event2 <is-same-ip-address>\n\n");
                checkTransactions = true;
            }

            Event userEvent = Event.read(user);
            double score = 0;
            while (userEvent != null) {
                score += addToHistory(userEvent);
                userEvent = Event.read(user);
            }
            Event imageEvent = Event.read(image);
            while (imageEvent != null) {
                score += addToHistory(imageEvent);
                imageEvent = Event.read(image);
            }
            if (checkTransactions) {
                scores.add(score);
            }
            Thread.sleep(10);
        }

    }

    private static void dumpTable() {
        for (String events : transitionCounts.keySet()) {
            TDigest h = transitionCounts.get(events);
            if (h.size() > 2) {
                System.out.printf("%20s\t%d\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\n", events, h.size(), h.quantile(0.05), h.quantile(0.25), h.quantile(0.5), h.quantile(0.95), h.quantile(0.99));
            }else {
                System.out.printf("%20s\t%d\tNA\tNA\tNA\n", events, h.size());
            }
        }
    }

    private static Map<Integer, NavigableSet<Event>> history = Maps.newLinkedHashMap();

    private static final Map<String, TDigest> transitionCounts = Maps.newHashMap();
    private static final Multiset<String> beforeCounts = HashMultiset.create();
    private static final Multiset<String> afterCounts = HashMultiset.create();

    private static double addToHistory(Event event) {
        int uid = event.getUid();

        NavigableSet<Event> x = history.get(uid);
        if (x == null) {
            x = Sets.newTreeSet();
            history.put(uid, x);
        }

        double score = 0;
        Event old = x.lower(event);
        x.add(event);
        if (old != null) {
            beforeCounts.add(old.getOp());
            afterCounts.add(event.getOp() + " " + (old.getIp() == event.getIp()));
            String key = old.getOp() + " => " + event.getOp() + " " + (old.getIp() == event.getIp());

            TDigest h = transitionCounts.get(key);
            if (h == null) {
                h = new TDigest(1000);
                transitionCounts.put(key, h);
            }
            h.add(event.getTime() - old.getTime());

            int k11 = h.size();
            int k1x = beforeCounts.count(old.getOp());
            score = (double) k1x / (k11 + 1.0);
            if (scores.cdf(score) > 0.99 && checkTransactions) {
                double q = scores.cdf(score);
                q = Math.max(1e-6, Math.min(1 - 1e-6, q));
                System.out.printf("%.1f\t%s\t%d\n", Math.log(q / (1 - q)), key, event.getUid());
            }
            trimHistory();
        }

        return score;
    }

    private static void trimHistory() {
        long now = System.currentTimeMillis();
        List<Integer> toDelete = Lists.newArrayList();
        for (Integer uid : history.keySet()) {
            NavigableSet<Event> x = history.get(uid);
            if (now - x.last().getTime() > opts.timeLimit * 1e3) {
                toDelete.add(uid);
            } else {
                break;
            }
        }

        history.keySet().removeAll(toDelete);
    }

    private static class Options {
        @Option(name = "-user", required = true)
        File user;

        @Option(name = "-image", required = true)
        File image;

        @Option(name = "-limit")
        public double timeLimit = 60;

        @Option(name = "-warmup")
        public double warmup = 30;

        @Option(name = "-max")
        public double maxRuntime = 600;
    }
}
