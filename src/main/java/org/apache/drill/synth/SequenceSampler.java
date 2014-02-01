package org.apache.drill.synth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Random;

/**
 * Create comma separated samples from another field definition or array of field definitions.
 */
public class SequenceSampler extends FieldSampler {
    private JsonNodeFactory nodeFactory = JsonNodeFactory.withExactBigDecimals(false);
    private double averageLength = 5;
    private FieldSampler base = null;
    private Random gen = new Random();
    private List<FieldSampler> array = null;

    @JsonCreator
    public SequenceSampler() {
    }

    public void setLength(double length) {
        averageLength = length;
    }

    public void setBase(FieldSampler base) {
        this.base = base;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setArray(List<FieldSampler> base) {
        this.array = Lists.newArrayList(base);
    }

    @Override
    public JsonNode sample() {
        Preconditions.checkState(array != null || base != null, "Need to specify either base or array");
        ArrayNode r = nodeFactory.arrayNode();
        if (base != null) {
            int n = (int) Math.floor(-averageLength * Math.log(gen.nextDouble()));
            for (int i = 0; i < n; i++) {
                r.add(base.sample());
            }
        } else {
            for (FieldSampler fieldSampler : array) {
                r.add(fieldSampler.sample());
            }
        }
        return r;
    }
}
