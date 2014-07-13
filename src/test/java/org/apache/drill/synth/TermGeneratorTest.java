package org.apache.drill.synth;

import com.google.common.base.Function;
import com.google.common.collect.*;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.mahout.math.stats.LogLikelihood;
import org.junit.Test;

import java.util.List;
import java.util.Random;
import java.util.SortedSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TermGeneratorTest {

    private static final WordGenerator WORDS = new WordGenerator("word-frequency-seed", "other-words");

    @Test
    public void generateTerms() {
        TermGenerator x = new TermGenerator(WORDS, 1, 0.8);
        final Multiset<String> counts = HashMultiset.create();
        for (int i = 0; i < 10000; i++) {
            counts.add(x.sample());
        }

        assertEquals(10000, counts.size());
        assertTrue("Should have some common words", counts.elementSet().size() < 10000);
        List<Integer> k = Lists.newArrayList(Iterables.transform(counts.elementSet(), new Function<String, Integer>() {
            public Integer apply(String s) {
                return counts.count(s);
            }
        }));
//        System.out.printf("%s\n", Ordering.natural().reverse().sortedCopy(k).subList(0, 30));
//        System.out.printf("%s\n", Iterables.transform(Iterables.filter(counts.elementSet(), new Predicate<String>() {
//            public boolean apply(String s) {
//                return counts.count(s) > 100;
//            }
//        }), new Function<String, String>() {
//            public String apply(String s) {
//                return s + ":" + counts.count(s);
//            }
//        }));
        assertEquals(1, Ordering.natural().leastOf(k, 1).get(0).intValue());
        assertTrue(Ordering.natural().greatestOf(k, 1).get(0) > 300);
        assertTrue(counts.count("the") > 300);
    }

    @Test
    public void distinctVocabularies() {
        TermGenerator x1 = new TermGenerator(WORDS, 1, 0.8);
        final Multiset<String> k1 = HashMultiset.create();
        for (int i = 0; i < 50000; i++) {
            k1.add(x1.sample());
        }

        TermGenerator x2 = new TermGenerator(WORDS, 1, 0.8);
        final Multiset<String> k2 = HashMultiset.create();
        for (int i = 0; i < 50000; i++) {
            k2.add(x2.sample());
        }

        final NormalDistribution normal = new NormalDistribution();
        List<Double> scores = Ordering.natural().sortedCopy(Iterables.transform(k1.elementSet(),
                new Function<String, Double>() {
                    public Double apply(String s) {
                        return normal.cumulativeProbability(LogLikelihood.rootLogLikelihoodRatio(k1.count(s), 50000 - k1.count(s), k2.count(s), 50000 - k2.count(s)));
                    }
                }));
        int n = scores.size();
//        System.out.printf("%.5f, %.5f, %.5f, %.5f, %.5f, %.5f, %.5f", scores.get(0), scores.get((int) (0.05*n)), scores.get(n / 4), scores.get(n / 2), scores.get(3 * n / 4), scores.get((int) (0.95 * n)), scores.get(n - 1));
        int i = 0;
        for (Double score : scores) {
            if (i % 10 == 0) {
                System.out.printf("%.6f\t%.6f\n", (double) i / n, score);
            }

            i++;
        }
    }

    @Test
    public void speciesCounts() {
        final boolean transpose = false;

        // generate an example of species sampled on multiple days
        LongTail<Integer> terms = new LongTail<Integer>(0.5, 0.3) {
            int max = 0;

            @Override
            protected Integer createThing() {
                return ++max;
            }
        };

        // I picked seeds to get a good illustration ... want a reasonable number of species and surprises
        terms.setSeed(2);

        Random gen = new Random(1);
        SortedSet<Integer> vocabulary = Sets.newTreeSet();
        List<Multiset<Integer>> r = Lists.newArrayList();

        for (int i = 0; i < 2000; i++) {
            double length = Math.rint(gen.nextGaussian() * 10 + 50);
            Multiset<Integer> counts = HashMultiset.create();
            for (int j = 0; j < length; j++) {
                counts.add(terms.sample());
            }
            r.add(counts);
        }

        if (transpose) {
            for (Multiset<Integer> day : r) {
                vocabulary.addAll(day.elementSet());
            }

            System.out.printf("%d\n", vocabulary.size());
            for (Integer s : vocabulary) {
                String sep = "";
                for (Multiset<Integer> day : r) {
                    System.out.printf("%s%s", sep, day.count(s));
                    sep = "\t";
                }
                System.out.printf("\n");
            }
        } else {
            System.out.printf("%d\n", vocabulary.size());
            for (Multiset<Integer> day : r) {
                vocabulary.addAll(day.elementSet());
                String sep = "";
                System.out.printf("%s%s", sep, vocabulary.size());
                sep = "\t";
                for (Integer s : vocabulary) {
                    System.out.printf("%s%s", sep, day.count(s));
                    sep = "\t";
                }
                System.out.printf("\n");
            }

            Multiset<Integer> total = HashMultiset.create();
            for (Multiset<Integer> day : r) {
                for (Integer species : day.elementSet()) {
                    total.add(species, day.count(species));
                }
            }
            String sep = "";
            System.out.printf("%s%s", sep, total.elementSet().size());
            sep = "\t";
            for (Integer s : vocabulary) {
                System.out.printf("%s%s", sep, total.count(s));
                sep = "\t";
            }
            System.out.printf("\n");
        }
    }
}
