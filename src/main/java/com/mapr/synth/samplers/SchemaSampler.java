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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.mahout.math.random.Sampler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Samples from a specified schema to generate reasonably interesting data.
 */
public class SchemaSampler implements Sampler<JsonNode> {
    private final JsonNodeFactory nodeFactory = JsonNodeFactory.withExactBigDecimals(false);

    // this string is used as a unique sentinel to mark generators whose fields should be flattened
    public static final String FLAT_SEQUENCE_MARKER = ">>flatten-me<<";

    private List<FieldSampler> schema;
    private Set<String> fields;
    private Queue<JsonNode> buffer = new ArrayDeque<>();

    public SchemaSampler(List<FieldSampler> s) {
        init(s);
    }

    public SchemaSampler(String schemaDefinition) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

        init(mapper.readValue(schemaDefinition, new TypeReference<List<FieldSampler>>() {
        }));
    }

    public SchemaSampler(File input) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        init(mapper.readValue(input, new TypeReference<List<FieldSampler>>() {
        }));
    }

    public Iterable<String> getFieldNames() {
        return fields;
    }

    private void init(List<FieldSampler> s) {
        schema = s;
        fields = Sets.newLinkedHashSet();
        for (FieldSampler sampler : s) {
            sampler.getNames(fields);
        }
    }

    @Override
    public JsonNode sample() {
        // we may have buffered records
        JsonNode x = buffer.poll();
        while (x == null) {
            // nothing buffered ... generate some data
            Map<String, JsonNode> generators = Maps.newTreeMap();
            ObjectNode r = nodeFactory.objectNode();
            Iterator<String> fx = fields.iterator();
            for (FieldSampler s : schema) {
                String fieldName = s.getName();
                if (s.isFlat()) {
                    if (fieldName == null) {
                        fieldName = FLAT_SEQUENCE_MARKER;
                    }
                    // this sampler either generates an object or an array
                    JsonNode v = s.sample();
                    if (v.isObject()) {
                        // an object just produces multiple fields in a single record
                        r.setAll((ObjectNode) v);
                    } else if (v.isArray()) {
                        // an array causes records to be buffered
                        generators.put(fieldName, v);
                    } else {
                        r.set(fieldName, v);
                    }
                } else {
                    r.set(fieldName, s.sample());
                }
            }
            // at this point r has all non generator fields
            if (generators.size() > 0) {
                // here we have to handle the case of more than one generator
                crossProduct(buffer, r, Lists.newArrayList(generators.keySet()), generators, 0);
                // the generators may or may not have actually generated anything
                // but that will just cause us to go once more around the circle
                x = buffer.poll();
            } else {
                // with no array generators, we can short-circuit the process
                x = r;
            }
        }
        // yes, there was a buffered record
        return x;
    }

    // exposed for testing
    public static void crossProduct(Queue<JsonNode> buffer, ObjectNode r, List<String> fields, Map<String, JsonNode> generators, int currentFieldIndex) {
        if (currentFieldIndex < fields.size()) {
            // get this generator
            JsonNode values = generators.get(fields.get(currentFieldIndex));
            int n = values.size();
            // and for each value it has
            for (int j = 0; j < n; j++) {
                // set that field or fields ...
                String key = fields.get(currentFieldIndex);
                // yes, we mean to check for pointer equality here
                //noinspection StringEquality
                if (key == FLAT_SEQUENCE_MARKER) {
                    assert values.get(j).isObject();
                    r.setAll(((ObjectNode) values.get(j)));
                }else {
                    r.set(key, values.get(j));
                }
                // and recurse
                crossProduct(buffer, r, fields, generators, currentFieldIndex + 1);
            }
        } else {
            // when we bottom out we add a copied record to the buffer
            buffer.add(r.deepCopy());
        }
    }

    public void restart() {
        for (FieldSampler sampler : schema) {
            sampler.restart();
        }
    }
}
