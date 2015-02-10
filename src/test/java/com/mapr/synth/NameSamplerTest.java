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

import com.google.common.collect.Lists;
import com.mapr.synth.samplers.NameSampler;
import org.apache.mahout.common.RandomUtils;
import org.junit.Test;

import java.util.List;

import static junit.framework.Assert.assertEquals;

public class NameSamplerTest {
    @Test
    public void testName() {
        RandomUtils.useTestSeed();
        NameSampler ns = new NameSampler(NameSampler.Type.FIRST);

        List<String> expected = Lists.newArrayList("Debra", "Eric", "Kent", "Robert", "Kim", "Susan", "Michelle", "Ethan", "Clinton", "Paul", "Curt", "Courtney", "Marcella", "Michael", "Rita", "Robert", "Antonio", "Ronald", "Aurelio", "Marcus");
        for (String s : expected) {
            assertEquals("regression 1", s, ns.sample().asText());
        }

        ns.setType("LAST");
        expected = Lists.newArrayList("Rose", "Vargas", "Ramirez", "Stephens", "Zarate", "Shields", "Acklin", "Tynan", "Valencia", "Meyer", "Velasco", "Medina", "Dees", "Harris", "Patterson", "Depriest", "Debose", "Landry", "Jackson", "Dixon");
        for (String s : expected) {
            assertEquals("regression 2", s, ns.sample().asText());
        }

        ns.setType("LAST_FIRST");
        expected = Lists.newArrayList(   "Wright, Samuel", "Campbell, Veronica", "Garrett, Carol", "Jones, Stanley", "Cook, Richard", "Miller, John", "Mcpeak, Leona", "Landin, Frank", "Levy, Bryan", "Gardner, Judy", "Davis, Sylvia", "Taylor, Randy", "George, Jose", "Pye, William", "Santiago, Amy", "Goodrich, Leslie", "Fenske, Amy", "Zendejas, Cynthia", "Garcia, Clifford", "Hartley, Melvin");
        for (String s : expected) {
            assertEquals("regression 3", s, ns.sample().asText());
        }

        ns.setType("FIRST_LAST");
        expected = Lists.newArrayList("Margie Big", "Kimberly Mendez", "Vanetta Montford", "Lawanda Lopez", "Doris Hurtado", "Melissa Bennett", "Virginia Ferguson", "Phyllis Browning", "Tracy Fischer", "Marilyn Millard", "Mary Berg", "Jeffrey Murray", "Stuart Loredo", "Cheryl Marks", "John Casale", "Patricia Reyes", "Claire Mims", "Benjamin Bacon", "Mary Vallo", "James Porter");
        for (String s : expected) {
            assertEquals("regression 4", s, ns.sample().asText());
        }
    }
}
