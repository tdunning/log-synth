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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.mapr.synth.distributions.ChineseRestaurant;

/**
 * Samples from a Pitman-Yor process with parameters alpha (strength) and d (discount).
 * For d = 0, this is a Dirichlet process and will have close order of (alpha * log T) unique values
 * (where T is the number of samples taken. For d > 0, the growth will be as
 * alpha * T^d.
 */
public class LongTailSampler extends FieldSampler     {
    ChineseRestaurant dist;

    public LongTailSampler(@JsonProperty("alpha") double alpha, @JsonProperty("d") double discount) {
        dist = new ChineseRestaurant(alpha, discount);
    }

    @Override
    public JsonNode doSample() {
        return new IntNode(dist.sample());
    }
}
