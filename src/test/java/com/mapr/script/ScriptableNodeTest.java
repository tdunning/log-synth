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

package com.mapr.script;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.mapr.synth.samplers.SchemaSampler;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.io.IOException;
import java.io.InputStreamReader;

public class ScriptableNodeTest {

    @Test
    public void testBasics() throws IOException {
        Context cx = Context.enter();
        Scriptable scope = cx.initStandardObjects();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema024.json"), Charsets.UTF_8).read());
        for (int i = 0; i < 100; i++) {
            JsonNode x = s.sample();
            ScriptableObject.putProperty(scope, "x", new ScriptableNode(x));
            cx.evaluateReader(scope, new InputStreamReader(Resources.getResource("post001.js").openStream(), Charsets.UTF_8), "post001.js", 1, null);
            Object result = cx.evaluateString(scope, "transform(x)", "<cmd>", 1, null);
            System.out.printf("%s\n", ScriptableNode.fromRhino(result));
        }
    }
}