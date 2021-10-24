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
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import org.apache.mahout.math.random.Multinomial;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * Sample from a space of goofy but somewhat plausible street names.
 *
 * Tip of the hat to http://www.jimwegryn.com/Names/StreetNameGenerator.htm
 *
 * Thread safe
 */
public class StreetNameSampler extends FieldSampler {
    List<Multinomial<String>> sampler = ImmutableList.of(
            new Multinomial<>(), new Multinomial<>(), new Multinomial<>()
    );

    public StreetNameSampler() {
        Splitter onTabs = Splitter.on("\t");
        try {
            for (String line : Resources.readLines(Resources.getResource("street-name-seeds"), Charsets.UTF_8)) {
                if (!line.startsWith("#")) {
                    Iterator<Multinomial<String>> i = sampler.iterator();
                    for (String name : onTabs.split(line)) {
                        i.next().add(name, 1);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Couldn't read built-in resource", e);
        }
    }

    @Override
    public JsonNode doSample() {
        synchronized (this) {
          return new TextNode(sampler.get(0).sample() + " " + sampler.get(1).sample() + " " + sampler.get(2).sample());
        }
    }
}
