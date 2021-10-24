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
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Set;

/**
 * Create a JSON map with values sampled from specified distributions.
 *
 * Thread safe for sampling
 */
public class MapSampler extends FieldSampler {
    private SchemaSampler base = null;
    private List<FieldSampler> children = null;

    @JsonCreator
    public MapSampler() {
    }

    @Override
    public void restart() {
        if (base != null) {
            base.restart();
//            for (FieldSampler child : children) {
//                child.setFlattener(isFlat());
//            }
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setValue(List<FieldSampler> children) {
        this.base = new SchemaSampler(children);
        this.children = Lists.newArrayList(children);
    }

    @Override
    public void getNames(Set<String> fields) {
        Iterables.addAll(fields, base.getFieldNames());
    }

    @Override
    public JsonNode doSample() {
        Preconditions.checkState(base != null, "Need to specify definition");
        return base.sample();
    }
}
