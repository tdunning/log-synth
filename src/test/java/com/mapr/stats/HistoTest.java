package com.mapr.stats;

import org.apache.mahout.common.RandomUtils;
import org.apache.mahout.math.jet.random.AbstractContinousDistribution;
import org.apache.mahout.math.jet.random.Gamma;
import org.apache.mahout.math.jet.random.Uniform;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


public class HistoTest {
    @Test
    public void testUniform() {
        runTest(new Uniform(0, 1, RandomUtils.getRandom()), 1000);
    }

    @Test
    public void testGamma() {
        // this Gamma distribution is very heavily skewed so that the deciles are
        // 0.0, 2.59e-26, 8.79e-12, 7.20e-09, 4.02e-07, 6.90e-06, 6.31e-05, 0.000402, 0.00190, 0.00741, 0.0273, Inf
        // this severe skew means that we have to have positional accuracy that varies by nearly 30 orders of
        // magnitude.
        runTest(new Gamma(.1, 10, RandomUtils.getRandom()), 1000);
    }

    /**
     * Builds estimates of the CDF of a bunch of data points and checks that the centroids are accurately
     * positioned.  Accuracy is assessed in terms of the estimated CDF which is much more stringent than
     * checking position of quantiles with a single value for desired accuracy.
     * @param gen          Random number generator that generates desired values.
     * @param sizeGuide    Control for size of the histogram.
     */
    private void runTest(AbstractContinousDistribution gen, double sizeGuide) {
        Histo dist = new Histo(sizeGuide);
        for (int i = 0; i < 100000; i++) {
            dist.add(gen.nextDouble());
        }
        Assert.assertTrue(dist.centroids().size() < 3 * sizeGuide);
        int n = 0;
        double q = 0;
        for (Double x : dist.summary.keySet()) {
            Histo.Group c = dist.summary.get(x);
            double estimatedCdf = ((double) n + c.count / 2) / dist.size();
            double actualCdf = gen.cdf(x);
            if (estimatedCdf > q) {
                System.out.printf("%.3f\t%.3g\n", actualCdf, x);
                q += 0.1;
            }
//            System.out.printf("%.4f\t%.4f\n", actualCdf, (estimatedCdf - actualCdf));
            Assert.assertEquals(0, estimatedCdf - actualCdf, 1.0/Math.sqrt(sizeGuide));
            n += c.count;
        }
    }

    @Before
    public void setUp() {
        RandomUtils.useTestSeed();
    }
}
