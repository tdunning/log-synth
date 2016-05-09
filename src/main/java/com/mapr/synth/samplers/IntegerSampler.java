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
import org.apache.mahout.common.RandomUtils;

import java.util.Random;

/**
 * Samples from a "foreign key" which is really just an integer.
 *
 * Thread safe
 */

public class IntegerSampler extends FieldSampler {
    private int min = 0;
    private int max = 100;
    private int power = 0;
    private Random base;
    private String format = null;

    public IntegerSampler() {
        base = RandomUtils.getRandom();
    }

    public void setMax(int max) {
        this.max = max;
    }

    public void setMin(int min) {
        this.min = min;
    }

    /**
     * Sets the amount of skew.  Skew is added by taking the min of several samples.
     * Setting power = 0 gives uniform distribution, setting it to 5 gives a very
     * heavily skewed distribution.
     * <p/>
     * If you set power to a negative number, the skew is reversed so large values
     * are preferred.
     *
     * @param skew Controls how skewed the distribution is.
     */
    public void setSkew(int skew) {
        this.power = skew;
    }

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
        }else {
            return new TextNode(String.format(format, r));
        }
      }
    }

}
