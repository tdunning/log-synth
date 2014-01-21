package org.apache.drill.synth;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.collect.*;
import com.google.common.io.Resources;
import org.apache.mahout.math.stats.OnlineSummarizer;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class SchemaSamplerTest {
    @Test
    public void testFieldNames() throws IOException {
        SchemaSampler s = new SchemaSampler("[{\"name\":\"id\", \"class\":\"id\"}, {\"name\":\"foo\", \"class\":\"address\"}, {\"name\":\"bar\", \"class\":\"date\", \"format\":\"yy-MM-dd\"}, {\"name\":\"baz\", \"class\":\"foreign-key\", \"size\":1000, \"skew\":1}]");
        assertEquals("[id, foo, bar, baz]", Iterables.toString(s.getFieldNames()));
        System.out.printf("%s\n", Iterables.toString(s.sample()));
        System.out.printf("%s\n", Iterables.toString(s.sample()));
        System.out.printf("%s\n", Iterables.toString(s.sample()));
        System.out.printf("%s\n", Iterables.toString(s.sample()));
        System.out.printf("%s\n", Iterables.toString(s.sample()));
    }

    @Test
    public void testInt() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema1.json"), Charsets.UTF_8).read());
        Multiset<String> counts = HashMultiset.create();
        for (int i = 0; i < 10000; i++) {
            counts.add(s.sample().get("size").asText());
        }
        for (int i = 10; i < 99; i++) {
            Assert.assertTrue(counts.elementSet().contains(i + ""));
        }
        assertEquals(99 - 10, counts.elementSet().size());
    }

    @Test
    public void testString() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema2.json"), Charsets.UTF_8).read());
        Multiset<String> counts = HashMultiset.create();
        double n = 10000;
        for (int i = 0; i < n; i++) {
            counts.add(s.sample().get("foo").asText());
        }
        check(counts, 0.95 / 2, "YES");
        check(counts, 0.05 / 2, "NO");
        check(counts, 1.00 / 2, "NA");
    }

    private void check(Multiset<String> counts, double p, String s) {
        double n = counts.size();
        assertEquals(p, counts.count(s) / n, Math.sqrt(n * p * (n - p)));
    }

    @Test
    public void testSeveral() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema3.json"), Charsets.UTF_8).read());
        Multiset<String> gender = HashMultiset.create();
        Pattern namePattern = Pattern.compile("[A-Z][a-z]+ [A-Z][a-z]+");
        Pattern addressPattern = Pattern.compile("[0-9]+ [A-Z][a-z]+ [A-Z][a-z]+ [A-Z][a-z]+");
        Pattern datePattern = Pattern.compile("[01][0-9]/[0123][0-9]/20[012][0-9]");
        for (int i = 0; i < 10000; i++) {
            JsonNode record = s.sample();
            assertEquals(i, record.get("id").asInt());
            assertTrue(namePattern.matcher(record.get("name").asText()).matches());
            assertTrue(addressPattern.matcher(record.get("address").asText()).matches());
            assertTrue(datePattern.matcher(record.get("first_visit").asText()).matches());
            gender.add(record.get("gender").asText());
        }
        check(gender, 0.5 * (1 - 0.02), "MALE");
        check(gender, 0.5 * (1 - 0.02), "FEMALE");
        check(gender, 0.02 * (1 - 0.02), "OTHER");
    }

    @Test
    public void testMisc() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema4.json"), Charsets.UTF_8).read());
        Multiset<String> country = HashMultiset.create();
        Multiset<String> language = HashMultiset.create();
        Multiset<String> browser = HashMultiset.create();
        Multiset<String> state = HashMultiset.create();
        Multiset<String> os = HashMultiset.create();
        for (int i = 0; i < 10000; i++) {
            JsonNode record = s.sample();
            country.add(record.get("co").asText());
            browser.add(record.get("br").asText());
            language.add(record.get("la").asText());
            state.add(record.get("st").asText());
            os.add(record.get("os").asText());
        }

        assertEquals(2542.0, country.count("us"), 200);
        assertEquals(3756.0, browser.count("Chrome"), 200);
        assertEquals(3256.0, language.count("en"), 200);
        assertEquals(1211.8, state.count("ca"), 100);
        assertEquals(5876.0, os.count("win7"), 120);
    }

    @Test
    public void testSequence() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema5.json"), Charsets.UTF_8).read());
        OnlineSummarizer s0 = new OnlineSummarizer();
        OnlineSummarizer s1 = new OnlineSummarizer();
        for (int i = 0; i < 10000; i++) {
            JsonNode x = s.sample();
            s0.add(Iterables.size(x.get("c")));
            s1.add(Iterables.size(x.get("d")));

            for (JsonNode n : x.get("d")) {
                int z = n.asInt();
                assertTrue(z >= 3 && z < 9);
            }
        }

        assertEquals(5, s0.getMean(), 1);
        assertEquals(10, s1.getMean(), 2);
    }


    @Test
    public void testSequenceArray() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema6.json"), Charsets.UTF_8).read());
        for (int i = 0; i < 10; i++) {
            JsonNode x = s.sample();
            Iterator<JsonNode> values = x.get("x").elements();
            assertEquals(3, values.next().asInt());
            assertEquals(6, values.next().asInt());
            assertEquals(8, values.next().asInt());

            assertFalse(values.hasNext());
        }
    }

    @Test
    public void testSkewedInteger() throws IOException {
        // will give fields x, y, z, q with different skewness
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema7.json"), Charsets.UTF_8).read());

        SortedMultiset<Integer> x = TreeMultiset.create();
        SortedMultiset<Integer> y = TreeMultiset.create();
        SortedMultiset<Integer> z = TreeMultiset.create();
        SortedMultiset<Integer> q = TreeMultiset.create();
        for (int i = 0; i < 10000; i++) {
            JsonNode record = s.sample();
            x.add(record.get("x").asInt());
            y.add(record.get("y").asInt());
            z.add(record.get("z").asInt());
            q.add(record.get("q").asInt());
        }

        for (int i = 10; i < 20; i++) {
            assertEquals(1000, x.count(i), 100);
            assertEquals(1900 - (i - 10) * 200, y.count(i), 120);
            assertEquals(100 + (i - 10) * 200, z.count(i), 120);
            // these magic numbers are a fit to the empirical distribution of q as computed by R
            double kq = 122623.551282 - 27404.139083 * i + 2296.601107 * i * i - 85.510684 * i * i * i + 1.193182 * i * i * i * i;
            // accuracy should get better for smaller numbers
            assertEquals(kq, q.count(i), (25.0 - i) / 15 * 120);
        }
    }

    @Test
    public void testFileSampler() throws IOException {
        File f = new File("numbers.tsv");
        f.deleteOnExit();

        BufferedWriter out = Files.newBufferedWriter(f.toPath(), Charsets.UTF_8);
        out.write("a\tb\n");
        for (int i = 0; i < 20; i++) {
            out.write(i + "\t" + (i * i) + "\n");
        }
        out.close();

        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema8.json"), Charsets.UTF_8).read());

        for (int k = 0; k < 1000; k++) {
            JsonNode r = s.sample();
            assertEquals(6, r.get("x").get("x").asInt() + r.get("x").get("y").asInt());
            int i = r.get("y").get("a").asInt();
            assertEquals(i * i, r.get("y").get("b").asInt());
        }
    }

    @Test
    public void testFlatten() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema9.json"), Charsets.UTF_8).read());

        for (int k = 0; k < 10; k++) {
            JsonNode r = s.sample();
            assertEquals("3,6,8", r.get("x").asText());
            assertTrue(r.get("y").asInt() >= 1 && r.get("y").asInt() < 5);
        }
    }

    public static class StringSamplerTest {
        @Test
        public void testEmptyDist() {
            StringSampler s = new StringSampler();
            try {
                s.setDist(new HashMap<String, Object>());
                fail("Should have detected empty distribution");
            } catch (IllegalArgumentException e) {
                // whew ... that's what we wanted
            }
        }

        @Test
        public void testSimple() {
            StringSampler s = new StringSampler();
            s.setDist(ImmutableMap.of("a", "3", "b", 5, "c", 1.0));

            Multiset<String> counts = HashMultiset.create();
            for (int i = 0; i < 1000; i++) {
                counts.add(s.sample().asText());
            }

            assertEquals(3, counts.elementSet().size());
            check(counts, "a", 1000 * 3.0 / 9.0);
            check(counts, "b", 1000 * 5.0 / 9.0);
            check(counts, "c", 1000 * 1.0 / 9.0);
        }

        private void check(Multiset<String> counts, String a, double n) {
            assertEquals(n, counts.count(a), 3 * Math.sqrt(n));
        }
    }
}
