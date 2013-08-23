package org.apache.drill.synth.sampler;

import org.junit.Test;

public class LogGeneratorTest {
    @Test
    public void testSample() {
        LogGenerator gen = new LogGenerator(20000);
        for (int i = 0; i < 1000; i++) {
            System.out.printf("%s\n", gen.sample());
        }
    }
}
