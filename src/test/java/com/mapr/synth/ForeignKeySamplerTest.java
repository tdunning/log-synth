package com.mapr.synth;

import com.fasterxml.jackson.databind.JsonNode;
import com.mapr.synth.samplers.ForeignKeySampler;
import org.apache.mahout.math.function.DoubleFunction;
import org.apache.mahout.math.random.*;
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
