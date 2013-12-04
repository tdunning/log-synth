package org.apache.drill.synth;

/**
 * Sample from a moderately realistic distribution of operating system indicators.
 */
public class OperatingSystemSampler extends StringSampler {
    public OperatingSystemSampler() {
        readDistribution("dist.os");
    }
}
