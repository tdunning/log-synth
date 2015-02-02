package com.mapr.synth.samplers;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.io.Resources;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class ZipSamplerTest {

    private static final int N = 50000;

    @Test
    public void testZips() throws IOException {
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema016.json"), Charsets.UTF_8).read());

        JsonNode v = s.sample();
        // regression test given that we specify the seed
        assertEquals("65529", v.get("z").get("zip").asText());

        Multiset<String> laCounts = HashMultiset.create();

        double latitude = 0;
        double longitude = 0;
        double latitudeFuzzy = 0;
        double longitudeFuzzy = 0;
        boolean allInside1 = true;
        boolean allInside2 = true;
        for (int i = 0; i < N; i++) {
            v = s.sample();
            double x = v.get("z").get("longitude").asDouble();
            double y = v.get("z").get("latitude").asDouble();

            longitude += x;
            latitude += y;
            allInside1 &= isContinental(x, y);

            x = v.get("zContinental").get("longitude").asDouble();
            y = v.get("zContinental").get("latitude").asDouble();
            allInside2 &= isContinental(x, y);

            x = v.get("zFuzzy").get("longitude").asDouble();
            y = v.get("zFuzzy").get("latitude").asDouble();
            longitudeFuzzy += x;
            latitudeFuzzy += y;

            laCounts.add(v.get("zLosAngeles").get("zip").asText());
            assertTrue("Unexpected zip code in LA", v.get("zLosAngeles").get("zip").asText().matches("(9[0123]...)|(89...)"));
        }

        assertFalse("Expected non-continental samples", allInside1);
        assertTrue("Should not have had non-continental samples", allInside2);

        longitude = longitude / N;
        latitude = latitude / N;

        longitudeFuzzy = longitudeFuzzy / N;
        latitudeFuzzy = latitudeFuzzy / N;

        // these expected values are the true means of all zip code locations
        assertEquals(-90.88465, longitude , 2);
        assertEquals(38.47346, latitude, 2);

        assertEquals(-90.88465, longitudeFuzzy , 7);
        assertEquals(38.47346, latitudeFuzzy, 5);

        assertEquals(1365, laCounts.elementSet().size(), 50);
    }

    private boolean isContinental(double x, double y) {
        return y >= 22 && y <= 50 && x >= -130 && x <= -65;
    }
}