package org.apache.drill.synth;


import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.drill.synth.sampler.LogGenerator;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.IntOptionHandler;
import org.kohsuke.args4j.spi.Setter;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

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
            System.err.println("Usage: -count <number>G|M|K [ -users number ] [-format JSON|NCSA|LOG|CSV ] log-file user-profiles");
            System.err.println(e.getMessage());
            parser.printUsage(System.err);
        }


        LogGenerator lg = new LogGenerator(opts.users);
        BufferedWriter log = Files.newWriter(new File(opts.files.get(0)), Charsets.UTF_8);
        LogLineFormatter out = LogLineFormatter.create(log, opts.format);
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

    public static enum Format {
        JSON, NCSA, LOG, CSV
    }

    private static class Options {
        @Option(name="-users", usage="number of unique users to simulate (default: 1e3)")
        double users = 1e3;

        @Option(name = "-count", usage="number of log records to create (default: 1e6)", handler = SizeParser.class)
        double count = 1e6;

        @Option(name = "-format", usage="log format to output")
        Format format = Format.LOG;

        @Argument()
        List<String> files;

        public static class SizeParser extends IntOptionHandler {
            public SizeParser(CmdLineParser parser, OptionDef option, Setter<? super Integer> setter) {
                super(parser, option, setter);
            }

            @Override
            protected Integer parse(String argument) throws NumberFormatException {
                int n = Integer.parseInt(argument.replaceAll("[KMG]?$", ""));

                switch (argument.charAt(argument.length() - 1)) {
                    case 'G':
                        n *= 1e9;
                        break;
                    case 'M':
                        n *= 1e6;
                        break;
                    case 'K':
                        n *= 1e3;
                        break;
                    default:
                        // no suffix leads here
                        break;
                }
                return n;
            }
        }
    }
}
