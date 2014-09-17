package com.mapr.synth.samplers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
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
        return new IntNode(r);
      }
    }

}
