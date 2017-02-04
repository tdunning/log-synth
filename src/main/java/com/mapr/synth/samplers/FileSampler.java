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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.io.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;

/**
 * Samples lines from a file
 *
 * Thread safe for sampling
 */
public class FileSampler extends FieldSampler {
    private JsonNode data;
    private IntegerSampler index;
    private int skew = Integer.MAX_VALUE;

    public FileSampler() {
    }

    @SuppressWarnings("unused")
    public void setFile(String lookup) throws IOException {
        if (lookup.matches(".*\\.json")) {
            readJsonData(Files.newReader(new File(lookup), Charsets.UTF_8));
        } else {
            List<String> lines = Files.readLines(new File(lookup), Charsets.UTF_8);
            readDelimitedData(lookup, lines);
        }

        setupIndex();
    }

    private void setupIndex() {
        index = new IntegerSampler();
        index.setMin(0);
        index.setMax(data.size());
        if (skew != Integer.MAX_VALUE) {
            index.setSkew(skew);
        }
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setResource(String lookup) throws IOException {
        if (lookup.matches(".*\\.json")) {
            readJsonData(Files.newReader(new File(Resources.getResource(lookup).getFile()), Charsets.UTF_8));
        } else {
            List<String> lines = Resources.readLines(Resources.getResource(lookup), Charsets.UTF_8);
            readDelimitedData(lookup, lines);
        }

        setupIndex();
    }

    private void readDelimitedData(String lookup, List<String> lines) {
        Splitter splitter;
        if (lookup.matches(".*\\.csv")) {
            splitter = Splitter.on(",");
        } else if (lookup.matches(".*\\.tsv")) {
            splitter = Splitter.on("\t");
        } else {
            throw new IllegalArgumentException("Must have file with .csv, .tsv or .json suffix");
        }

        List<String> names = Lists.newArrayList(splitter.split(lines.get(0)));
        JsonNodeFactory nf = JsonNodeFactory.withExactBigDecimals(false);
        ArrayNode localData = nf.arrayNode();
        for (String line : lines.subList(1, lines.size())) {
            ObjectNode r = nf.objectNode();
            List<String> fields = Lists.newArrayList(splitter.split(line));
            Preconditions.checkState(names.size() == fields.size(), "Wrong number of fields, expected ", names.size(), fields.size());
            Iterator<String> ix = names.iterator();
            for (String field : fields) {
                r.put(ix.next(), field);
            }
            localData.add(r);
        }
        data = localData;
    }

    private void readJsonData(BufferedReader input) throws IOException {
        ObjectMapper om = new ObjectMapper();
        data = om.readTree(input);
    }

    /**
     * Sets the amount of skew.  Skew is added by taking the min of several samples.
     * Setting power = 0 gives uniform distribution, setting it to 5 gives a very
     * heavily skewed distribution.
     * <p>
     * If you set power to a negative number, the skew is reversed so large values
     * are preferred.
     *
     * @param skew Controls how skewed the distribution is.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void setSkew(int skew) {
        if (index != null) {
            index.setSkew(skew);
        } else {
            this.skew = skew;
        }
    }

    @Override
    public JsonNode sample() {
      synchronized (this) {
        return data.get(index.sample().asInt());
      }
    }
}
