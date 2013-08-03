package org.apache.drill.synth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.apache.mahout.math.random.Sampler;

import java.io.IOException;
import java.util.List;

/**
 * Samples from a specified schema to generate reasonably interesting data.
 */
public class SchemaSampler implements Sampler<List<String>> {

    private final List<FieldSampler> schema;

    public SchemaSampler(String schemaDefinition) throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        schema = mapper.readValue(schemaDefinition, new TypeReference<List<FieldSampler>>() {
        });

    }

    public List<String> getFieldNames() {
        return Lists.transform(schema, new Function<FieldSampler, String>() {
            @Override
            public String apply(org.apache.drill.synth.FieldSampler input) {
                return input.getName();
            }
        });
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
