package org.apache.drill.synth;

import com.google.common.base.Preconditions;
import org.apache.mahout.math.random.Multinomial;

import java.util.Map;

/**
 * Sample from a space of goofy but somewhat plausible street names.
 *
 * Tip of the hat to http://www.jimwegryn.com/Names/StreetNameGenerator.htm
 */
public class StringSampler extends FieldSampler {
    Multinomial<String> distribution = new Multinomial<>();

    public StringSampler() {
    }

    public void setDist(Map<String, ?> dist) {
        Preconditions.checkArgument(dist.size() > 0);
        for (String key : dist.keySet()) {
            distribution.add(key, Double.parseDouble(dist.get(key).toString()));
        }
    }

    @Override
    public String sample() {
        return distribution.sample();
    }
}
