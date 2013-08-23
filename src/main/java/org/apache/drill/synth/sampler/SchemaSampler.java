package org.apache.drill.synth.sampler;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.drill.synth.FieldSampler;
import org.apache.mahout.math.random.Sampler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

/**
 * Samples from a specified schema to generate reasonably interesting data.
 */
public class SchemaSampler implements Sampler<List<String>> {
    private final List<FieldSampler> schema;
    private final List<String> fields;

    public SchemaSampler(String schemaDefinition) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
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
    public List<String> sample() {
        List<String> r = Lists.newArrayList();
        for (FieldSampler s: schema) {
            r.add(s.sample());
        }
        return r;
    }
}
