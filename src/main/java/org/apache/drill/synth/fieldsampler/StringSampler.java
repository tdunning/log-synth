package org.apache.drill.synth.fieldsampler;

import java.util.Map;

import org.apache.drill.synth.FieldSampler;
import org.apache.mahout.math.random.Multinomial;

import com.google.common.base.Preconditions;

/**
 * Sample from a space of goofy but somewhat plausible street names.
 *
 * Tip of the hat to http://www.jimwegryn.com/Names/StreetNameGenerator.htm
 */
public class StringSampler extends FieldSampler {
    Multinomial<String> distribution = new Multinomial<String>();

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
