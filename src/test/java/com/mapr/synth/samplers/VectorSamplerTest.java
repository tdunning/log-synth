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
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VectorSamplerTest {
    @Test
    public void testVector() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema029.json"), Charsets.UTF_8).read());
        for (int i = 0; i < 10; i++) {
            JsonNode data = s.sample();
                       /*
    {
        "class": "vector",
        "name": "prices",
        "mean": 4.65,
        "sd": 0.01,
        "length": 10000,
        "transform": "exp",
        "seed": 1,
    },
    */
            JsonNode v = data.get("prices");
            assertTrue(v.isArray());
            assertEquals(10000, v.size());
            double[] v1 = new double[10000];
            double[] v2 = new double[10000];
            for (int j = 0; j < 10000; j++) {
                v1[j] = v.get(j).asDouble();
                v2[j] = Math.log(v1[j]);
            }
            assertEquals(100, median(v1), 0.03);
            assertEquals(100, mean(v1), 0.05);
            assertEquals(Math.log(100), mean(v2), 0.001);
            assertEquals(0.01, sd(v2), 0.0003);
            assertTrue(isNormal(v2, Math.log(100), 0.01));

            /*
    {
        "class": "vector",
        "name": "zero",
        "mean": 0,
        "sd": 10,
        "length": 10000,
        "seed": 2
    },
    */
            v = data.get("zero");
            assertTrue(v.isArray());
            for (int j = 0; j < 10000; j++) {
                v1[j] = v.get(j).asDouble();
            }
            assertEquals(0, mean(v1), 0.3);
            assertEquals(10, sd(v1), 0.2);
            assertTrue(isNormal(v1, 0, 10));
            /*
    {
        "class": "vector",
        "name": "clipped",
        "mean": 0,
        "sd": 10,
        "length": 10000,
        "max": 0,
        "seed": 3
    },
    */
            v = data.get("clipped");
            assertTrue(v.isArray());
            Random rand = new Random();
            for (int j = 0; j < 10000; j++) {
                v1[j] = v.get(j).asDouble();
                assertTrue(v1[j] <= 0);
                v1[j] = v1[j] * (rand.nextBoolean() ? 1 : -1);
            }
            assertEquals(0, mean(v1), 0.3);
            assertEquals(10, sd(v1), 0.3);
            assertTrue(isNormal(v1, 0, 10));

            /*
    {
        "class": "vector",
        "name": "ten",
        "min": 1,
        "max": 10,
        "length": 20000,
        "transform": "log",
        "seed": 4
    }
]
                        */
            v = data.get("ten");
            assertTrue(v.isArray());
            for (int j = 0; j < 10000; j++) {
                v1[j] = v.get(j).asDouble();
                v2[j] = Math.exp(v1[j]);
                assertTrue(v1[j] >= 1);
                assertTrue(v1[j] <= 10);
            }
            assertTrue(isUniform(v2, Math.exp(1), Math.exp(10)));

            v = data.get("coarse");
            assertTrue(v.isArray());
            for (int j = 0; j < 10000; j++) {
                double x = v.get(j).asDouble();
                assertTrue(x >= 1);
                assertTrue(x <= 10);
                assertEquals(Math.rint(x / 0.1) * 0.1, x, 1e-10);
            }
        }
    }

    private boolean isUniform(double[] vx, double min, double max) {
        Arrays.sort(vx);
        double diff = 0;
        for (int i = 0; i < vx.length; i++) {
            double q = (double) i / (vx.length - 1);
            diff = Math.max(diff, Math.abs(q - (vx[i] - min) / (max - min)));
        }
        return diff < 5.0 / Math.sqrt(vx.length);
    }

    private boolean isNormal(double[] vx, double mean, double sd) {
        Arrays.sort(vx);
        NormalDistribution n = new NormalDistribution(mean, sd);
        double diff = 0;
        for (int i = 0; i < vx.length; i++) {
            double q = (double) i / (vx.length - 1);
            diff = Math.max(diff, Math.abs(q - n.cumulativeProbability(vx[i])));
        }

        return diff < 5.0 / Math.sqrt(vx.length);
    }

    private double mean(double[] vx) {
        double sum = 0;
        for (double v : vx) {
            sum += v;
        }
        return sum / vx.length;
    }

    private double sd(double[] vx) {
        double m = mean(vx);
        double sum = 0;
        for (double v : vx) {
            sum += (v - m) * (v - m);
        }
        return Math.sqrt(sum / (vx.length - 1));
    }

    private double median(double[] vx) {
        Arrays.sort(vx);
        int n1 = (vx.length - 1) / 2;
        int n2 = vx.length / 2;
        return (vx[n1] + vx[n2]) / 2;
    }
}