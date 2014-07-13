package org.apache.drill.synth.samplers;

/**
 * Sample from Browsers.
 * <p/>
 * See http://en.wikipedia.org/wiki/Usage_share_of_web_browsers
 */
public class BrowserSampler extends StringSampler {
    public BrowserSampler() {
        readDistribution("dist.browser");
    }
}
