package com.mapr.synth.samplers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.mapr.synth.distributions.TermGenerator;
import com.mapr.synth.distributions.WordGenerator;

/**
 * Sample from English words with somewhat plausible frequency distribution.
 */
public class WordSampler extends FieldSampler {
    private TermGenerator gen = new TermGenerator(new WordGenerator("word-frequency-seed", "other-words"), 1, 0.8);
    public WordSampler() {
    }

    @Override
    public JsonNode sample() {
        return new TextNode(gen.sample());
    }
}
