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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.mapr.synth.samplers.SchemaSampler;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.IntOptionHandler;
import org.kohsuke.args4j.spi.Setter;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.AccessControlException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates plausible database tables in JSON, CSV or TSV format according to the data design
 * specified in a schema file.
 */
public class Synth {

    private static final int REPORTING_DELTA = 500;

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
                    "[-format JSON|TSV|CSV|XML ] " +
                    "[-threads n] " +
                    "[-output output-directory-name] ");
            throw e;
        }

        Preconditions.checkArgument(opts.threads > 0 && opts.threads <= 2000,
                "Must have at least one thread and no more than 2000");

        Preconditions.checkArgument(opts.template == null || opts.template.exists(),
                "Please specify a valid template file");

        if (opts.threads > 1) {
            Preconditions.checkArgument(!"-".equals(opts.output),
                    "If more than on thread is used, you have to use -output to set the output directory");
        }

        File outputDir = new File(opts.output);
        if (!"-".equals(opts.output)) {
            if (!outputDir.exists()) {
                Preconditions.checkState(outputDir.mkdirs(), String.format("Couldn't create output directory %s", opts.output));
            }
            Preconditions.checkArgument(outputDir.exists() && outputDir.isDirectory(),
                    String.format("Couldn't create directory %s", opts.output));
        }

        if (opts.schema == null) {
            throw new IllegalArgumentException("Must specify schema file using [-schema filename] option");
        }
        final SchemaSampler sampler = new SchemaSampler(opts.schema);
        final AtomicLong rowCount = new AtomicLong();

        Template template = null;
        if (opts.template != null) {
            final Configuration cfg = new Configuration(Configuration.VERSION_2_3_21);
            cfg.setDefaultEncoding("UTF-8");
            cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
            cfg.setDirectoryForTemplateLoading(opts.template.getCanonicalFile().getParentFile());

            template = cfg.getTemplate(opts.template.getName());
        }

        final List<ReportingWorker> tasks = Lists.newArrayList();
        int limit = (opts.count + opts.threads - 1) / opts.threads;
        int remaining = opts.count;
        for (int i = 0; i < opts.threads; i++) {

            final int count = Math.min(limit, remaining);
            remaining -= count;

            tasks.add(new ReportingWorker(opts, sampler, template, rowCount, count, i));
        }

        final double t0 = System.nanoTime() * 1e-9;
        ExecutorService pool = Executors.newFixedThreadPool(opts.threads);
        ScheduledExecutorService blinker = Executors.newScheduledThreadPool(1);
        final AtomicBoolean finalRun = new AtomicBoolean(false);

        final PrintStream sideLog = new PrintStream(new FileOutputStream("side-log"));
        Runnable blink = new Runnable() {
            double oldT;
            private long oldN;


            @Override
            public void run() {
                double t = System.nanoTime() * 1e-9;
                long n = rowCount.get();
                System.err.printf("%s\t%d\t%.1f\t%d\t%.1f\t%.3f\n", finalRun.get() ? "F" : "R", opts.threads, t - t0, n, n / (t - t0), (n - oldN) / (t - oldT));
                for (ReportingWorker task : tasks) {
                    ReportingWorker.ThreadReport r = task.report();
                    sideLog.printf("\t%d\t%.2f\t%.2f\t%.2f\t%.1f\t%.1f\n", r.fileNumber, r.threadTime, r.userTime, r.wallTime, r.rows / r.threadTime, r.rows / r.wallTime);
                }
                oldN = n;
                oldT = t;
            }
        };
        if (!"-".equals(opts.output)) {
            blinker.scheduleAtFixedRate(blink, 0, 10, TimeUnit.SECONDS);
        }
        List<Future<Integer>> results = pool.invokeAll(tasks);

        int total = 0;
        for (Future<Integer> result : results) {
            total += result.get();
        }
        Preconditions.checkState(total == opts.count,
                String.format("Expected to generate %d lines of output, but actually generated %d", opts.count, total));
        pool.shutdownNow();
        blinker.shutdownNow();
        finalRun.set(true);
        sideLog.close();
        blink.run();
    }

    private static class ReportingWorker implements Callable<Integer> {
        private final Options opts;
        private final SchemaSampler sampler;
        private final AtomicLong rowCount;
        private final int count;
        private final int fileNumber;
        private final String extension;
        int localCount;
        private ThreadMXBean mx;
        private AtomicLong wallTime;
        private AtomicLong threadTime;
        private AtomicLong userTime;
        private AtomicLong lastUserTime;
        final AtomicLong lastWall;
        final AtomicLong lastThreadTime;
        final AtomicLong lastRowCount;
        final Template template;

        private static XmlMapper xmlMapper;
        private static XMLStreamWriter sw;

        ReportingWorker(final Options opts, final SchemaSampler sampler, final Template template, final AtomicLong rowCount, final int count, final int fileNumber) {
            mx = ManagementFactory.getThreadMXBean();
            try {
                if (mx.isThreadCpuTimeSupported())
                    mx.setThreadCpuTimeEnabled(true);
                else
                    throw new AccessControlException("");
            } catch (AccessControlException e) {
                System.out.println("CPU Usage monitoring is not available!");
                System.exit(0);
            }

            this.opts = opts;
            this.sampler = sampler;
            this.rowCount = rowCount;
            this.count = count;
            this.fileNumber = fileNumber;
            this.template = template;
            switch (opts.format) {
                default:
                case JSON:
                    this.extension = ".json";
                    break;
                case TSV:
                    this.extension = ".tsv";
                    break;
                case CSV:
                    this.extension = ".csv";
                    break;
                case XML:
                    this.extension = ".xml";
                    break;
            }
            localCount = this.count;
            lastWall = new AtomicLong(System.nanoTime());
            wallTime = new AtomicLong(lastWall.get());
            lastThreadTime = new AtomicLong(mx.getCurrentThreadCpuTime());
            threadTime = new AtomicLong(lastThreadTime.get());
            lastUserTime = new AtomicLong(mx.getCurrentThreadUserTime());
            userTime = new AtomicLong(lastThreadTime.get());
            lastRowCount = new AtomicLong(0);
        }

        @Override
        public Integer call() throws Exception {
            if ("-".equals(opts.output)) {
                return generateFile(opts, sampler, template, System.out, localCount);
            } else {
                Path outputPath = new File(opts.output, String.format("synth-%04d.%s", fileNumber, extension)).toPath();

                try (PrintStream out = new PrintStream(Files.newOutputStream(outputPath,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING))) {

                    if (opts.format == Format.XML) {
                        XMLOutputFactory f = XMLOutputFactory.newFactory();
                        sw = f.createXMLStreamWriter(out);
                        sw.writeStartDocument();
                        sw.writeCharacters("\n");
                        sw.writeStartElement("root");
                        sw.writeCharacters("\n");

                        xmlMapper = new XmlMapper();
                        xmlMapper.enable(SerializationFeature.INDENT_OUTPUT);
                    }

                    header(opts.format, sampler.getFieldNames(), out);
                    int rows = 0;
                    while (rows < localCount) {
                        int k = Math.min(localCount - rows, REPORTING_DELTA);
                        rows += k;
                        rowCount.addAndGet(generateFile(opts, sampler, template, out, k));
                        wallTime.set(System.nanoTime());
                        threadTime.set(mx.getCurrentThreadCpuTime());
                        userTime.set(mx.getCurrentThreadUserTime());
                    }

                    if (opts.format == Format.XML) {
                        sw.close();
                    }
                    return rows;
                }
            }
        }

        public static void header(Format format, Iterable<String> names, PrintStream out) {
            switch (format) {
                case TSV:
                    out.printf("%s\n", withTabs.join(names));
                    break;
                case CSV:
                    out.printf("%s\n", withCommas.join(names));
                    break;
            }
        }


        static int generateFile(Options opts, SchemaSampler s, Template template, PrintStream out, int count) throws IOException, TemplateException {
            if (template != null) {
                PrintWriter writer = new PrintWriter(out);

                for (int i = 0; i < count; i++) {
                    template.process(s.sample(), writer);
                }
            } else {
                for (int i = 0; i < count; i++) {
                    format(opts.format, opts.quote, s.getFieldNames(), s.sample(), out);
                }
            }

            return count;
        }

        private static void format(Format format, Quote quoteConvention, Iterable<String> names, JsonNode fields, PrintStream out) throws IOException {
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
                case XML:
                    printXml(fields);
            }
        }

        private static void printXml(JsonNode fields) throws IOException {
            xmlMapper.writeValue(sw, fields);
            try {
                sw.writeCharacters("\n");
            } catch (XMLStreamException e) {
                throw new IOException(e);
            }
        }

        private static void printDelimited(Quote quoteConvention, Iterable<String> names, JsonNode fields, String separator, PrintStream out) {
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
            out.print("\n");
        }

        ThreadReport report() {
            return new ThreadReport();
        }

        class ThreadReport {
            long rows;
            int fileNumber = ReportingWorker.this.fileNumber;
            double wallTime;
            double threadTime;
            double userTime;

            ThreadReport() {
                while (true) {
                    long oldWall = lastWall.get();
                    long oldThread = lastThreadTime.get();
                    long oldUser = lastUserTime.get();
                    long oldRowCount = lastRowCount.get();

                    long wall = ReportingWorker.this.wallTime.get();
                    long thread = ReportingWorker.this.threadTime.get();
                    long user = ReportingWorker.this.userTime.get();
                    long rowCount = ReportingWorker.this.rowCount.get();

                    wallTime = (wall - oldWall) * 1e-9;
                    threadTime = (thread - oldThread) * 1e-9;
                    userTime = (user - oldUser) * 1e-9;
                    rows = rowCount - oldRowCount;

                    if (lastWall.compareAndSet(oldWall, wall) && lastThreadTime.compareAndSet(oldThread, thread)
                            && lastRowCount.compareAndSet(oldRowCount, rowCount)) {
                        // nobody stomped on our report
                        return;
                    }
                }
            }
        }
    }

    private static Joiner withCommas = Joiner.on(",");
    private static Joiner withTabs = Joiner.on("\t");

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
        out.print("\n");
    }

    public enum Format {
        JSON, TSV, CSV, XML
    }


    public enum Quote {
        DOUBLE_QUOTE, BACK_SLASH, OPTIMISTIC
    }

    private static class Options {
        @Option(name = "-output")
        String output = "-";

        @Option(name = "-threads")
        int threads = 1;

        @Option(name = "-count", handler = SizeParser.class)
        int count = 1000;

        @Option(name = "-schema", required = true)
        File schema;

        @Option(name = "-template")
        File template;

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
                int n = Integer.parseInt(argument.replaceAll("[kKMG]?$", ""));

                switch (argument.charAt(argument.length() - 1)) {
                    case 'G':
                        n *= 1e9;
                        break;
                    case 'M':
                        n *= 1e6;
                        break;
                    case 'K':
                    case 'k':
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
