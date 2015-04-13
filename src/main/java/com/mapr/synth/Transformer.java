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

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.mapr.script.ScriptableNode;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;

import java.io.File;
import java.io.IOException;

/**
 * Transforms data after generation.  This allows for complex interaction effects, particularly the modeling of
 * sales conversions without having to build a special sampler.
 */
public abstract class Transformer {
    public static Transformer create(File transform) {
        if (transform == null) {
            return new IdentityTransform();
        } else {
            return new ScriptTransform(transform);
        }
    }

    public abstract JsonNode apply(JsonNode in);

    private static class IdentityTransform extends Transformer {
        public JsonNode apply(JsonNode in) {
            return in;
        }
    }

    private static class ScriptTransform extends Transformer {

        private final Function transform;
        private final ScriptableObject scope;
        private final Context cx;

        public ScriptTransform(File definition) {
            cx = Context.enter();
            scope = cx.initStandardObjects();

            transform = (Function) scope.get("transform", scope);
            try {
                cx.evaluateReader(scope, Files.newReader(definition, Charsets.UTF_8), definition.getAbsolutePath(), 1, null);
            } catch (IOException e) {
                throw new RuntimeException("Error loading transform defintion", e);
            }
        }

        @Override
        public JsonNode apply(JsonNode in) {
            return ScriptableNode.fromRhino(transform.call(cx, scope, scope, new Object[]{new ScriptableNode(in)}));
        }
    }
}
