package com.mapr.synth.samplers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;

import java.util.List;

/**
 * Create a JSON map with values sampled from specified distributions.
 *
 * Thread safe for sampling
 */
public class MapSampler extends FieldSampler {
    private SchemaSampler base = null;

    @JsonCreator
    public MapSampler() {
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setValue(List<FieldSampler> base) {
        this.base = new SchemaSampler(base);
    }

    @Override
    public JsonNode sample() {
        Preconditions.checkState(base != null, "Need to specify definition");
        return base.sample();
    }
}
