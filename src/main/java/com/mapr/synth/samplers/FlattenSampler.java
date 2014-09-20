package com.mapr.synth.samplers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Delegate to another sampler which generates a list of lists.  Flatten that list into a single list.
 *
 * Thread safe for sampling
 */
public class FlattenSampler extends FieldSampler {
    private JsonNodeFactory nodeFactory = JsonNodeFactory.withExactBigDecimals(false);
    private FieldSampler delegate;

    @JsonCreator
    public FlattenSampler(@JsonProperty("value") FieldSampler delegate) {
        this.delegate = delegate;
    }

    @Override
    public JsonNode sample() {
        JsonNode value = delegate.sample();
        ArrayNode r = nodeFactory.arrayNode();

        for (JsonNode component : value) {
            if (component.isArray()) {
                for (JsonNode node : component) {
                    r.add(node);
                }
            } else {
                throw new IllegalArgumentException(String.format("Cannot flatten type %s", component.getClass()));
            }
        }
        return r;
    }
}
