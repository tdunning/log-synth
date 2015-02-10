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
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Glue together elements of a list as strings.  Should normally only be done with a list of strings.
 *
 * Thread safe for sampling
 */
public class JoinSampler extends FieldSampler {
    private FieldSampler delegate;
    private String separator = ",";

    @JsonCreator
    public JoinSampler(@JsonProperty("value") FieldSampler delegate) {
        this.delegate = delegate;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setSeparator(String separator) {
        this.separator = separator;
    }

    @Override
    public JsonNode sample() {
        JsonNode value = delegate.sample();
        StringBuilder r = new StringBuilder();

        String separator="";
        for (JsonNode component : value) {
            r.append(separator);
            r.append(component.asText());
            separator = this.separator;
        }
        return new TextNode(r.toString());
    }
}

