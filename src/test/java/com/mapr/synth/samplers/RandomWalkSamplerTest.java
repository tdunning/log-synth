package com.mapr.synth.samplers;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.tdunning.math.stats.AVLTreeDigest;
import com.tdunning.math.stats.TDigest;
import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.TDistribution;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class RandomWalkSamplerTest {

    @Test
    public void testBasics() throws IOException {
        // this sampler has four variables
        // g1 is gamma distributed with alpha = 0.2, beta = 0.2
        // v1 is unit normal
        // v2 is normal with mean = 0, sd = 2
        // v3 is gamma-normal with dof=2, mean = 0.
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema015.json"), Charsets.UTF_8).read());

        TDigest tdG1 = new AVLTreeDigest(500);
        TDigest tdG2 = new AVLTreeDigest(500);
        TDigest td1 = new AVLTreeDigest(500);
        TDigest td2 = new AVLTreeDigest(500);
        TDigest td3 = new AVLTreeDigest(500);

        double x1 = 0;
        double x2 = 0;
        double x3 = 0;

        for (int i = 0; i < 1000000; i++) {
            JsonNode r = s.sample();
            tdG1.add(r.get("g1").asDouble());
            tdG2.add(r.get("g2").asDouble());

            double step1 = r.get("v1").get("step").asDouble();
            td1.add(step1);
            x1 += step1;
            assertEquals(x1, r.get("v1").get("value").asDouble(), 0);
            assertEquals(x1, r.get("v1-bare").asDouble(), 0);

            double step2 = r.get("v2").get("step").asDouble();
            td2.add(step2);
            x2 += step2;
            assertEquals(x2, r.get("v2").get("value").asDouble(), 0);

            double step3 = r.get("v3").get("step").asDouble();
            td3.add(step3);
            x3 += step3;
            assertEquals(x3, r.get("v3").get("value").asDouble(), 0);
        }

        // now compare against reference distributions to test accuracy of the observed step distributions
        NormalDistribution normalDistribution = new NormalDistribution();
        GammaDistribution gd1 = new GammaDistribution(0.2, 5);
        GammaDistribution gd2 = new GammaDistribution(1, 1);
        TDistribution tDistribution = new TDistribution(2);
        for (double q : new double[]{0.001, 0.01, 0.1, 0.2, 0.5, 0.8, 0.9, 0.99, 0.99}) {
            double uG1 = gd1.cumulativeProbability(tdG1.quantile(q));
            assertEquals(q, uG1, (1 - q) * q * 10e-2);

            double uG2 = gd2.cumulativeProbability(tdG2.quantile(q));
            assertEquals(q, uG2, (1 - q) * q * 10e-2);

            double u1 = normalDistribution.cumulativeProbability(td1.quantile(q));
            assertEquals(q, u1, (1 - q) * q * 10e-2);

            double u2 = normalDistribution.cumulativeProbability(td2.quantile(q) / 2);
            assertEquals(q, u2, (1 - q) * q * 10e-2);

            double u3 = tDistribution.cumulativeProbability(td3.quantile(q));
            assertEquals(q, u3, (1 - q) * q * 10e-2);
        }
    }
}