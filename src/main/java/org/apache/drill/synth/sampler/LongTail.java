package org.apache.drill.synth.sampler;

import java.util.List;

import org.apache.mahout.math.random.Sampler;

import com.google.common.collect.Lists;

/**
 * Samples from a set of things based on a long-tailed distribution.  This converts
 * the ChineseRestaurant distribution from a distribution over integers into a distribution
 * over more plausible looking things like words.
 */
public abstract class LongTail<T> implements Sampler<T> {
    private ChineseRestaurant base;
    private List<T> things = Lists.newArrayList();

    protected LongTail(double alpha, double discount) {
        base = new ChineseRestaurant(alpha, discount);
    }

    public T sample() {
        int n = base.sample();
        while (n >= things.size()) {
            things.add(createThing());
        }
        return things.get(n);
    }

    public ChineseRestaurant getBaseDistribution() {
        return base;
    }

    protected abstract T createThing();

    public void setThing(int i, T thing) {
        while (things.size() <= i) {
            things.add(null);
        }
        things.set(i, thing);
    }
}
