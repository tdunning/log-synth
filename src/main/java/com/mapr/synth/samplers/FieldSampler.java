package com.mapr.synth.samplers;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.mapr.synth.*;
import org.apache.mahout.math.random.Sampler;

@JsonTypeInfo(use=JsonTypeInfo.Id.NAME, include=JsonTypeInfo.As.PROPERTY, property="class")
@JsonSubTypes({
        @JsonSubTypes.Type(value=AddressSampler.class, name="address"),
        @JsonSubTypes.Type(value=DateSampler.class, name="date"),
        @JsonSubTypes.Type(value=ArrivalSampler.class, name="event"),
        @JsonSubTypes.Type(value=ForeignKeySampler.class, name="foreign-key"),
        @JsonSubTypes.Type(value=IdSampler.class, name="id"),
        @JsonSubTypes.Type(value=IntegerSampler.class, name="int"),
        @JsonSubTypes.Type(value=NameSampler.class, name="name"),
        @JsonSubTypes.Type(value=FileSampler.class, name="lookup"),
        @JsonSubTypes.Type(value=FlattenSampler.class, name="flatten"),
        @JsonSubTypes.Type(value=JoinSampler.class, name="join"),
        @JsonSubTypes.Type(value=MapSampler.class, name="map"),
        @JsonSubTypes.Type(value=StreetNameSampler.class, name="street-name"),
        @JsonSubTypes.Type(value=StringSampler.class, name="string"),
        @JsonSubTypes.Type(value=CountrySampler.class, name="country"),
        @JsonSubTypes.Type(value=BrowserSampler.class, name="browser"),
        @JsonSubTypes.Type(value=StateSampler.class, name="state"),
        @JsonSubTypes.Type(value=LanguageSampler.class, name="language"),
        @JsonSubTypes.Type(value=OperatingSystemSampler.class, name="os"),
        @JsonSubTypes.Type(value=WordSampler.class, name="word"),

        @JsonSubTypes.Type(value=SequenceSampler.class, name="sequence"),

        @JsonSubTypes.Type(value=CommonPointOfCompromise.class, name="common-point-of-compromise")
})
public abstract class FieldSampler implements Sampler<JsonNode> {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
