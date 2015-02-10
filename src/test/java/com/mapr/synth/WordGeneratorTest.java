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

package com.mapr.synth;

import com.mapr.synth.distributions.WordGenerator;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WordGeneratorTest {
    @Test
    public void checkRealWords() {
        WordGenerator words = new WordGenerator("word-frequency-seed", "other-words");
        for (int i = 0; i < 20000; i++) {
            assertFalse(words.getString(i).matches("-[0-9]+"));
        }

        for (int i = 0; i < 1000; i++) {
            String w = words.getString(i + 200000);
            assertTrue(w.matches(".*-[0-9]+"));
        }
    }

}
