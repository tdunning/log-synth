package org.apache.drill.synth.fieldsampler;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.apache.drill.synth.FieldSampler;
import org.apache.mahout.math.random.Multinomial;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

/**
 * Sample from a space of goofy but somewhat plausible street names.
 *
 * Tip of the hat to http://www.jimwegryn.com/Names/StreetNameGenerator.htm
 */
public class StreetNameSampler extends FieldSampler {
    List<Multinomial<String>> sampler = ImmutableList.of(
            new Multinomial<String>(), new Multinomial<String>(), new Multinomial<String>()
    );

    public StreetNameSampler() {
        Splitter onTabs = Splitter.on("\t");
        try {
            for (String line : Resources.readLines(Resources.getResource("street-name-seeds"), Charsets.UTF_8)) {
                if (!line.startsWith("#")) {
                    Iterator<Multinomial<String>> i = sampler.iterator();
                    for (String name : onTabs.split(line)) {
                        i.next().add(name, 1);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Couldn't read built-in resource", e);
        }
    }

    @Override
    public String sample() {
        return sampler.get(0).sample() + " " + sampler.get(1).sample() + " " + sampler.get(2).sample();
    }
}
