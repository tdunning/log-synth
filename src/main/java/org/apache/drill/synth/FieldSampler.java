package org.apache.drill.synth;

import org.apache.drill.synth.fieldsampler.AddressSampler;
import org.apache.drill.synth.fieldsampler.DateSampler;
import org.apache.drill.synth.fieldsampler.ForeignKeySampler;
import org.apache.drill.synth.fieldsampler.IdSampler;
import org.apache.drill.synth.fieldsampler.IntegerSampler;
import org.apache.drill.synth.fieldsampler.NameSampler;
import org.apache.drill.synth.fieldsampler.StreetNameSampler;
import org.apache.drill.synth.fieldsampler.StringSampler;
import org.apache.mahout.math.random.Sampler;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use=JsonTypeInfo.Id.NAME, include=JsonTypeInfo.As.PROPERTY, property="class")
@JsonSubTypes({
        @JsonSubTypes.Type(value=AddressSampler.class, name="address"),
        @JsonSubTypes.Type(value=DateSampler.class, name="date"),
        @JsonSubTypes.Type(value=ForeignKeySampler.class, name="foreign-key"),
        @JsonSubTypes.Type(value=IdSampler.class, name="id"),
        @JsonSubTypes.Type(value=IntegerSampler.class, name="int"),
        @JsonSubTypes.Type(value=NameSampler.class, name="name"),
        @JsonSubTypes.Type(value=StreetNameSampler.class, name="street-name"),
        @JsonSubTypes.Type(value=StringSampler.class, name="string")
})
public abstract class FieldSampler implements Sampler<String> {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
