package org.apache.drill.synth;

import org.junit.Test;

public class LogGeneratorTest {
    @Test
    public void testSample() {
        LogGenerator gen = new LogGenerator(200);
        for (int i = 0; i < 100; i++) {
            System.out.printf("%s\n", gen.sample());
        }
    }
}
