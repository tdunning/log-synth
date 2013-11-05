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

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import org.apache.mahout.common.RandomUtils;
import org.apache.mahout.common.RandomWrapper;
import org.apache.mahout.math.jet.random.AbstractContinousDistribution;
import org.apache.mahout.math.jet.random.Gamma;
import org.apache.mahout.math.jet.random.Normal;
import org.apache.mahout.math.jet.random.Uniform;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class HistoTest {
    @Test
    public void testUniform() {
        RandomWrapper gen = RandomUtils.getRandom();
        for (int i = 0; i < 5; i++) {
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
        for (int i = 0; i < 5; i++) {
            runTest(new Gamma(0.1, 0.1, gen), 1000,
                    new double[]{6.0730483624079e-30, 6.0730483624079e-20, 6.0730483627432e-10, 5.9339110446023e-03,
                            2.6615455373884e+00, 1.5884778179295e+01, 3.3636770117188e+01},
                    new double[]{0.001, 0.01, 0.1, 0.5, 0.9, 0.99, 0.999},
                    "gamma");
        }
    }

    @Test
    public void testNarrowNormal() {
        // this mixture of a uniform and normal distribution has a very narrow peak which is centered
        // near the median.  Our system should be scale invariant and work well regardless.
        final RandomWrapper gen = RandomUtils.getRandom();
        AbstractContinousDistribution mix = new AbstractContinousDistribution() {
            AbstractContinousDistribution normal = new Normal(0, 1e-5, gen);
            AbstractContinousDistribution uniform = new Uniform(-1, 1, gen);

            @Override
            public double nextDouble() {
                double x;
                if (gen.nextDouble() < 0.5) {
                    x = uniform.nextDouble();
                } else {
                    x = normal.nextDouble();
                }
                return x;
            }
        };

        for (int i = 0; i < 5; i++) {
            runTest(mix, 1000, null, new double[]{0.001, 0.01, 0.1, 0.3, 0.5, 0.7, 0.9, 0.99, 0.999}, "mixture");
        }
    }

    @Test
    public void testRepeatedValues() {
        final RandomWrapper gen = RandomUtils.getRandom();
        AbstractContinousDistribution mix = new AbstractContinousDistribution() {
            @Override
            public double nextDouble() {
                return Math.floor(gen.nextDouble() * 10) / 10.0;
            }
        };

        Histo dist = new Histo((double) 1000);
        long t0 = System.nanoTime();
        Multiset<Double> data = HashMultiset.create();
        for (int i1 = 0; i1 < 100000; i1++) {
            double x = mix.nextDouble();
            data.add(x);
            dist.add(x);
        }

        System.out.printf("# %fus per point\n", (System.nanoTime() - t0) * 1e-3 / 100000);
        System.out.printf("# %d centroids\n", dist.centroidCount());

        // I would be happier with 5x compression, but repeated values make things kind of weird
        Assert.assertTrue("Summary is too large", dist.centroidCount() < 20 * (double) 1000);
        System.out.printf("dist\tx\tQ\terror\n");

//            double estimate = dist.cdf(x);
//            System.out.printf("%s\t%s\t%.3g\t%.3f\t%.6f\n", "mixture", "cdf", x, q, estimate - q);
//            assertEquals(q, estimate, 0.005);

        // all quantiles should round to nearest actual value
        for (int i = 1; i < 10; i++) {
            double z = i / 10.0;
            // well, we only test 80% of the possible values
            for (double q = z - 0.04; q < z + 0.041; q += 0.002) {
                double estimate = dist.quantile(q);
                assertEquals(z, estimate, 0.0005);
                System.out.printf("%s\t%s\t%.3g\t%.3f\t%.6f\n", "mixture", "quantile", q, estimate, estimate - z);
            }
        }
//            assertEquals(q, estimate, 0.005);
//        }
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
        List<Double> data = Lists.newArrayList();
        for (int i = 0; i < 100000; i++) {
            double x = gen.nextDouble();
            data.add(x);
            dist.add(x);
        }
        Collections.sort(data);
        if (xValues == null) {
            xValues = qValues.clone();
            for (int i = 0; i < qValues.length; i++) {
                xValues[i] = data.get((int) (data.size() * qValues[i]));
            }
        }

        System.out.printf("# %fus per point\n", (System.nanoTime() - t0) * 1e-3 / 100000);
        System.out.printf("# %d centroids\n", dist.centroidCount());

        Assert.assertTrue("Summary is too large", dist.centroidCount() < 9 * sizeGuide);
        System.out.printf("dist\tx\tQ\terror\n");
        for (int i = 0; i < xValues.length; i++) {
            double x = xValues[i];
            double q = qValues[i];
            double estimate = dist.cdf(x);
            System.out.printf("%s\t%s\t%.3g\t%.3f\t%.6f\n", tag, "cdf", x, q, estimate - q);
            assertEquals(q, estimate, 0.005);

            estimate = cdf(dist.quantile(q), data);
            System.out.printf("%s\t%s\t%.3g\t%.3f\t%.6f\n", tag, "quantile", x, q, estimate - q);
            assertEquals(q, estimate, 0.005);
        }
    }

    private double cdf(final double x, List<Double> data) {
        int n1 = 0;
        int n2 = 0;
        for (Double v : data) {
            n1 += (v < x) ? 1 : 0;
            n2 += (v <= x) ? 1 : 0;
        }
        return (n1 + n2) / 2.0 / data.size();
    }


    @Before
    public void setUp() {
        RandomUtils.useTestSeed();
    }
}
