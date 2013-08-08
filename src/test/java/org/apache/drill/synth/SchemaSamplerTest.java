package org.apache.drill.synth;

import com.google.common.base.Charsets;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.common.io.Resources;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class SchemaSamplerTest {
    @Test
    public void testFieldNames() throws IOException {
        SchemaSampler s = new SchemaSampler("[{\"name\":\"id\", \"class\":\"id\"}, {\"name\":\"foo\", \"class\":\"address\"}, {\"name\":\"bar\", \"class\":\"date\", \"format\":\"yy-MM-dd\"}, {\"name\":\"baz\", \"class\":\"foreign-key\", \"size\":1000, \"skew\":1}]" );
        Assert.assertEquals("[id, foo, bar, baz]", Iterables.toString(s.getFieldNames()));
        System.out.printf("%s\n", Iterables.toString(s.sample()));
        System.out.printf("%s\n", Iterables.toString(s.sample()));
        System.out.printf("%s\n", Iterables.toString(s.sample()));
        System.out.printf("%s\n", Iterables.toString(s.sample()));
        System.out.printf("%s\n", Iterables.toString(s.sample()));
    }

    @Test
    public void testInt() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema1.txt"), Charsets.UTF_8).read());
        Multiset<String> counts = HashMultiset.create();
        for (int i = 0; i < 10000; i++) {
            counts.add(s.sample().get(1));
        }
        for (int i = 10; i < 99; i++) {
            Assert.assertTrue(counts.elementSet().contains(i + ""));
        }
        Assert.assertEquals(99 - 10, counts.elementSet().size());
    }
}
