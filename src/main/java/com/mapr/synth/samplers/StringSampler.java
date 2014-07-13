package com.mapr.synth.samplers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.io.Resources;
import org.apache.mahout.math.random.Multinomial;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sample from a multinomial of strings.
 *
 * Tip of the hat to http://www.jimwegryn.com/Names/StreetNameGenerator.htm
 */
public class StringSampler extends FieldSampler {
    private AtomicReference<Multinomial<String>> distribution = new AtomicReference<>();

    public StringSampler() {
    }

    protected void readDistribution(String resourceName) {
        try {
            if (distribution.compareAndSet(null, new Multinomial<String>())) {
                Splitter onTab = Splitter.on("\t").trimResults();
                for (String line : Resources.readLines(Resources.getResource(resourceName), Charsets.UTF_8)) {
                    if (!line.startsWith("#")) {
                        Iterator<String> parts = onTab.split(line).iterator();
                        String name = translate(parts.next());
                        double weight = Double.parseDouble(parts.next());
                        distribution.get().add(name, weight);
                    }
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Couldn't read built-in resource file", e);
        }
    }

    public void setDist(Map<String, ?> dist) {
        Preconditions.checkArgument(dist.size() > 0);
        distribution.compareAndSet(null, new Multinomial<String>());
        for (String key : dist.keySet()) {
            distribution.get().add(key, Double.parseDouble(dist.get(key).toString()));
        }
    }


    protected String translate(String s) {
        return s;
    }

    @Override
    public JsonNode sample() {
        return new TextNode(distribution.get().sample());
    }
}
