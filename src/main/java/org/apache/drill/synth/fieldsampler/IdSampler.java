package org.apache.drill.synth.fieldsampler;

import org.apache.drill.synth.FieldSampler;

/**
 * Samples from a "foreign key" which is really just an integer.
 *
 * The only cleverness here is that we allow a variable amount of key skew.
 */
public class IdSampler extends FieldSampler {
    private int current = 0;

    public IdSampler() {
    }

    @Override
    public String sample() {
        return Integer.toString(current++);
    }

    public void setStart(int start) {
        this.current = start;
    }
}
