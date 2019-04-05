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

package com.mapr.synth;

import com.fasterxml.jackson.databind.JsonNode;
import com.mapr.synth.samplers.ForeignKeySampler;
import org.apache.mahout.math.function.DoubleFunction;
import org.apache.mahout.math.random.Sampler;
import org.junit.Test;

public class ForeignKeySamplerTest {
    @Test
    public void testSample() {
        check(1000, getDistribution(0), new ForeignKeySampler(1000, 0));
        check(1000, getDistribution(0.5), new ForeignKeySampler(1000, 0.5));
        check(1000, getDistribution(1), new ForeignKeySampler(1000, 0.3));
    }

    private DoubleFunction getDistribution(final double alpha) {
        return new DoubleFunction() {
            @Override
            public double apply(double rank) {
                return Math.pow(rank + 1, -alpha);
            }
        };
    }

    private void check(int n, DoubleFunction distribution, Sampler<JsonNode> s) {
        int[] counts = new int[n];
        for (int i = 0; i < 100000; i++) {
            counts[s.sample().asInt()]++;
        }

        double sum = 0;
        double[] p = new double[n];
        for (int i = 0; i < n; i++) {
            p[i] = distribution.apply(i);
            sum += p[i];
        }

        double z1 = 0;
        double z2 = 0;
        for (int i = 0; i < n; i++) {
            z1 += (p[i] - counts[i] / 100000.0) * Math.log(p[i] / sum);
            double expected = p[i] * 100000;
            double deviation = expected - counts[i];
            z2 += deviation * deviation / expected;
        }
        System.out.printf("%.4f %.4f\n", z1, z2);
    }
}
