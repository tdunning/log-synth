package org.apache.drill.synth;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.mahout.math.random.Sampler;

@JsonTypeInfo(use=JsonTypeInfo.Id.NAME, include=JsonTypeInfo.As.PROPERTY, property="class")
@JsonSubTypes({
        @JsonSubTypes.Type(value=AddressSampler.class, name="address"),
        @JsonSubTypes.Type(value=DateSampler.class, name="date"),
        @JsonSubTypes.Type(value=ForeignKeySampler.class, name="foreign-key"),
        @JsonSubTypes.Type(value=IdSampler.class, name="id"),
        @JsonSubTypes.Type(value=IdSampler.class, name="int"),
        @JsonSubTypes.Type(value=NameSampler.class, name="name"),
        @JsonSubTypes.Type(value=StreetNameSampler.class, name="street-name")
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
