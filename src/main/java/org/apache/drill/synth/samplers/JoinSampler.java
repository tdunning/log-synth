package org.apache.drill.synth.samplers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.drill.synth.samplers.FieldSampler;

/**
 * Glue together elements of a list as strings.  Should normally only be done with a list of strings.
 */
public class JoinSampler extends FieldSampler {
    private FieldSampler delegate;
    private String separator = ",";

    @JsonCreator
    public JoinSampler(@JsonProperty("value") FieldSampler delegate) {
        this.delegate = delegate;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setSeparator(String separator) {
        this.separator = separator;
    }

    @Override
    public JsonNode sample() {
        JsonNode value = delegate.sample();
        StringBuilder r = new StringBuilder();

        String separator="";
        for (JsonNode component : value) {
            r.append(separator);
            r.append(component.asText());
            separator = this.separator;
        }
        return new TextNode(r.toString());
    }
}

