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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Charsets;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.SortedMultiset;
import com.google.common.collect.TreeMultiset;
import com.google.common.io.Resources;
import com.mapr.synth.samplers.SchemaSampler;
import com.mapr.synth.samplers.StringSampler;
import org.apache.mahout.math.stats.OnlineSummarizer;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SchemaSamplerTest {
    @Test
    public void testCross() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

        // the cross product should generate a list of records as long as the product of the
        // options given to it.
        TreeMap<String, JsonNode> generators = new TreeMap<>();
        generators.put(SchemaSampler.FLAT_SEQUENCE_MARKER, mapper.readTree("[{a: 1, b: 2}, {a: 2, b: 3}]"));
        generators.put("foo", mapper.readTree("[{foo_a: 1, foo_b: 2}, {foo_a: 2, foo_b: 3}, {foo_a: 3, foo_b: 4}]"));

        Queue<JsonNode> r = new ArrayDeque<>();
        SchemaSampler.crossProduct(r, (ObjectNode) mapper.readTree("{base_a: 23}"),
                Lists.newArrayList(generators.keySet()), generators, 0);
        assertEquals(6, r.size());
        Multiset<String> counts = HashMultiset.create();
        for (JsonNode node : r) {
            assertEquals(23, node.get("base_a").asInt());
            assertEquals(2, node.get("foo").size());
            counts.add(String.format("a=%d", node.get("a").asInt()));
            counts.add(String.format("nest_a=%d", node.get("foo").get("foo_a").asInt()));
            counts.add(String.format("a=%d, nest_a=%d", node.get("a").asInt(), node.get("foo").get("foo_a").asInt()));
        }
        assertEquals(3, counts.count("a=1"));
        assertEquals(2, counts.count("nest_a=1"));
        assertEquals(1, counts.count("a=1, nest_a=1"));

        assertEquals(3, counts.count("a=2"));
        assertEquals(2, counts.count("nest_a=2"));
        assertEquals(1, counts.count("a=1, nest_a=2"));
    }

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
        //noinspection UnstableApiUsage
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema001.json"), Charsets.UTF_8).read());
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
        //noinspection UnstableApiUsage
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema002.json"), Charsets.UTF_8).read());
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
        //noinspection UnstableApiUsage
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema003.json"), Charsets.UTF_8).read());
        Multiset<String> gender = HashMultiset.create();
        Pattern namePattern = Pattern.compile("[A-Z][a-z]+ [A-Z][a-z]+");
        Pattern addressPattern = Pattern.compile("[0-9]+ [A-Z][a-z]+ [A-Z][a-z]+ [A-Z][a-z]+");
        Pattern datePattern1 = Pattern.compile("[01][0-9]/[0123][0-9]/20[012][0-9]");
        Pattern datePattern2 = Pattern.compile("2014-0[12]-[0123][0-9]");
        Pattern datePattern3 = Pattern.compile("[01][0-9]/[0123][0-9]/199[5-9]");
        for (int i = 0; i < 10000; i++) {
            JsonNode record = s.sample();
            assertEquals(i, record.get("id").asInt());
            assertTrue(namePattern.matcher(record.get("name").asText()).matches());
            assertTrue(addressPattern.matcher(record.get("address").asText()).matches());
            assertTrue(datePattern1.matcher(record.get("first_visit").asText()).matches());
            assertTrue(datePattern2.matcher(record.get("second_date").asText()).matches());
            assertTrue(datePattern3.matcher(record.get("third_date").asText()).matches());
            gender.add(record.get("gender").asText());
        }
        check(gender, 0.5 * (1 - 0.02), "MALE");
        check(gender, 0.5 * (1 - 0.02), "FEMALE");
        check(gender, 0.02 * (1 - 0.02), "OTHER");
    }

    @Test
    public void testMisc() throws IOException {
        //noinspection UnstableApiUsage
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema004.json"), Charsets.UTF_8).read());
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
        //noinspection UnstableApiUsage
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema005.json"), Charsets.UTF_8).read());
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
        //noinspection UnstableApiUsage
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema006.json"), Charsets.UTF_8).read());
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
    public void testMap() throws IOException {
        //noinspection UnstableApiUsage
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema011.json"), Charsets.UTF_8).read());
        for (int i = 0; i < 100; i++) {
            JsonNode x = s.sample();
            assertEquals(i, x.get("id").asInt());
            int v = x.get("stuff").get("a").asInt();
            assertTrue(v == 3 || v == 4);
            v = x.get("stuff").get("b").asInt();
            assertTrue(v == 4 || v == 5);
        }
    }

    @Test
    public void testSkewedInteger() throws IOException {
        // will give fields x, y, z, q with different skewness
        //noinspection UnstableApiUsage
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema007.json"), Charsets.UTF_8).read());

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
            assertEquals(kq, q.count(i), (25.0 - i) / 10 * 120);
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

        //noinspection UnstableApiUsage
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema008.json"), Charsets.UTF_8).read());

        for (int k = 0; k < 1000; k++) {
            JsonNode r = s.sample();
            assertEquals(6, r.get("x").get("x").asInt() + r.get("x").get("y").asInt());
            int i = r.get("y").get("a").asInt();
            assertEquals(i * i, r.get("y").get("b").asInt());
        }
    }

    @Test
    public void testJoin() throws IOException {
        //noinspection UnstableApiUsage
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema009.json"), Charsets.UTF_8).read());

        for (int k = 0; k < 10; k++) {
            JsonNode r = s.sample();
            assertEquals("3,6,8", r.get("x").asText());
            assertTrue(r.get("y").asInt() >= 1 && r.get("y").asInt() < 5);
            assertTrue(r.get("z").asText().matches("(xyz(,xyz)*)?"));
        }
    }

    @Test
    public void testEvents() throws IOException, ParseException {
        //noinspection UnstableApiUsage
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema012.json"), Charsets.UTF_8).read());
        long t = System.currentTimeMillis();

        SimpleDateFormat df0 = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat df1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat df2 = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        JsonNode old = s.sample();

        long old1 = df0.parse(old.get("foo1").asText()).getTime();
        assertTrue(Math.abs(old1 - t) < TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS));

        long old2 = df1.parse(old.get("foo2").asText()).getTime();
        assertEquals((double) old2, df1.parse("2014-01-01 00:00:00").getTime(), 10.0);

        long old3 = df2.parse(old.get("foo3").asText()).getTime();
        assertEquals(old3, df1.parse("2014-02-01 00:00:00").getTime(), 10);

        long old4 = old.get("foo4").asLong();
        assertEquals(0, old4);

        long old5 = old.get("foo5").asLong();
        assertEquals(5432, old5);

        double sum1 = 0;
        double sum2 = 0;
        double sum3 = 0;
        double sum4 = 0;
        double sum5 = 0;

        final int N = 10000;

        for (int k = 0; k < N; k++) {
            JsonNode r = s.sample();

            long t1 = df0.parse(r.get("foo1").asText()).getTime();
            sum1 += t1 - old1;
            old1 = t1;

            long t2 = df1.parse(r.get("foo2").asText()).getTime();
            sum2 += t2 - old2;
            old2 = t2;

            long t3 = df2.parse(r.get("foo3").asText()).getTime();
            sum3 += t3 - old3;
            old3 = t3;

            long t4 = r.get("foo4").asLong();
            sum4 += t4 - old4;
            old4 = t4;

            long t5 = r.get("foo5").asLong();
            sum5 += t5 - old5;
            old5 = t5;
        }

        assertEquals((double) TimeUnit.MILLISECONDS.convert(10, TimeUnit.DAYS), (sum1 / N), 0.03 * (sum1 / N));
        assertEquals(100, sum2 / N, 3);
        assertEquals(2000, sum3 / N, 2000 * 0.03);

        assertEquals(10.0, sum4 / N, 10 * 0.03);
        assertEquals(100.0, sum5 / N, 100 * 0.03);
    }

    @Test
    public void testFlattenedFieldNames() throws IOException {
        // field names for flattened sequences are kind of tricky
        //noinspection UnstableApiUsage
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema036.json"), Charsets.UTF_8).read());
        List<String> names = Lists.newArrayList(s.getFieldNames());
        assertTrue(names.contains("a"));
        assertTrue(names.contains("b"));
        assertTrue(names.contains("foo"));
        assertTrue(names.contains("latitude"));
        assertTrue(names.contains("longitude"));
        assertTrue(names.contains("zipx"));
        assertEquals(6, names.size());
    }

    public static class StringSamplerTest {
        @Test
        public void testEmptyDist() {
            StringSampler s = new StringSampler();
            try {
                s.setDist(new HashMap<>());
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
