package org.apache.drill.synth.samplers;

/**
 * Sample from a list of country codes according to web population.
 */
public class CountrySampler extends StringSampler {
    public CountrySampler() {
        readDistribution("dist.country");
    }
}
