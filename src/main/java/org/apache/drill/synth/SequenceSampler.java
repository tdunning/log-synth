package org.apache.drill.synth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Random;

/**
 * Create comma separated samples from another field definition or array of field definitions.
 */
public class SequenceSampler extends FieldSampler {
    private JsonNodeFactory nodeFactory = JsonNodeFactory.withExactBigDecimals(false);
    private double averageLength = 5;
    private List<FieldSampler> base;
    private Random gen = new Random();

    @JsonCreator
    public SequenceSampler() {
    }

    public void setLength(double length) {
        averageLength = length;
    }

    public void setBase(FieldSampler base) {
        this.base = ImmutableList.of(base);
    }

    public void setArray(List<FieldSampler> base) {
        this.base = Lists.newArrayList(base);
    }

    @Override
    public JsonNode sample() {
        ArrayNode r = nodeFactory.arrayNode();
        if (base.size() == 1) {
            int n = (int) Math.floor(-averageLength * Math.log(gen.nextDouble()));
            for (int i = 0; i < n; i++) {
                r.add(base.get(0).sample());
            }
        } else {
            for (FieldSampler fieldSampler : base) {
                r.add(fieldSampler.sample());
            }
        }
        return r;
    }
}
