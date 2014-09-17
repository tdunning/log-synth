package com.mapr.synth.samplers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Samples from a "foreign key" which is really just an integer.
 * <p/>
 * The only cleverness here is that we allow a variable amount of key skew.
 */
public class IdSampler extends FieldSampler {
  private AtomicInteger current = new AtomicInteger(0);

  public IdSampler() {
  }

  @Override
  public JsonNode sample() {
    return new IntNode(current.getAndIncrement());
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setStart(int start) {
    this.current.set(start);
  }
}
