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

package com.mapr.synth.distributions;

import com.mapr.synth.Util;
import org.apache.mahout.math.jet.random.AbstractContinousDistribution;

import java.util.Random;

/**
 * The wrapped normal is the equivalent of a normal distribution, but applied to a periodic domain. For small
 * deviations, this will be almost identical to the normal distribution, but as large deviations are more common, the
 * consequences of values wrapping around the circle will become more and more significant.
 */
public class WrappedNormal extends AbstractContinousDistribution {
    private double scale;
    private double mean;
    private double sd;
    private Random base = new Random();

    public WrappedNormal(double scale, double mean, double sd) {
        this.scale = scale;
        this.mean = mean;
        this.sd = sd;
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    public void setMean(double mean) {
        this.mean = mean;
    }

    public void setSd(double sd) {
        this.sd = sd;
    }

    public void setSeed(long seed) {
        base.setSeed(seed);
    }

    @Override
    public double nextDouble() {
        return Util.fractionalPart((base.nextGaussian() * sd + mean) / scale) * scale;
    }

}
