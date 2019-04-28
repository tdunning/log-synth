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
import org.apache.mahout.math.jet.random.Gamma;

import java.util.Random;

/**
 * Samples from a gamma distribution.  This is often useful for modeling the unknown value of the standard
 * deviation of a normal distribution.
 * <p>
 * Note that the mean is alpha * scale = alpha / rate
 */
class GammaSampler extends FieldSampler {
    private static final int SEED_NOT_SET = new Random(1).nextInt();

    // use either alpha, beta (or rate) or dof, scale to parameterize
    private double alpha = 1;
    private double beta = 1;

    private double dof = Double.NaN;
    private double scale = Double.NaN;

    private int seed = SEED_NOT_SET;
    private Gamma rand = new Gamma(alpha, 1 / beta, new Random());

    @Override
    public JsonNode sample() {
        return new DoubleNode(rand.nextDouble());
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
    public void setAlpha(double alpha) {
        this.alpha = alpha;
        dof = Double.NaN;
        scale = Double.NaN;
        init();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setRate(double rate) {
        this.beta = 1 / rate;

        dof = Double.NaN;
        scale = Double.NaN;
        init();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setBeta(double beta) {
        this.beta = beta;

        dof = Double.NaN;
        scale = Double.NaN;
        init();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setDof(double dof) {
        alpha = Double.NaN;
        beta = Double.NaN;

        this.dof = dof;
        init();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setScale(double scale) {
        alpha = Double.NaN;
        beta = Double.NaN;

        this.scale = scale;
        init();
    }

    private void init() {
        double a, b;
        if (Double.isNaN(alpha) && Double.isNaN(beta) && !Double.isNaN(dof)) {
            if (Double.isNaN(scale)) {
                scale = 1;
            }
            a = dof / 2;
            b = scale * dof / 2;
        } else if (Double.isNaN(dof) && !Double.isNaN(alpha) && !Double.isNaN(beta)) {
            a = alpha;
            b = beta;
        } else {
            throw new IllegalArgumentException("Must use either alpha,beta,rate (or defaults) or dof,scale to parametrize gamma");
        }
        if (seed != SEED_NOT_SET) {
            rand = new Gamma(a, b, new Random(seed));
        } else {
            rand = new Gamma(a, b, new Random());
        }
    }
}

