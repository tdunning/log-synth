/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.mahout.math.stats;

import org.apache.mahout.common.RandomUtils;
import org.apache.mahout.common.RandomWrapper;
import org.apache.mahout.math.jet.random.AbstractContinousDistribution;
import org.apache.mahout.math.jet.random.Gamma;
import org.apache.mahout.math.jet.random.Uniform;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class HistoTest {
    @Test
    public void testUniform() {
        RandomWrapper gen = RandomUtils.getRandom();
        for (int i = 0; i < 20; i++) {
            runTest(new Uniform(0, 1, gen), 1000,
                    new double[]{0.0001, 0.001, 0.01, 0.1, 0.5, 0.9, 0.99, 0.999, 0.9999},
                    new double[]{0.0001, 0.001, 0.01, 0.1, 0.5, 0.9, 0.99, 0.999, 0.9999},
                    "uniform");
        }
    }

    @Test
    public void testGamma() {
        // this Gamma distribution is very heavily skewed.  The 0.1%-ile is 6.07e-30 while
        // the median is 0.006 and the 99.9th %-ile is 33.6 while the mean is 1.
        // this severe skew means that we have to have positional accuracy that
        // varies by over 11 orders of magnitude.
        RandomWrapper gen = RandomUtils.getRandom();
        for (int i = 0; i < 20; i++) {
            runTest(new Gamma(0.1, 0.1, gen), 1000,
                    new double[]{6.0730483624079e-30, 6.0730483624079e-20, 6.0730483627432e-10, 5.9339110446023e-03,
                            2.6615455373884e+00, 1.5884778179295e+01, 3.3636770117188e+01},
                    new double[]{0.001, 0.01, 0.1, 0.5, 0.9, 0.99, 0.999},
                    "gamma");
        }
    }

    /**
     * Builds estimates of the CDF of a bunch of data points and checks that the centroids are accurately
     * positioned.  Accuracy is assessed in terms of the estimated CDF which is much more stringent than
     * checking position of quantiles with a single value for desired accuracy.
     *
     * @param gen       Random number generator that generates desired values.
     * @param sizeGuide Control for size of the histogram.
     * @param tag       Label for the output lines
     */
    private void runTest(AbstractContinousDistribution gen, double sizeGuide, double[] xValues, double[] qValues, String tag) {
        Histo dist = new Histo(sizeGuide);
        long t0 = System.nanoTime();
        for (int i = 0; i < 100000; i++) {
            dist.add(gen.nextDouble());
        }
        System.out.printf("# %fus per point\n", (System.nanoTime() - t0) * 1e-3 / 100000);
        System.out.printf("# %d centroids\n", dist.centroids().size());

        Assert.assertTrue(dist.centroids().size() < 7 * sizeGuide);
        System.out.printf("dist\tx\tQ\terror\n");
        for (int i = 0; i < xValues.length; i++) {
            double x = xValues[i];
            double q = qValues[i];
            double estimate = dist.cdf(x);
            System.out.printf("%s\t%.3g\t%.3f\t%.6f\n", tag, x, q, estimate - q);
        }
    }


    @Before
    public void setUp() {
        RandomUtils.useTestSeed();
    }
}
