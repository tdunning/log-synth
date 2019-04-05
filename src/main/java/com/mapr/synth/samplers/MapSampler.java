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
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;

import java.util.List;

/**
 * Create a JSON map with values sampled from specified distributions.
 *
 * Thread safe for sampling
 */
public class MapSampler extends FieldSampler {
    private SchemaSampler base = null;

    @JsonCreator
    public MapSampler() {
    }

    @Override
    public void restart() {
        if (base != null) {
            base.restart();
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setValue(List<FieldSampler> base) {
        this.base = new SchemaSampler(base);
    }

    @Override
    public JsonNode sample() {
        Preconditions.checkState(base != null, "Need to specify definition");
        return base.sample();
    }
}
