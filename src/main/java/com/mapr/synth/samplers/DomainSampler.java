/*
 * Licensed to the Ted Dunning under one or more contributor license
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.mapr.synth.Util;
import com.mapr.synth.distributions.LongTail;
import org.apache.mahout.math.random.Multinomial;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class DomainSampler extends FieldSampler {
    private JsonNodeFactory factory = JsonNodeFactory.withExactBigDecimals(false);

    private LongTail<String> domainDistribution;

    // distribution parameters for domain names
    private double alpha = 1000;
    private double discount = 0.3;

    public DomainSampler() throws IOException {
        List<String> topNames = Lists.newArrayList();
        Splitter onComma = Splitter.on(',').trimResults(CharMatcher.is('"'));
        Util.readData("f500-domains.csv", line -> {
            Iterator<String> ix = onComma.split(line).iterator();
            ix.next();
            topNames.add(ix.next());
            return null;
        });
        Multinomial<String> tld = Util.readTable(onComma, "tld.csv");
        NameSampler names = new NameSampler(NameSampler.Type.LAST);
        domainDistribution = new LongTail<String>(alpha, discount) {
            int i = 0;

            @Override
            protected String createThing() {
                if (i < topNames.size()) {
                    return topNames.get(i++);
                } else {
                    return names.sample().asText() + tld.sample();
                }
            }
        };
        restart();
    }


    public void setAlpha(double alpha) {
        this.alpha = alpha;
        domainDistribution.getBaseDistribution().setAlpha(alpha);
    }

    @SuppressWarnings("unused")
    public void setDiscount(double discount) {
        this.discount = discount;
        domainDistribution.getBaseDistribution().setDiscount(discount);
    }

    @Override
    public void setSeed(long seed) {
        domainDistribution.setSeed(seed);
    }

    @SuppressWarnings("unused")
    public void setFlat(boolean isFlat) {
        setFlattener(isFlat);
    }

    @Override
    public void getNames(Set<String> fields) {
        if (isFlat()) {
            fields.add(getFieldName("domain"));
            fields.add(getFieldName("revDomain"));
        } else {
            fields.add(getName());
        }
    }

    private String getFieldName(String name) {
        if (getName() == null) {
            return name;
        } else {
            return (getName() + "-" + name);
        }
    }

    @Override
    public JsonNode sample() {
        ObjectNode r = new ObjectNode(factory);

        String domain = domainDistribution.sample();
        r.set(getFieldName("domain"), new TextNode(domain));
        List<String> parts = Arrays.asList(domain.split("\\."));
        Collections.reverse(parts);
        String reversed = String.join(".", parts);
        r.set(getFieldName("revDomain"), new TextNode(reversed));

        return r;
    }
}
