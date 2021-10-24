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
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.io.Resources;
import org.apache.mahout.math.random.Multinomial;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sample from a multinomial of strings.
 *
 * Tip of the hat to http://www.jimwegryn.com/Names/StreetNameGenerator.htm
 *
 * Thread safe for sampling
 */
public class StringSampler extends FieldSampler {
    private AtomicReference<Multinomial<String>> distribution = new AtomicReference<>();

    public StringSampler() {
    }

    public StringSampler(String resource) {
        readDistribution(resource);
    }

    protected void readDistribution(String resourceName) {
        try {
            if (distribution.compareAndSet(null, new Multinomial<>())) {
                Splitter onTab = Splitter.on("\t").trimResults();
                double i = 20;
                for (String line : Resources.readLines(Resources.getResource(resourceName), Charsets.UTF_8)) {
                    if (!line.startsWith("#")) {
                        Iterator<String> parts = onTab.split(line).iterator();
                        String name = translate(parts.next());
                        double weight;
                        if (parts.hasNext()) {
                            weight = Double.parseDouble(parts.next());
                        } else {
                            weight = 1.0 / i;
                        }
                        distribution.get().add(name, weight);
                    }
                    i++;
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Couldn't read built-in resource file", e);
        }
    }

    public void setDist(Map<String, ?> dist) {
        Preconditions.checkArgument(dist.size() > 0);
        distribution.compareAndSet(null, new Multinomial<>());
        for (String key : dist.keySet()) {
            distribution.get().add(key, Double.parseDouble(dist.get(key).toString()));
        }
    }


    protected String translate(String s) {
        return s;
    }

    @Override
    public JsonNode doSample() {
      synchronized (this) {
        return new TextNode(distribution.get().sample());
      }
    }
}
