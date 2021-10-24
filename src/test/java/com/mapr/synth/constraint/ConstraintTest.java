package com.mapr.synth.constraint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.mapr.synth.samplers.FieldSampler;
import com.mapr.synth.samplers.SchemaSampler;

public class ConstraintTest {
	
	 @Test
	    public void testDeserialization() throws IOException {
	        SchemaSampler s = SchemaSampler.fromResource("schema045.json");
	        JsonNode ss = s.sample();
	        System.out.println(ss);
	    }

}
