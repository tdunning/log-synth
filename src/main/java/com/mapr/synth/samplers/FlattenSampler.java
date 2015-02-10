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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Delegate to another sampler which generates a list of lists.  Flatten that list into a single list.
 *
 * Thread safe for sampling
 */
public class FlattenSampler extends FieldSampler {
    private JsonNodeFactory nodeFactory = JsonNodeFactory.withExactBigDecimals(false);
    private FieldSampler delegate;

    @JsonCreator
    public FlattenSampler(@JsonProperty("value") FieldSampler delegate) {
        this.delegate = delegate;
    }

    @Override
    public JsonNode sample() {
        JsonNode value = delegate.sample();
        ArrayNode r = nodeFactory.arrayNode();

        for (JsonNode component : value) {
            if (component.isArray()) {
                for (JsonNode node : component) {
                    r.add(node);
                }
            } else {
                throw new IllegalArgumentException(String.format("Cannot flatten type %s", component.getClass()));
            }
        }
        return r;
    }
}
