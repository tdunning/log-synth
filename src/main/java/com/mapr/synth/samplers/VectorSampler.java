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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sample double precision numbers from a variety of distributions.
 * <p>
 * If you specify mean and sd, you will get numbers from a normal distribution. The
 * default values are 0 and 1 respectively, but not that omitting both may have
 * surprising results if min or max is specified.
 * <p>
 * If you specify min and/or max, the distribution will be trimmed, but if min and
 * max are given but neither mean nor sd are given, then the distribution will be
 * uniform rather than a trimmed normal.
 * <p>
 * If you set transform to exp or log, the result will be transformed appropriately.
 * Note that the transform is applied after any summing.
 * <p>
 * If you set resolution to a number, all values will be rounded to the nearest value that
 * is a multiple of the resolution.
 */
public class VectorSampler extends FieldSampler {
    private double mean = Double.NaN;
    private double sd = -1;
    private double min = Double.NaN;
    private double max = Double.NaN;

    private double resolution = Double.NaN;

    private int seed = Integer.MIN_VALUE;

    private Function transform = input -> input;
    private Function inverse = input -> input;

    private FieldSampler length = constant(10000);

    private Random gen = new Random();
    private JsonNodeFactory nodeFactory = JsonNodeFactory.withExactBigDecimals(false);
    private Sampler sampler;

    private interface Sampler {
        double eval();
    }

    private interface Function {
        double apply(double input);
    }

    private AtomicBoolean initialized = new AtomicBoolean(false);

    private void init() {
        if (!initialized.get()) {
            synchronized (this) {
                if (Double.isNaN(mean) && Double.isNaN(max - min)) {
                    throw new IllegalArgumentException("Must set either mean or sd or both min and max");
                }
                if (min >= max) {
                    throw new IllegalArgumentException("Min must be >= max if both are defined");
                }
                if (!Double.isNaN(min)) {
                    min = inverse.apply(min);
                }
                if (!Double.isNaN(max)) {
                    max = inverse.apply(max);
                }

                Function rounder;
                if (Double.isNaN(resolution)) {
                    rounder = input -> input;
                } else {
                    rounder = (double input) -> {
                        return Math.rint(input / resolution) * resolution;
                    };
                }

                if (Double.isNaN(mean)) {
                    sampler = new Sampler() {
                        @Override
                        public double eval() {
                            return rounder.apply(transform.apply(gen.nextDouble() * (max - min) + min));
                        }
                    };
                } else {
                    sampler = new Sampler() {
                        @Override
                        public double eval() {
                            while (true) {
                                double x = rounder.apply(transform.apply(gen.nextGaussian() * sd + mean));
                                if ((Double.isNaN(min) || x >= min) && (Double.isNaN(max) || x <= max)) {
                                    return x;
                                }
                            }
                        }
                    };
                }

                initialized.set(true);
            }
        }
    }

    @Override
    public JsonNode sample() {
        init();

        ArrayNode r = new ArrayNode(nodeFactory);
        int n = (int) length.sample().asDouble();
        for (int i = 0; i < n; i++) {
            r.add(sampler.eval());
        }
        return r;
    }

    public void setTransform(String xform) {
        switch (xform) {
            case "exp":
                transform = input -> Math.exp(input);
                inverse = input -> Math.log(input);
                break;
            case "log":
                transform = input -> Math.log(input);
                inverse = input -> Math.exp(input);
                break;
            default:
                throw new IllegalArgumentException("Transform can only be \"exp\" or \"log\"");
        }
    }

    public void setMax(double max) {
        this.max = max;
    }

    public void setMean(double mean) {
        this.mean = mean;
        if (sd < 0) {
            sd = 1;
        }
    }

    public void setMin(double min) {
        this.min = min;
    }

    public void setSd(double sd) {
        this.sd = sd;
        if (Double.isNaN(mean)) {
            mean = 0;
        }
    }

    public void setResolution(double resolution) {
        this.resolution = resolution;
    }

    public void setLength(JsonNode value) throws IOException {
        if (value.isObject()) {
            length = FieldSampler.newSampler(value.toString());
        } else {
            this.length = constant(value.asDouble());
        }

    }

    public void setSeed(int seed) {
        gen = new Random(seed);
    }
}
