package com.mapr.synth.samplers;

/**
 * Sample from US states.
 * <p/>
 * See http://www.infoplease.com/us/states/population-by-rank.html for data.
 */
public class StateSampler extends StringSampler {
    public StateSampler() {
        readDistribution("dist.states");
    }
}
