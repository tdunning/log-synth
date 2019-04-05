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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import org.apache.mahout.common.RandomUtils;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Samples from Social Security Numbers with roughly equal representation across different ages
 */
public class SsnSampler extends FieldSampler {
    private Random rand = RandomUtils.getRandom();

    private final JsonNodeFactory nodeFactory = JsonNodeFactory.withExactBigDecimals(false);
    private final Map<String, List<String>> values = Maps.newHashMap();
    private final List<String> codes = Lists.newArrayList();
    private Set<String> keepTypes = Sets.newHashSet("normal");
    private Set<String> keepFields = Sets.newHashSet("ssn", "state");
    private List<String> names;
    private boolean verbose = true;

    public SsnSampler() {
        Splitter onComma = Splitter.on(",").trimResults();
        try {
            names = null;
            //noinspection UnstableApiUsage
            for (String line : Resources.readLines(Resources.getResource("ssn-seeds"), Charsets.UTF_8)) {
                if (line.startsWith("#")) {
                    // last comment line contains actual field names
                    names = Lists.newArrayList(onComma.split(line.substring(1)));
                } else {
                    Preconditions.checkState(names != null);

                    List<String> fields = Lists.newArrayList(onComma.split(line));
                    for (int i = Integer.parseInt(fields.get(1)); i <= Integer.parseInt(fields.get(1)); i++) {
                        String key = String.format("%03d", i);
                        values.put(key, fields.subList(2, fields.size()));
                        codes.add(key);
                    }

                }
            }
            assert names != null;
            names = names.subList(2, names.size());
        } catch (IOException e) {
            throw new RuntimeException("Couldn't read built-in resource", e);
        }
    }

    @SuppressWarnings("unused")
    public void setSeed(long seed) {
        rand = new Random(seed);
    }

    /**
     * Limits the fields that are returned to only those that are specified.
     */
    @SuppressWarnings("UnusedDeclaration")
    public void setFields(String fields) {
        keepFields = Sets.newHashSet(Splitter.on(Pattern.compile("[\\s,;]+")).split(fields));
        for (String field : keepFields) {
            Preconditions.checkArgument(names.contains(field) || "ssn".equals(field), "Illegal field: %s", field);
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setTypes(String types) {
        keepTypes = Sets.newHashSet(Splitter.on(Pattern.compile("[\\s,;]+")).split(types));
        Set<String> legalTypes = ImmutableSet.of("normal", "extra");
        for (String type : keepTypes) {
            Preconditions.checkArgument(legalTypes.contains(type), "Illegal type requested: %s, needed one of %s", type, legalTypes);
        }
    }


    @SuppressWarnings("UnusedDeclaration")
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public JsonNode sample() {
        while (true) {
            int i = rand.nextInt(codes.size());
            List<String> fields = values.get(codes.get(i));

            if (keepTypes.contains(fields.get(names.indexOf("type")))) {
                if (verbose) {
                    ObjectNode rx = new ObjectNode(nodeFactory);
                    Iterator<String> nx = names.iterator();
                    for (String field : fields) {
                        Preconditions.checkState(nx.hasNext());
                        String fieldName = nx.next();
                        if (keepFields.contains(fieldName)) {
                            rx.set(fieldName, new TextNode(field));
                        }
                    }
                    Preconditions.checkState(!nx.hasNext());
                    if (keepFields.contains("ssn")) {
                        rx.set("ssn", new TextNode(String.format("%s-%02d-%04d", codes.get(i), rand.nextInt(99) + 1, rand.nextInt(9999) + 1)));
                    }
                    return rx;
                } else {
                    return new TextNode(String.format("%s-%02d-%04d", codes.get(i), rand.nextInt(99) + 1, rand.nextInt(9999) + 1));
                }
            }
        }
    }
}
