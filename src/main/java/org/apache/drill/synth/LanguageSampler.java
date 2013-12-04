package org.apache.drill.synth;

/**
 * Sample from kind of plausible distribution of browser languages.
 */
public class LanguageSampler extends StringSampler {
    public LanguageSampler() {
        readDistribution("dist.language");
    }
}
