package org.apache.drill.synth;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.common.io.Resources;
import org.apache.mahout.math.stats.OnlineSummarizer;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
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
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema1.txt"), Charsets.UTF_8).read());
        Multiset<String> counts = HashMultiset.create();
        for (int i = 0; i < 10000; i++) {
            counts.add(s.sample().get(1));
        }
        for (int i = 10; i < 99; i++) {
            Assert.assertTrue(counts.elementSet().contains(i + ""));
        }
        assertEquals(99 - 10, counts.elementSet().size());
    }

    @Test
    public void testString() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema2.txt"), Charsets.UTF_8).read());
        Multiset<String> counts = HashMultiset.create();
        double n = 10000;
        for (int i = 0; i < n; i++) {
            counts.add(s.sample().get(1));
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
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema3.txt"), Charsets.UTF_8).read());
        Multiset<String> gender = HashMultiset.create();
        Pattern namePattern = Pattern.compile("[A-Z][a-z]+ [A-Z][a-z]+");
        Pattern addressPattern = Pattern.compile("[0-9]+ [A-Z][a-z]+ [A-Z][a-z]+ [A-Z][a-z]+");
        Pattern datePattern = Pattern.compile("[01][0-9]/[0123][0-9]/20[012][0-9]");
        for (int i = 0; i < 10000; i++) {
            List<String> record = s.sample();
            assertEquals(i, Integer.parseInt(record.get(0)));
            assertTrue(namePattern.matcher(record.get(1)).matches());
            assertTrue(addressPattern.matcher(record.get(3)).matches());
            assertTrue(datePattern.matcher(record.get(4)).matches());
            gender.add(record.get(1));
        }
        check(gender, 0.5 * (1 - 0.02), "MALE");
        check(gender, 0.5 * (1 - 0.02), "FEMALE");
        check(gender, 0.02 * (1 - 0.02), "OTHER");
    }

    @Test
    public void testMisc() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema4.txt"), Charsets.UTF_8).read());
        Multiset<String> country = HashMultiset.create();
        Multiset<String> language = HashMultiset.create();
        Multiset<String> browser = HashMultiset.create();
        Multiset<String> state = HashMultiset.create();
        Multiset<String> os = HashMultiset.create();
        for (int i = 0; i < 10000; i++) {
            List<String> record = s.sample();
            country.add(record.get(0));
            browser.add(record.get(1));
            language.add(record.get(2));
            state.add(record.get(3));
            os.add(record.get(4));
        }

        assertEquals(2542.0, country.count("us"), 200);
        assertEquals(3756.0, browser.count("Chrome"), 200);
        assertEquals(3256.0, language.count("en"), 200);
        assertEquals(1211.8, state.count("ca"), 100);
        assertEquals(5876.0, os.count("win7"), 100);
    }

    @Test
    public void testSequence() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema5.txt"), Charsets.UTF_8).read());
        Splitter onComma = Splitter.on(",").omitEmptyStrings();
        OnlineSummarizer s0 = new OnlineSummarizer();
        OnlineSummarizer s1 = new OnlineSummarizer();
        for (int i = 0; i < 10000; i++) {
            List<String> x = s.sample();
            s0.add(Iterables.size(onComma.split(x.get(0))));
            Iterable<String> numbers = onComma.split(x.get(1));
            s1.add(Iterables.size(numbers));

            for (String number : numbers) {
                int z = Integer.parseInt(number);
                assertTrue(z >= 3 && z < 9);
            }
        }

        assertEquals(5, s0.getMean(), 1);
        assertEquals(10, s1.getMean(), 2);

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
                counts.add(s.sample());
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
