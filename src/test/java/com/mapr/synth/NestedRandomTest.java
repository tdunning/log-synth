package com.mapr.synth;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import org.junit.Test;

import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class NestedRandomTest {

    private static final int ITERATIONS = 20000;

    @Test
    public void testDefaultSeed() {
        NestedRandom r0 = new NestedRandom();
        NestedRandom r1 = new NestedRandom(0);
        for (int i = 0; i < ITERATIONS; i++) {
            assertEquals(r0.get(i).random().nextInt(), r1.get(i).random().nextInt());
        }
    }

    @Test
    public void testUniqueAndRepeatable() {
        Multiset<Integer> samples = HashMultiset.create();

        for (int seed = 0; seed < 20; seed++) {
            NestedRandom origin = new NestedRandom(seed);
            addSamples(samples, origin.get("a"));
            addSamples(samples, origin.get("b"));
            addSamples(samples, origin.get("c"));

            addSamples(samples, origin.get("a"));
            addSamples(samples, origin.get("b"));
            addSamples(samples, origin.get("c"));

        }

        Multiset<Integer> counts = HashMultiset.create();
        for (Integer x : samples.elementSet()) {
            counts.add(samples.count(x));
        }
        Set<Integer> observedCounts = counts.elementSet();
        assertEquals(2, observedCounts.size());
        assertTrue(observedCounts.contains(2));
        assertTrue(observedCounts.contains(4));
        assertTrue(counts.count(4) < 1000);
        assertTrue(counts.count(2) > 2e6);
    }

    private void addSamples(Multiset<Integer> samples, NestedRandom twig) {
        int i = 0;
        int sample = 0;
        Random r = null;
        for (NestedRandom leaf : twig) {
            assertEquals(twig.get(i).random().nextDouble(), leaf.random().nextDouble(), 0);
            r = leaf.random();
            sample = r.nextInt();
            samples.add(sample);

            samples.add(leaf.get("q").random().nextInt());

            if (++i > ITERATIONS) {
                break;
            }
        }
        assertNotNull(r);
        Random rx = twig.get(ITERATIONS).random();
        assertEquals(sample, rx.nextInt());
        assertEquals(r.nextInt(), rx.nextInt());
    }
}