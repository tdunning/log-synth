/*
 * Licensed to the Ted Dunning under one or more contributor license
 * agreements.  See the NOTICE file that may be
 * distributed with this work for additional information
 * regarding copyright ownership.  Ted Dunning licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.mapr.synth.samplers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Random;

/**
 * Samples a version 4 (random) UUID.  Note that the random bits generated are pull from the
 * standard Java random number generator and are subject to limitations because of that.
 *
 * See http://en.wikipedia.org/wiki/Universally_unique_identifier#Version_4_.28random.29
 *
 * Thread safe.
 */

public class UUIDSampler extends FieldSampler {
    private Random rand = new Random();
    public UUIDSampler() {
    }

    @SuppressWarnings("unused")
    public void setSeed(long seed) {
        rand.setSeed(seed);
    }

    @Override
    public JsonNode sample() {
        int a = rand.nextInt();
        int b = rand.nextInt(1 << 16);
        int c = 0x4000 + rand.nextInt(1 << 12);
        int d = 0x8000 + rand.nextInt(1 << 14);
        long e = rand.nextLong() & ((1L << 48) - 1);
        return new TextNode(String.format("%08x-%04x-%04x-%04x-%012x", a, b, c, d, e));
    }
}
