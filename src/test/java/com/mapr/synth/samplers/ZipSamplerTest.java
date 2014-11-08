package com.mapr.synth.samplers;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
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

        double latitude = 0;
        double longitude = 0;
        for (int i = 0; i < N; i++) {
            v = s.sample();
            double x = v.get("z").get("longitude").asDouble();
            longitude += x;

            double y = v.get("z").get("latitude").asDouble();
            latitude += y;
        }
        longitude = longitude / N;
        latitude = latitude / N;

        // these expected values are the true means of all zip code locations
        assertEquals(-90.88465, longitude , 2);
        assertEquals(38.47346, latitude, 2);
    }
}