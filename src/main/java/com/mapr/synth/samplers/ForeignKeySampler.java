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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.google.common.base.Preconditions;
import org.apache.mahout.math.random.Multinomial;

/**
 * Samples from a "foreign key" which is really just an integer.
 * <p>
 * The only cleverness here is that we allow a variable amount of key skew.
 *
 * Thread safe
 */
@JsonIgnoreProperties({"base"})
public class ForeignKeySampler extends FieldSampler {
    private int size = 1000;
    private double skew = 0.5;

    private Multinomial<Integer> base;

    @SuppressWarnings("UnusedDeclaration")
    public ForeignKeySampler() {
    }

    public ForeignKeySampler(int size, double skew) {
        setSize(size);
        setSkew(skew);
    }

    public void setSize(int size) {
        Preconditions.checkArgument(size > 0);
        this.size = size;

        setup();
    }

    public void setSkew(double skew) {
        Preconditions.checkArgument(skew >= 0, "Skew should be non-negative");
        Preconditions.checkArgument(skew <= 3, "Skew should be less than or equal to 3");

        this.skew = skew;

        setup();
    }

    private void setup() {
        base = new Multinomial<>();
        for (int i = 0; i < size; i++) {
            base.add(i, Math.pow(i + 1.0, -skew));
        }
    }

    @Override
    public JsonNode sample() {
      synchronized (this) {
        return new IntNode(base.sample());
      }
    }
}
