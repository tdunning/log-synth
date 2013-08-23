package org.apache.drill.synth.fieldsampler;

import java.util.Random;

import org.apache.drill.synth.FieldSampler;
import org.apache.mahout.common.RandomUtils;

/**
 * Samples from a "foreign key" which is really just an integer.
 *
 * The only cleverness here is that we allow a variable amount of key skew.
 */
public class IntegerSampler extends FieldSampler {
    private int min = 0;
    private int max = 100;
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
