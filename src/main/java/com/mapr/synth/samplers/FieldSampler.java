/*
 * Licensed to Ted Dunning under one or more contributor license
 * agreements.  See the NOTICE file that may be
 * distributed with this work for additional information
 * regarding copyright ownership.  Ted Dunning licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.mapr.synth.samplers;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.mapr.synth.OperatingSystemSampler;
import com.mapr.synth.drive.Commuter;
import org.apache.mahout.math.random.Sampler;

import java.io.IOException;
import java.util.Set;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "class")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AddressSampler.class, name = "address"),
        @JsonSubTypes.Type(value = ArrayFlattener.class, name = "array-flatten"),
        @JsonSubTypes.Type(value = ArrivalSampler.class, name = "event"),
        @JsonSubTypes.Type(value = BrowserSampler.class, name = "browser"),
        @JsonSubTypes.Type(value = BurstyEvents.class, name = "bursts"),
        @JsonSubTypes.Type(value = Changer.class, name = "changer"),
        @JsonSubTypes.Type(value = CommonPointOfCompromise.class, name = "common-point-of-compromise"),
        @JsonSubTypes.Type(value = Commuter.class, name = "commuter"),
        @JsonSubTypes.Type(value = CountrySampler.class, name = "country"),
        @JsonSubTypes.Type(value = DateSampler.class, name = "date"),
        @JsonSubTypes.Type(value = DomainSampler.class, name = "domain"),
        @JsonSubTypes.Type(value = DnsSampler.class, name = "dns"),
        @JsonSubTypes.Type(value = FileSampler.class, name = "lookup"),
        @JsonSubTypes.Type(value = FlattenSampler.class, name = "flatten"),
        @JsonSubTypes.Type(value = ForeignKeySampler.class, name = "foreign-key"),
        @JsonSubTypes.Type(value = GammaSampler.class, name = "gamma"),
        @JsonSubTypes.Type(value = HeaderSampler.class, name = "header"),
        @JsonSubTypes.Type(value = IdSampler.class, name = "id"),
        @JsonSubTypes.Type(value = IntegerSampler.class, name = "int"),
        @JsonSubTypes.Type(value = JoinSampler.class, name = "join"),
        @JsonSubTypes.Type(value = LanguageSampler.class, name = "language"),
        @JsonSubTypes.Type(value = LongTailSampler.class, name = "pitman_yor"),
        @JsonSubTypes.Type(value = MapSampler.class, name = "map"),
        @JsonSubTypes.Type(value = NameSampler.class, name = "name"),
        @JsonSubTypes.Type(value = NormalSampler.class, name = "normal"),
        @JsonSubTypes.Type(value = OperatingSystemSampler.class, name = "os"),
        @JsonSubTypes.Type(value = RandomWalkSampler.class, name = "random-walk"),
        @JsonSubTypes.Type(value = SequenceSampler.class, name = "sequence"),
        @JsonSubTypes.Type(value = SsnSampler.class, name = "ssn"),
        @JsonSubTypes.Type(value = StateSampler.class, name = "state"),
        @JsonSubTypes.Type(value = StreetNameSampler.class, name = "street-name"),
        @JsonSubTypes.Type(value = StringSampler.class, name = "string"),
        @JsonSubTypes.Type(value = UUIDSampler.class, name = "uuid"),
        @JsonSubTypes.Type(value = VectorSampler.class, name = "vector"),
        @JsonSubTypes.Type(value = VinSampler.class, name = "vin"),
        @JsonSubTypes.Type(value = WordSampler.class, name = "word"),
        @JsonSubTypes.Type(value = ZipSampler.class, name = "zip"),

})
public abstract class FieldSampler implements Sampler<JsonNode> {
    private String name;
    private boolean flattener = false;

    protected static FieldSampler newSampler(String def) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

        return mapper.readValue(def, new TypeReference<>() {
        });
    }

    protected static FieldSampler newSampler(JsonNode def) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

        return mapper.convertValue(def, new TypeReference<>() {
        });
    }

    protected static FieldSampler constant(final double v) {
        return new FieldSampler() {
            private DoubleNode sd = new DoubleNode(v);

            @Override
            public JsonNode sample() {
                return sd;
            }
        };
    }

    /**
     * Restart should back up any variables to the minimum values, but should not reseed any
     * random number generators.
     */
    public void restart() {
        // do nothing
    }

    public String getName() {
        return name;
    }

    public void setSeed(long seed) {
        // do nothing by default
    }

    void setName(String name) {
        this.name = name;
    }

    @SuppressWarnings({"WeakerAccess", "SameParameterValue"})
    public void setFlattener(boolean flattener) {
        this.flattener = flattener;
    }

    public boolean isFlat() {
        return flattener;
    }

    public void getNames(Set<String> fields) {
        fields.add(name);
    }
}
