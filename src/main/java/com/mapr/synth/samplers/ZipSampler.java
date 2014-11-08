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
    private JsonNodeFactory nodeFactory = JsonNodeFactory.withExactBigDecimals(false);

    private Map<String, List<String>> values = Maps.newHashMap();
    private Random rand = new Random();
    private int zipCount;
    private double latitudeFuzz = 0;
    private double longitudeFuzz = 0;
    private boolean onlyContinental = false;

    public ZipSampler() {
        try {
            List<String> names = null;
            for (String line : Resources.readLines(Resources.getResource("zip.csv"), Charsets.UTF_8)) {
                CsvSplitter onComma = new CsvSplitter();
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

    @SuppressWarnings("UnusedDeclaration")
    public void setLatitudeFuzz(double fuzz) {
        latitudeFuzz = fuzz;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setLongitudeFuzz(double fuzz) {
        longitudeFuzz = fuzz;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setOnlyContinental(boolean onlyContinental) {
        this.onlyContinental = onlyContinental;
    }

    @Override
    public JsonNode sample() {
        boolean keep = false;
        ObjectNode r = null;
        while (!keep) {
            int i = rand.nextInt(zipCount);
            keep = true;
            r = new ObjectNode(nodeFactory);
            valueExtraction:
            for (String key : values.keySet()) {
                switch (key) {
                    case "latitude": {
                        String v = values.get(key).get(i);
                        if (v == null || v.equals("")) {
                            if (!onlyContinental) {
                                r.set(key, new TextNode(v));
                            } else {
                                keep = false;
                                break valueExtraction;
                            }
                        } else {
                            double x = Double.parseDouble(v);
                            if (!onlyContinental || (x >= 22 && x <= 50)) {
                                if (latitudeFuzz > 0) {
                                    v = Double.toString(x + rand.nextGaussian() * latitudeFuzz);
                                }
                            } else {
                                keep = false;
                                break valueExtraction;
                            }
                        }
                        r.set(key, new TextNode(v));
                        break;
                    }

                    case "longitude": {
                        String v = values.get(key).get(i);
                        if (v == null || v.equals("")) {
                            r.set(key, new TextNode(v));
                        } else {
                            double x = Double.parseDouble(v);
                            if (!onlyContinental || (x >= -130 && x <= -65)) {
                                if (longitudeFuzz > 0 && key.equals("longitude")) {
                                    v = Double.toString(Double.parseDouble(v) + rand.nextGaussian() * longitudeFuzz);
                                }
                            } else {
                                keep = false;
                                break valueExtraction;
                            }
                        }
                        r.set(key, new TextNode(v));
                        break;
                    }
                    default:
                        r.set(key, new TextNode(values.get(key).get(i)));
                        break;
                }
            }
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
