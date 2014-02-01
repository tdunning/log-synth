package org.apache.drill.synth;

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

    private final List<FieldSampler> schema;
    private final List<String> fields;

    public SchemaSampler(String schemaDefinition) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

        schema = mapper.readValue(schemaDefinition, new TypeReference<List<FieldSampler>>() {
        });
        fields = Lists.transform(schema, new Function<FieldSampler, String>() {
            @Override
            public String apply(org.apache.drill.synth.FieldSampler input) {
                return input.getName();
            }
        });
    }

    public SchemaSampler(File input) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        schema = mapper.readValue(input, new TypeReference<List<FieldSampler>>() {
        });
        fields = Lists.transform(schema, new Function<FieldSampler, String>() {
            @Override
            public String apply(org.apache.drill.synth.FieldSampler input) {
                return input.getName();
            }
        });
    }

    public List<String> getFieldNames() {
        return fields;
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
