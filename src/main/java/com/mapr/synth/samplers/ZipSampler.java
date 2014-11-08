package com.mapr.synth.samplers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;

import java.io.IOException;
import java.util.*;

/**
 * Returns data structures containing various aspects of zip codes including location, population and such.
 */
public class ZipSampler extends FieldSampler {
    private CsvSplitter onComma = new CsvSplitter();
    private JsonNodeFactory nodeFactory = JsonNodeFactory.withExactBigDecimals(false);

    Map<String, List<String>> values = Maps.newHashMap();
    private Random rand = new Random();
    private int zipCount;

    public ZipSampler() {
        try {
            List<String> names = null;
            for (String line : Resources.readLines(Resources.getResource("zip.csv"), Charsets.UTF_8)) {
                if (line.startsWith("#")) {
                    // last comment line contains actual field names
                    names = Lists.newArrayList(onComma.split(line.substring(1)));
                } else {
                    Preconditions.checkState(names != null);
                    assert names != null;
                    Iterable<String> fields = onComma.split(line);
                    Iterator<String> nx = names.iterator();
                    for (String value : fields) {
                        Preconditions.checkState(nx.hasNext());
                        String fieldName = nx.next();
                        List<String> dataList = values.get(fieldName);
                        if (dataList == null) {
                            dataList = Lists.newArrayList();
                            values.put(fieldName, dataList);
                        }
                        dataList.add(value);
                    }
                    if (!names.iterator().next().equals("V1")) {
                        Preconditions.checkState(!nx.hasNext());
                    }
                    zipCount++;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Couldn't read built-in zip code data file", e);
        }
    }

    public void setSeed(long seed) {
        rand = new Random(seed);
    }

    @Override
    public JsonNode sample() {
        int i = rand.nextInt(zipCount);
        ObjectNode r = new ObjectNode(nodeFactory);
        for (String key : values.keySet()) {
            r.set(key, new TextNode(values.get(key).get(i)));
        }
        return r;
    }

    private class CsvSplitter {
        public Iterable<String> split(final String string) {
            return new Iterable<String>() {
                @Override
                public Iterator<String> iterator() {
                    return new Iterator<String>() {
                        int max = string.length();
                        int position = 0;

                        @Override
                        public boolean hasNext() {
                            return position <= max;
                        }

                        private char currentChar() {
                            return string.charAt(position);
                        }

                        @Override
                        public String next() {
                            if (position == max) {
                                position++;
                                return "";
                            } else if (currentChar() == '\"') {
                                position++;
                                int start = position;
                                while (position < max && currentChar() != '\"') {
                                    position++;
                                }
                                if (position >= max) {
                                    throw new IllegalStateException("Unclosed quoted string");
                                }
                                String r = string.substring(start, position);
                                position += 2;
                                return r;
                            } else {
                                int start = position;
                                while (position < max && currentChar() != ',') {
                                    position++;
                                }
                                String r = string.substring(start, position);
                                position++;
                                return r;
                            }
                        }

                        @Override
                        public void remove() {
                            throw new UnsupportedOperationException("Can't remove from CsvSplitter");
                        }
                    };
                }
            };
        }
    }
}
