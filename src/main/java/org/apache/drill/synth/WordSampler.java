package org.apache.drill.synth;

/**
 * Sample from English words with somewhat plausible frequency distribution.
 */
public class WordSampler extends FieldSampler {
    private TermGenerator gen = new TermGenerator(new WordGenerator("word-frequency-seed", "other-words"), 1, 0.8);
    public WordSampler() {
    }

    @Override
    public String sample() {
        return gen.sample();
    }
}
