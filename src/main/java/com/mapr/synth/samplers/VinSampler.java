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

package com.mapr.synth.samplers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.*;
import com.google.common.io.LineProcessor;
import com.google.common.io.Resources;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Samples somewhat realistic Vehicle Identification Numbers (VIN).
 * <p>
 * Parameters can be used to limit the samples produced:
 * <p>
 * <ul>
 * <li><em>countries</em> Comma separated list of two letter country codes.  Acceptable codes include
 * us, ca, north_america, eu, uk, de, kr, jp</li>
 * <li><em>years</em> Comma separated list of year ranges.  Each year range is a 4 digit year or a hyphenated year range</li>
 * <li><em>makes</em> Comma separated list of car manufacturers.  Can include ford, chevrolet, gm, bmw, vw, audi,
 * mitsubishi, subaru, mazda, honda, toyota, hyundai, kia, nissan, ferrari, jaguar, delorean, chrysler, tesla</li>
 * <li><em>verbose</em> If set to true, the result will include a textual description of aspects of the generated VIN</li>
 * </ul>
 */
public class VinSampler extends FieldSampler {
    private static Splitter onComma = Splitter.on(",").trimResults().omitEmptyStrings();
    private static Pattern rangePattern = Pattern.compile("([12][09]\\d\\d)(-[12][09]\\d\\d)");

    private static Map<String, String> makes;

    private static SetMultimap<String, String> byCountry = HashMultimap.create();
    private static SetMultimap<String, String> byMake = HashMultimap.create();

    private static Map<String, String> restraint = Maps.newHashMap();
    private static List<String> restraintCodes;

    private static Map<String, String> fordModels = Maps.newHashMap();
    private static List<String> fordModelCodes;

    private static Map<String, String> fordEngines = Maps.newHashMap();
    private static List<String> fordEngineCodes;

    private static List<String> fordPlantCodes = Lists.newArrayList("5", "V", "G", "M", "F");

    private static Map<String, String> bmwModels;
    private static List<String> bmwModelCodes;

    private static Map<String, String> bmwPlants;
    private static List<String> bmwPlantCodes;

    private static Map<String, Integer> letterCode;
    private static List<String> letters;

    private static List<Integer> checkWeights = Lists.newArrayList(8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2);

    static {
        fill();
    }


    private Random rand = new Random();
    private List<String> legalCodes;
    private List<Integer> legalYears;

    private AtomicInteger sequenceCounter = new AtomicInteger();
    private boolean verbose = false;
    private JsonNodeFactory nodeFactory = JsonNodeFactory.withExactBigDecimals(false);

    @SuppressWarnings("UnusedDeclaration")
    public VinSampler() throws FileNotFoundException {
        legalCodes = Lists.newArrayList(makes.keySet());
        setYears("1990-2014");
    }

    @Override
    public JsonNode sample() {
        ObjectNode r = new ObjectNode(nodeFactory);

        String manufacturer = randomCode(legalCodes);
        String restraint = randomCode(restraintCodes);

        int year = randomCode(legalYears);
        String yearCode = computeYearCode(year);
        int sequence = sequenceCounter.incrementAndGet();

        String front;
        String plant;

        String make = makes.get(manufacturer);

        switch (make) {
            case "Ford": {
                String model = randomCode(fordModelCodes);
                String engine = randomCode(fordEngineCodes);
                plant = randomCode(fordPlantCodes);
                front = pad(manufacturer, 3, "AAAAAAAAAAAAAAAAAA") + restraint + pad(model, 3, "0000000000000000") + engine;
                if (verbose) {
                    r.set("model", new TextNode(fordModels.get(model)));
                    r.set("engine", new TextNode(fordEngines.get(engine)));
                }
                break;
            }
            case "BMW":
            case "BMW M": {
                String model = randomCode(bmwModelCodes);
                plant = randomCode(bmwPlantCodes);
                front = pad(manufacturer, 3, "AAAAAAAAAAAAAAAAAA") + restraint + model;
                if (verbose) {
                    r.set("model", new TextNode(bmwModels.get(model)));
                    r.set("plant", new TextNode(bmwPlants.get(plant)));
                }
                break;
            }
            default: {
                String model = gibberish(4);
                plant = gibberish(1);
                front = pad(manufacturer, 3, "AAAAAAAAAAAAAAAAAA") + restraint + model;
                break;
            }
        }
        String check = "0";

        String rawVin = front + check + yearCode + plant + String.format("%06d", sequence);
        String vin = addCheckDigit(rawVin);

        if (verbose) {
            r.set("VIN", new TextNode(vin));
            r.set("manufacturer", new TextNode(makes.get(manufacturer)));
            r.set("year", new IntNode(year));
        } else {
            return new TextNode(vin);
        }
        return r;
    }

    private String gibberish(int length) {
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < length; i++) {
            buf.append(randomCode(letters));
        }
        return buf.toString();
    }

    private String computeYearCode(int year) {
        Preconditions.checkArgument(year >= 1980 && year <= 2020, "Invalid year %d", year);
        year = year - 1980;
        return "ABCDEFGHJKLMNPRSTVWXY123456789ABCDEFGHJK".substring(year, year + 1);
    }

    // exposed for testing
    String addCheckDigit(String rawVin) {
        int sum = 0;
        for (int i = 0; i < rawVin.length(); i++) {
            Integer code = letterCode.get(rawVin.substring(i, i + 1));
            if (code != null) {
                sum += checkWeights.get(i) * code;
            } else {
                throw new IllegalArgumentException(String.format("Invalid character: %s in VIN: %s", rawVin.substring(i, i + 1), rawVin));
            }
        }
        sum = sum % 11;
        if (sum == 10) {
            return rawVin.substring(0, 8) + "X" + rawVin.substring(9);
        } else {
            return rawVin.substring(0, 8) + sum + rawVin.substring(9);
        }
    }


    private <T> T randomCode(List<T> codes) {
        return codes.get(rand.nextInt(codes.size()));
    }


    @SuppressWarnings("UnusedDeclaration")
    public void setCountries(String countries) {
        Set<String> s = Sets.newHashSet();
        for (String country : onComma.split(countries)) {
            s.addAll(byCountry.get(country));
        }
        legalCodes.retainAll(s);
        if (legalCodes.size() == 0) {
            throw new IllegalArgumentException("No VIN's match all constraints");
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setCountry(String countries) {
        setCountries(countries);
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setMakes(String makes) {

        Set<String> s = Sets.newHashSet();
        for (String country : onComma.split(makes)) {
            s.addAll(byMake.get(country));
        }
        legalCodes.retainAll(s);
        if (legalCodes.size() == 0) {
            throw new IllegalArgumentException("No VIN's match all constraints");
        }

    }

    @SuppressWarnings("UnusedDeclaration")
    public void setMake(String makes) {
        setMakes(makes);
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setYears(String years) {
        legalYears = yearCodes(years);
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setYear(String years) {
        setYears(years);
    }

    private List<Integer> yearCodes(String years) {
        List<Integer> x = Lists.newArrayList();
        for (String range : onComma.split(years)) {
            Matcher m = rangePattern.matcher(range);
            if (m.matches()) {
                if (m.groupCount() == 1) {
                    x.add(Integer.parseInt(m.group(1)));

                } else {
                    int start = Integer.parseInt(m.group(1));
                    int end = Integer.parseInt(m.group(2).substring(1));
                    for (int year = start; year <= end; year++) {
                        x.add(Integer.parseInt(m.group(1)));
                    }
                }
            } else {
                throw new IllegalArgumentException("Can't parse range " + range);
            }
        }
        return x;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setSeed(int seed) {
        rand.setSeed(seed);
    }

    @SuppressWarnings("UnusedDeclaration")
    private void addYear(int year, List<String> years) {
        year = Math.max(year, 1980);
        year = Math.min(year, 2019);
        int offset = year - 1980;
        years.add("ABCDEFGHJKLMNPRSTVWXY123456789ABCDEFGHJK".substring(offset, offset + 1));
    }

    private String pad(String s, int length, String padding) {
        return (s + padding).substring(0, length);
    }

    private static Map<String, String> mapResource(String name) throws IOException {
        final Splitter onTab = Splitter.on("\t");

        return Resources.readLines(Resources.getResource(name), Charsets.UTF_8, new LineProcessor<Map<String, String>>() {
            final Map<String, String> r = Maps.newHashMap();

            @Override
            public boolean processLine(String line) throws IOException {
                Iterator<String> pieces = onTab.split(line).iterator();
                String key = pieces.next();
                r.put(key, pieces.next());
                return true;
            }

            @Override
            public Map<String, String> getResult() {
                return r;
            }
        });
    }

    private static SetMultimap<String, String> multiMapResource(String name) throws IOException {
        final Splitter onTab = Splitter.on("\t");

        return Resources.readLines(Resources.getResource(name), Charsets.UTF_8, new LineProcessor<SetMultimap<String, String>>() {
            final SetMultimap<String, String> r = HashMultimap.create();

            @Override
            public boolean processLine(String line) throws IOException {
                Iterator<String> pieces = onTab.split(line).iterator();
                String key = pieces.next();
                r.put(key, pieces.next());
                return true;
            }

            @Override
            public SetMultimap<String, String> getResult() {
                return r;
            }
        });
    }

    private static void fill() {
        letterCode = Maps.newLinkedHashMap();
        letterCode.put("A", 1);
        letterCode.put("B", 2);
        letterCode.put("C", 3);
        letterCode.put("D", 4);
        letterCode.put("E", 5);
        letterCode.put("F", 6);
        letterCode.put("G", 7);
        letterCode.put("H", 8);

        letterCode.put("J", 1);
        letterCode.put("K", 2);
        letterCode.put("L", 3);
        letterCode.put("M", 4);
        letterCode.put("N", 5);

        letterCode.put("P", 7);

        letterCode.put("R", 9);
        letterCode.put("S", 2);
        letterCode.put("T", 3);
        letterCode.put("U", 4);
        letterCode.put("V", 5);
        letterCode.put("W", 6);
        letterCode.put("X", 7);
        letterCode.put("Y", 8);
        letterCode.put("Z", 9);

        letterCode.put("0", 0);
        letterCode.put("1", 1);
        letterCode.put("2", 2);
        letterCode.put("3", 3);
        letterCode.put("4", 4);
        letterCode.put("5", 5);
        letterCode.put("6", 6);
        letterCode.put("7", 7);
        letterCode.put("8", 8);
        letterCode.put("9", 9);
        letters = Lists.newArrayList(letterCode.keySet());

        try {
            bmwModels = mapResource("bmw-models.tsv");
            bmwModelCodes = Lists.newArrayList(bmwModels.keySet());
            bmwPlants = mapResource("bmw-plants.tsv");
            bmwPlantCodes = Lists.newArrayList(bmwPlants.keySet());
            fordModels = mapResource("ford-models.tsv");
            fordEngines = mapResource("ford-engines.tsv");
            fordEngineCodes = Lists.newArrayList(fordEngines.keySet());
            fordModelCodes = Lists.newArrayList(fordModels.keySet());
            restraint = mapResource("ford-restraints.tsv");
            restraintCodes = Lists.newArrayList(restraint.keySet());
            byMake = multiMapResource("vin-by-make.tsv");
            makes = mapResource("vin-make.tsv");
            byCountry = multiMapResource("vin-by-country.tsv");
        } catch (IOException e) {
            throw new RuntimeException("Can't read resources");
        }
    }
}
