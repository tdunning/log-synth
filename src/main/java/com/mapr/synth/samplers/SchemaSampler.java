package com.mapr.synth.samplers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.apache.mahout.math.random.Sampler;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * Samples from a specified schema to generate reasonably interesting data.
 */
public class SchemaSampler implements Sampler<JsonNode> {
    private final JsonNodeFactory nodeFactory = JsonNodeFactory.withExactBigDecimals(false);

    private List<FieldSampler> schema;
    private List<String> fields;

    public SchemaSampler(List<FieldSampler> s) {
        init(s);
    }

    public SchemaSampler(String schemaDefinition) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

        init(mapper.<List<FieldSampler>>readValue(schemaDefinition, new TypeReference<List<FieldSampler>>() {
        }));
    }

    public SchemaSampler(File input) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        init(mapper.<List<FieldSampler>>readValue(input, new TypeReference<List<FieldSampler>>() {
        }));
    }

    public List<String> getFieldNames() {
        return fields;
    }

    private void init(List<FieldSampler> s) {
        schema = s;
        fields = Lists.transform(schema, new Function<FieldSampler, String>() {
            @Override
            public String apply(FieldSampler input) {
                return input.getName();
            }
        });
    }

    @Override
    public JsonNode sample() {
        ObjectNode r = nodeFactory.objectNode();
        Iterator<String> fx = fields.iterator();
        for (FieldSampler s : schema) {
            r.set(fx.next(), s.sample());
        }
        return r;
    }
}
