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

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.*;

/**
 * Created by tdunning on 10/29/16.
 */
public class HeaderSamplerTest {

    @Test
    public void testEncoding() throws IOException {
        HeaderSampler s = new HeaderSampler();
        s.setType("normal");
        Multiset<String> encodings = HashMultiset.create();
        for (int i = 0; i < 1000; i++) {
            encodings.add(s.encoding());
        }
        assertEquals(3, encodings.elementSet().size());
        assertEquals(333.0, encodings.count("deflate"), 90);
        assertEquals(333.0, encodings.count("gzip"), 90);
        assertEquals(333.0, encodings.count("gzip, deflate"), 90);

        s.setType("mal1");
        encodings = HashMultiset.create();
        for (int i = 0; i < 1000; i++) {
            encodings.add(s.encoding());
        }
        assertEquals(1, encodings.elementSet().size());
        assertEquals(1000, encodings.count("identity"), 90);

        s.setType("mal2");
        encodings = HashMultiset.create();
        for (int i = 0; i < 1000; i++) {
            encodings.add(s.encoding());
        }
        assertEquals(1, encodings.elementSet().size());
    }

    @Test
    public void testUserAgent() throws IOException {
        HeaderSampler s = new HeaderSampler();
        s.setType("normal");
        Multiset<String> agents = HashMultiset.create();
        for (int i = 0; i < 1000; i++) {
            agents.add(s.userAgent());
        }
        assertTrue(agents.elementSet().size() > 100);

        s.setType("ababil");
        agents = HashMultiset.create();
        for (int i = 0; i < 1000; i++) {
            agents.add(s.userAgent());
        }
        assertEquals(1, agents.elementSet().size());
    }

    @Test
    public void testLanguage() throws Exception {
        HeaderSampler s = new HeaderSampler();
        s.setType("normal");
        Multiset<String> languages = HashMultiset.create();
        for (int i = 0; i < 1000; i++) {
            languages.add(s.language());
        }
        assertEquals(10, languages.elementSet().size());

        s.setType("ababil");
        languages = HashMultiset.create();
        for (int i = 0; i < 1000; i++) {
            languages.add(s.language());
        }
        assertEquals(1, languages.elementSet().size());
    }

    @Test
    public void testSample() throws Exception {
        HeaderSampler s = new HeaderSampler();
        s.setType("normal");
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            headers.add(s.sample().asText());
        }
        System.out.printf("foo\n");
    }
}