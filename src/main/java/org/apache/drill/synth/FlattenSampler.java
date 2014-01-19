package org.apache.drill.synth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Delegate to another sampler which generates a list and flatten that list as a character separated string.
 */
public class FlattenSampler extends FieldSampler {
    private FieldSampler delegate;
    private String separator = ",";

    @JsonCreator
    public FlattenSampler(@JsonProperty("value") FieldSampler delegate) {
        this.delegate = delegate;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setSeparator(String separator) {
        this.separator = separator;
    }

    @Override
    public JsonNode sample() {
        StringBuilder r = new StringBuilder();
        JsonNode value = delegate.sample();

        String separator = "";
        for (JsonNode component : value) {
            r.append(separator);
            r.append(component.toString());
            separator = this.separator;
        }
        return new TextNode(r.toString());
    }
}
