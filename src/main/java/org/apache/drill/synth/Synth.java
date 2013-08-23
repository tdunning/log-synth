package org.apache.drill.synth;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.apache.drill.synth.sampler.SchemaSampler;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.IntOptionHandler;
import org.kohsuke.args4j.spi.Setter;

import com.google.common.base.Joiner;

/**
 * Generates plausible database tables in JSON, CSV or TSV format.
 */
public class Synth {
    public static void main(String[] args) throws IOException, CmdLineException {
        Options opts = new Options();
        CmdLineParser parser = new CmdLineParser(opts);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println("Usage: [ -count <number>G|M|K ] -schema schema-file [-format JSON|TSV|CSV ]");
            throw e;
        }

        SchemaSampler s = new SchemaSampler(opts.schema);
        if (opts.format == Format.TSV || opts.format == Format.CSV) {
            format(opts.format, null, s.getFieldNames());
        }
        for (int i = 0; i < opts.count; i++) {
            format(opts.format, s.getFieldNames(), s.sample());
        }
    }

    static Joiner withCommas = Joiner.on(",");
    static Joiner withTabs = Joiner.on("\t");

    private static void format(Format format, List<String> names, List<String> fields) {
        switch (format) {
            case JSON:
                String sep = "{";
                Iterator<String> i = names.iterator();
                for (String field : fields) {
                    System.out.printf("%s\"%s\":\"%s\"", sep, i.next(), field);
                    sep = ",";
                }
                System.out.printf("}\n");
                break;
            case TSV:
                System.out.printf("%s\n", withTabs.join(fields));
                break;
            case CSV:
                System.out.printf("%s\n", withCommas.join(fields));
                break;
        }
    }

    public static enum Format {
        JSON, TSV, CSV
    }

    private static class Options {
        @Option(name = "-count", handler = SizeParser.class)
        int count = 1000;

        @Option(name = "-schema")
        File schema;

        @Option(name = "-format")
        Format format = Format.CSV;

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
