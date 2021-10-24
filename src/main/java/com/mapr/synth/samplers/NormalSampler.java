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
import com.fasterxml.jackson.databind.node.DoubleNode;
import org.apache.mahout.math.jet.random.Normal;

import java.util.Random;

/**
 * Samples from a normal distribution.
 */
public class NormalSampler extends FieldSampler {
    private double sd = 1;
    private double precision = Double.NaN;
    private double mean = 0;
    private double min = -Double.MAX_VALUE;
    private double max = Double.MAX_VALUE;

    private int seed = Integer.MAX_VALUE;
    private Normal rand = null;


    @SuppressWarnings("UnusedDeclaration")
    public void setSeed(int seed) {
        if (seed == Integer.MAX_VALUE) {
            seed = 1;
        }
        this.seed = seed;
        init();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setMean(double mean) {
        this.mean = mean;
        init();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setSd(double sd) {
        this.sd = sd;
        this.precision = Double.NaN;
        init();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setPrecision(double precision) {
        this.precision = precision;
        this.sd = Double.NaN;
        init();
    }

    public void setMin(double min) {
        this.min = min;
        init();
    }

    public void setMax(double max) {
        this.max = max;
        init();
    }

    private void init() {
        if (max <= min) {
            throw new IllegalArgumentException("Parameter max must be greater than min");
        }
        if (max - min < 0.1 * sd) {
            throw new IllegalArgumentException("Value of max-min is too small, should be > 0.1 * sd");
        }
        if (Double.isNaN(sd)) {
            sd = 1 / precision;
        }
        if (seed != Integer.MAX_VALUE) {
            rand = new Normal(mean, sd, new Random(seed));
        } else {
            rand = new Normal(mean, sd, new Random());
        }
    }

    @Override
    public JsonNode doSample() {
        double x;
        do {
            x = rand.nextDouble();
        } while (x < min || x > max);
        return new DoubleNode(x);
    }
}
