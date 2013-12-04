package org.apache.drill.synth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Random;

/**
 * Create comma separated samples from another field definition.
 */
public class SequenceSampler extends FieldSampler {
    private double averageLength = 5;
    private FieldSampler base;
    private Random gen = new Random();

    @JsonCreator
    public SequenceSampler(@JsonProperty("base") FieldSampler base) {
        this.base = base;
    }

    public void setLength(double length) {
        averageLength = length;
    }

    @Override
    public String sample() {
        int n = (int) Math.floor(-averageLength * Math.log(gen.nextDouble()));
        StringBuilder r = new StringBuilder();
        String separator = "";
        for (int i = 0; i < n; i++) {
            r.append(separator);
            r.append(base.sample());
            separator = ",";
        }
        return r.toString();
    }
}
