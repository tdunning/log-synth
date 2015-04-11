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
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;

/**
 * Delegate to another sampler which generates an object.  The fields of this object inserted into the
 * parent of this sampler.  This is handy when working with samplers that return complex types such as
 * VIN an ZIP.  If you want, for instance, the zip-code, latitude and longitude as three variables in a
 * record, you can do this:
 *
 * <per>
 *     {"class":"flatten", "type":"flatten", "prefix":"", "value": {
 *         "class":"zip", "fields":["zip", "latitude", "longitude"]
 *     }}
 * </per>
 *
 * By default, the promoted values have names prefixed by the name of the flattener. You can set this
 * prefix to any value you like including the empty string.
 * <p/>
 * Thread safe for sampling
 */
public class FlattenSampler extends FieldSampler {
    private JsonNodeFactory nodeFactory = JsonNodeFactory.withExactBigDecimals(false);
    private FieldSampler delegate;
    private String prefix = "";

    @JsonCreator
    public FlattenSampler(@JsonProperty("name") String name, @JsonProperty("value") FieldSampler delegate) {
        this.delegate = delegate;
        prefix = name + "-";
        setName(name);
        setFlattener(true);
    }

    @SuppressWarnings("unused")
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public JsonNode sample() {
        JsonNode value = delegate.sample();

        if (value.isObject()) {
            ObjectNode r = new ObjectNode(nodeFactory);
            for (Iterator<String> it = value.fieldNames(); it.hasNext(); ) {
                String key = it.next();
                JsonNode v = value.get(key);
                r.set(prefix + key, v);
            }
            return r;
        } else {
            return value;
        }
    }
}
