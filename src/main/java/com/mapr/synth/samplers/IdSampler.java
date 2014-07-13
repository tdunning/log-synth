package com.mapr.synth.samplers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;

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
    public JsonNode sample() {
        return new IntNode(current++);
    }

    public void setStart(int start) {
        this.current = start;
    }
}
