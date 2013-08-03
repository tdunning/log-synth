package org.apache.drill.synth;

import org.apache.mahout.common.RandomUtils;

import java.util.Random;

/**
 * Samples from a "foreign key" which is really just an integer.
 *
 * The only cleverness here is that we allow a variable amount of key skew.
 */
public class IntegerSampler extends FieldSampler {
    private int min;
    private int max;
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

    @Override
    public String sample() {
        return java.lang.Integer.toString(min + base.nextInt(max - min));
    }

}
