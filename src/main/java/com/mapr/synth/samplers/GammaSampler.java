package com.mapr.synth.samplers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import org.apache.mahout.math.jet.random.Gamma;

import java.util.Random;

/**
 * Samples from a gamma distribution.  This is often useful for modeling the unknown value of the standard
 * deviation of a normal distribution.
 * <p/>
 * Note that the mean is alpha * scale = alpha / rate
 */
public class GammaSampler extends FieldSampler {
    private static final int SEED_NOT_SET = new Random(1).nextInt();

    private double alpha = 1;
    private double rate = 1;
    private int seed = SEED_NOT_SET;
    private Gamma rand = new Gamma(alpha, rate, new Random());

    @Override
    public JsonNode sample() {
        return new DoubleNode(rand.nextDouble());
    }


    @SuppressWarnings("UnusedDeclaration")
    public void setSeed(int seed) {
        if (seed == SEED_NOT_SET) {
            seed++;
        }
        this.seed = seed;
        init();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setAlpha(double alpha) {
        this.alpha = alpha;
        init();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setRate(double rate) {
        this.rate = rate;
        init();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setScale(double scale) {
        this.rate = 1 / scale;
        init();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setDof(double dof) {
        this.alpha = dof / 2;
        this.rate = 2 / dof;
        init();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setBeta(double beta) {
        this.rate = beta;
        init();
    }

    private void init() {
        if (seed != SEED_NOT_SET) {
            rand = new Gamma(alpha, rate, new Random(seed));
        } else {
            rand = new Gamma(alpha, rate, new Random());
        }
    }
}

