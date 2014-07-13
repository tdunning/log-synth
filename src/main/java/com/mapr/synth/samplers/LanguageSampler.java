package com.mapr.synth.samplers;

/**
 * Sample from kind of plausible distribution of browser languages.
 */
public class LanguageSampler extends StringSampler {
    public LanguageSampler() {
        readDistribution("dist.language");
    }
}
