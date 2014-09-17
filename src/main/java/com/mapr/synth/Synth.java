package com.mapr.synth;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.mapr.synth.samplers.SchemaSampler;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.IntOptionHandler;
import org.kohsuke.args4j.spi.Setter;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.*;

/**
 * Generates plausible database tables in JSON, CSV or TSV format.
 */
public class Synth {
    public static void main(String[] args) throws IOException, CmdLineException, InterruptedException, ExecutionException {
        final Options opts = new Options();
        CmdLineParser parser = new CmdLineParser(opts);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println("Usage: " +
                    "[ -count <number>G|M|K ] " +
                    "-schema schema-file " +
                    "[-quote DOUBLE_QUOTE|BACK_SLASH|OPTIMISTIC] " +
                    "[-format JSON|TSV|CSV ] " +
                    "[-threads n] " +
                    "[-output output-directory-name] ");
            throw e;
        }
        Preconditions.checkArgument(opts.threads > 0 && opts.threads <= 200,
                "Must have at least one thread and no more than 200");

        if (opts.threads > 1) {
            Preconditions.checkArgument(!"-".equals(opts.output),
                    "If more than on thread is used, you have to use -output to set the output directory");
        }

        File outputDir = new File(opts.output);
        if (!"-".equals(opts.output) && !outputDir.exists()) {
            Preconditions.checkState(outputDir.mkdirs(), String.format("Couldn't create output directory %s", opts.output));
        }
        Preconditions.checkArgument(outputDir.exists() && outputDir.isDirectory(),
                String.format("Couldn't create directory %s", opts.output));


        final SchemaSampler s = new SchemaSampler(opts.schema);

        List<Callable<Integer>> tasks = Lists.newArrayList();
        int limit = (opts.count + opts.threads - 1) / opts.threads;
        int remaining = opts.count;
        for (int i = 0; i < opts.threads; i++) {

            final int count = Math.min(limit, remaining);
            final int fileNumber = i;
            remaining -= count;
            tasks.add(new Callable<Integer>() {
                int localCount = count;

                @Override
                public Integer call() throws Exception {
                    if ("-".equals(opts.output)) {
                        return generateFile(opts, s, System.out, localCount);
                    } else {
                        Path outputPath = new File(opts.output, String.format("synth-%04d", fileNumber)).toPath();
                        try (PrintStream out = new PrintStream(Files.newOutputStream(outputPath,
                                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING))) {
                            return generateFile(opts, s, out, localCount);
                        }
                    }
                }
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(opts.threads);
        List<Future<Integer>> results = pool.invokeAll(tasks);

        int total = 0;
        for (Future<Integer> result : results) {
            total += result.get();
        }
        Preconditions.checkState(total == opts.count,
                String.format("Expected to generate %d lines of output, but actually generated %d", opts.count, total));
        pool.shutdownNow();
    }

    private static int generateFile(Options opts, SchemaSampler s, PrintStream out, int count) {
        header(opts.format, s.getFieldNames(), out);
        for (int i = 0; i < count; i++) {
            format(opts.format, opts.quote, s.getFieldNames(), s.sample(), out);
        }
        return count;
    }

    static Joiner withCommas = Joiner.on(",");
    static Joiner withTabs = Joiner.on("\t");

    private static void header(Format format, List<String> names, PrintStream out) {
        switch (format) {
            case TSV:
                out.printf("%s\n", withTabs.join(names));
                break;
            case CSV:
                out.printf("%s\n", withCommas.join(names));
                break;
        }
    }

    private static void format(Format format, Quote quoteConvention, List<String> names, JsonNode fields, PrintStream out) {
        switch (format) {
            case JSON:
                out.printf("%s\n", fields.toString());
                break;
            case TSV:
                printDelimited(quoteConvention, names, fields, "\t", out);
                break;
            case CSV:
                printDelimited(quoteConvention, names, fields, ",", out);
                break;
        }
    }

    private static void printDelimited(Quote quoteConvention, List<String> names, JsonNode fields, String separator, PrintStream out) {
        String x = "";
        for (String name : names) {
            switch (quoteConvention) {
                case DOUBLE_QUOTE:
                    out.printf("%s%s", x, fields.get(name));
                    break;
                case OPTIMISTIC:
                    out.printf("%s%s", x, fields.get(name).asText());
                    break;
                case BACK_SLASH:
                    out.printf("%s%s", x, fields.get(name).asText().replaceAll("([,\t\\s\\\\])", "\\\\$1"));
                    break;
            }
            x = separator;
        }
        out.printf("\n");
    }

    public static enum Format {
        JSON, TSV, CSV
    }

    public static enum Quote {
        DOUBLE_QUOTE, BACK_SLASH, OPTIMISTIC
    }

    private static class Options {
        @Option(name = "-output")
        String output = "-";

        @Option(name = "-threads")
        int threads = 1;

        @Option(name = "-count", handler = SizeParser.class)
        int count = 1000;

        @Option(name = "-schema")
        File schema;

        @Option(name = "-format")
        Format format = Format.CSV;

        @Option(name = "-quote")
        Quote quote = Quote.DOUBLE_QUOTE;

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
