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
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.mapr.synth.Util;
import org.apache.mahout.common.RandomUtils;

import java.util.Random;

/**
 * Samples from a "foreign key" which is really just an integer.
 * <p>
 * Thread safe
 */

public class IntegerSampler extends FieldSampler {
    private int min = 0;
    private int max = 100;
    private int power = 0;
    private Random base;
    private String format = null;

    @SuppressWarnings("WeakerAccess")
    public IntegerSampler() {
        base = RandomUtils.getRandom();
    }

    public void setMax(String max) {
        this.max = Util.parseInteger(max);
    }

    public void setMax(JsonNode max) {
        this.max = Util.parseInteger(max);
    }

    public void setMin(JsonNode min) {
        this.min = Util.parseInteger(min);
    }

    @SuppressWarnings("SameParameterValue")
    void setMinAsInt(int min) {
        this.min = min;
    }

    void setMaxasInt(int max) {
        this.max = max;
    }

    /**
     * Sets the amount of skew.  Skew is added by taking the min of several samples.
     * Setting power = 0 gives uniform distribution, setting it to 5 gives a very
     * heavily skewed distribution.
     * <p>
     * If you set power to a negative number, the skew is reversed so large values
     * are preferred.
     *
     * @param skew Controls how skewed the distribution is.
     */
    public void setSkew(int skew) {
        this.power = skew;
    }

    @SuppressWarnings("unused")
    public void setFormat(String format) {
        this.format = format;
    }

    @Override
    public JsonNode sample() {
        synchronized (this) {
            int r = power >= 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
            if (power >= 0) {
                for (int i = 0; i <= power; i++) {
                    r = Math.min(r, min + base.nextInt(max - min));
                }
            } else {
                int n = -power;
                for (int i = 0; i <= n; i++) {
                    r = Math.max(r, min + base.nextInt(max - min));
                }
            }
            if (format == null) {
                return new IntNode(r);
            } else {
                return new TextNode(String.format(format, r));
            }
        }
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

}
