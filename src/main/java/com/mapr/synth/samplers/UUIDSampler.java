package com.mapr.synth.samplers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.mahout.common.RandomUtils;
import java.util.UUID;

import java.util.Random;

/**
 * Samples a random UUID.
 *
 * Thread safe (?)
 */

public class UUIDSampler extends FieldSampler {
    public UUIDSampler() {
    }

    @Override
    public JsonNode sample() {
      synchronized (this) {
        return  new TextNode(UUID.randomUUID().toString());
      }
    }

}
