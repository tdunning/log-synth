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
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.AtomicDouble;

import java.io.IOException;
import java.util.Random;

/**
 * Does a random walk where the next step is distributed according to a Normal distribution, but where
 * the standard deviation of the step distribution may not be fixed.
 */
public class RandomWalkSampler extends FieldSampler {
    private static final JsonNode ONE = new DoubleNode(1);
    private static final JsonNode ZERO = new DoubleNode(0);
    private static final int SEED_NOT_SET = new Random(1).nextInt();

    private int seed = SEED_NOT_SET;

    private FieldSampler sd = new FieldSampler() {
        @Override
        public JsonNode doSample() {
            return ONE;
        }
    };
    private FieldSampler mean = new FieldSampler() {
        @Override
        public JsonNode doSample() {
            return ZERO;
        }
    };

    private FieldSampler stepDistribution = null;
    private Random rand = new Random();
    private boolean verbose = false;

    private AtomicDouble state = new AtomicDouble();
    private double start = 0;

    @Override
    public void restart() {
        state.set(start);
    }

    @Override
    public JsonNode doSample() {
        double step;
        if (stepDistribution == null) {
            step = rand.nextGaussian() * sd.doSample().asDouble() + mean.doSample().asDouble();
        } else {
            step = stepDistribution.doSample().asDouble();
        }
        double newState = state.addAndGet(step);

        if (verbose) {
            ObjectNode r = new ObjectNode(JsonNodeFactory.withExactBigDecimals(false));
            r.set("value", new DoubleNode(newState));
            r.set("step", new DoubleNode(step));
            return r;
        } else {
            return new DoubleNode(newState);
        }
    }

    public void setStart(double start) {
        this.start = start;
        state.set(start);
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setSeed(int seed) {
        if (seed == SEED_NOT_SET) {
            seed++;
        }
        this.seed = seed;
        init();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setS(JsonNode value) throws IOException {
        if (value.isObject()) {
            sd = FieldSampler.newSampler(value.toString());
        } else {
            this.sd = constant(value.asDouble());
        }
        init();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setPrecision(final JsonNode value) throws IOException {
        if (value.isObject()) {
            sd = new FieldSampler() {
                FieldSampler base = FieldSampler.newSampler(value.toString());

                @Override
                public JsonNode doSample() {
                    return new DoubleNode(Math.sqrt(1 / base.doSample().asDouble()));
                }
            };
        } else {
            this.sd = constant(Math.sqrt(1 / value.asDouble()));
        }
        init();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setVariance(final JsonNode value) throws IOException {
        if (value.isObject()) {
            sd = new FieldSampler() {
                FieldSampler base = FieldSampler.newSampler(value.toString());

                @Override
                public JsonNode doSample() {
                    return new DoubleNode(Math.sqrt(base.doSample().asDouble()));
                }
            };
        } else {
            this.sd = constant(Math.sqrt(value.asDouble()));
        }
        init();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setSD(final JsonNode value) throws IOException {
        if (value.isObject()) {
            sd = new FieldSampler() {
                FieldSampler base = FieldSampler.newSampler(value.toString());

                @Override
                public JsonNode doSample() {
                    return new DoubleNode(base.doSample().asDouble());
                }
            };
        } else {
            sd = constant(value.asDouble());
        }
        init();
    }

    @SuppressWarnings("unused")
    public void setMean(final JsonNode value) throws IOException {
        if (value.isObject()) {
            mean = FieldSampler.newSampler(value.toString());
        } else {
            mean = constant(value.asDouble());
        }
        init();
    }

    @SuppressWarnings("unused")
    @JsonProperty("step-distribution")
    public void setStepDistribution(final JsonNode dist) {
        if (dist.isObject()) {
            stepDistribution = FieldSampler.newSampler(dist);
        } else {
            throw new IllegalArgumentException("Wanted definition for step distribution");
        }
    }

    private void init() {
        if (seed != SEED_NOT_SET) {
            rand = new Random(seed);
        }
    }

}
