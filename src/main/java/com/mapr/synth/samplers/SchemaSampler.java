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
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.apache.mahout.math.random.Sampler;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * Samples from a specified schema to generate reasonably interesting data.
 */
public class SchemaSampler implements Sampler<JsonNode> {
    private final JsonNodeFactory nodeFactory = JsonNodeFactory.withExactBigDecimals(false);

    private List<FieldSampler> schema;
    private List<String> fields;

    public SchemaSampler(List<FieldSampler> s) {
        init(s);
    }

    public SchemaSampler(String schemaDefinition) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

        init(mapper.<List<FieldSampler>>readValue(schemaDefinition, new TypeReference<List<FieldSampler>>() {
        }));
    }

    public SchemaSampler(File input) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        init(mapper.<List<FieldSampler>>readValue(input, new TypeReference<List<FieldSampler>>() {
        }));
    }

    public List<String> getFieldNames() {
        return fields;
    }

    private void init(List<FieldSampler> s) {
        schema = s;
        fields = Lists.transform(schema, new Function<FieldSampler, String>() {
            @Override
            public String apply(FieldSampler input) {
                return input.getName();
            }
        });
    }

    @Override
    public JsonNode sample() {
        ObjectNode r = nodeFactory.objectNode();
        Iterator<String> fx = fields.iterator();
        for (FieldSampler s : schema) {
            if (s.isFlat()) {
                JsonNode v = s.sample();
                for (Iterator<String> it = v.fieldNames(); it.hasNext(); ) {
                    String key = it.next();
                    r.set(key, v.get(key));
                }
            } else {
                r.set(fx.next(), s.sample());
            }
        }
        return r;
    }
}
