package com.mapr.synth.samplers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Sample kind of plausible addresses
 */
public class AddressSampler extends FieldSampler {

    private final StreetNameSampler street;
    private final ForeignKeySampler number;

    public AddressSampler() {
        street = new StreetNameSampler();
        number = new ForeignKeySampler(100000, 0.5);
    }

    @Override
    public JsonNode sample() {
        return new TextNode(number.sample().asInt() + " " + street.sample().asText());
    }
}
