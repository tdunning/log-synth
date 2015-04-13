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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.NativeObject;

public class ScriptableNode extends ScriptableObject {
    private JsonNode embed;

    public ScriptableNode(JsonNode embed) {
        this.embed = embed;
    }

    @Override
    public String getClassName() {
        return embed.getClass().getName();
    }

    @Override
    public Object get(String s, Scriptable scriptable) {
        JsonNode r = embed.get(s);
        return embedIfNeeded(r);
    }

    private Object embedIfNeeded(JsonNode r) {
        if (r.isArray() || r.isObject() || r.isContainerNode() || r.isBigDecimal() || r.isBigInteger()) {
            return new ScriptableNode(r);
        } else if (r.isDouble()) {
            return r.asDouble();
        } else if (r.isInt()) {
            return r.asInt();
        } else if (r.isTextual()) {
            return r.asText();
        } else {
            throw new IllegalArgumentException(String.format("Can't understand how to handle result of type %s", r.getClass()));
        }
    }

    @Override
    public Object get(int i, Scriptable scriptable) {
        return embedIfNeeded(embed.get(i));
    }

    @Override
    public boolean has(String s, Scriptable scriptable) {
        return embed.has(s);
    }

    @Override
    public boolean has(int i, Scriptable scriptable) {
        return embed.has(i);
    }

    @Override
    public void put(String s, Scriptable scriptable, Object o) {
        if (embed.isObject()) {
            if (o instanceof ScriptableNode) {
                JsonNode v = ((ScriptableNode) o).embed;
                ((ObjectNode) embed).putObject(s).setAll((ObjectNode) v);
            } else {
                if (o instanceof Short) {
                    ((ObjectNode) embed).put(s, (Short) o);
                } else if (o instanceof Integer) {
                    ((ObjectNode) embed).put(s, (Integer) o);
                } else if (o instanceof Long) {
                    ((ObjectNode) embed).put(s, (Long) o);
                } else if (o instanceof Double) {
                    ((ObjectNode) embed).put(s, (Double) o);
                } else if (o instanceof Float) {
                    ((ObjectNode) embed).put(s, (Float) o);
                } else if (o instanceof String) {
                    ((ObjectNode) embed).put(s, (String) o);
                } else if (o instanceof Boolean) {
                    ((ObjectNode) embed).put(s, (Boolean) o);
                } else {
                    throw new UnsupportedOperationException("heh?");
                }
            }
        } else {
            throw new IllegalArgumentException(String.format("Can't put with type %s", embed.getClass()));
        }
    }

    @Override
    public void put(int i, Scriptable scriptable, Object o) {
        throw new UnsupportedOperationException("Default operation");
    }

    @Override
    public void delete(String s) {
        throw new UnsupportedOperationException("Default operation");
    }

    @Override
    public void delete(int i) {
        throw new UnsupportedOperationException("Default operation");
    }

    @Override
    public Scriptable getPrototype() {
        throw new UnsupportedOperationException("Default operation");
    }

    @Override
    public void setPrototype(Scriptable scriptable) {
        throw new UnsupportedOperationException("Default operation");
    }

    @Override
    public Scriptable getParentScope() {
        throw new UnsupportedOperationException("Default operation");
    }

    @Override
    public void setParentScope(Scriptable scriptable) {
        throw new UnsupportedOperationException("Default operation");
    }

    @Override
    public String toString() {
        return "ScriptableNode{" +
                "embed=" + embed +
                '}';
    }

    public static JsonNode fromRhino(Object x) {
        if (x instanceof Short) {
            return new ShortNode((Short) x);
        } else if (x instanceof Integer) {
            return new IntNode((Integer) x);
        } else if (x instanceof Long) {
            return new LongNode((Long) x);
        } else if (x instanceof Double) {
            return new DoubleNode((Double) x);
        } else if (x instanceof Float) {
            return new FloatNode((Float) x);
        } else if (x instanceof String) {
            return new TextNode((String) x);
        } else if (x instanceof Boolean) {
            return BooleanNode.valueOf((Boolean) x);
        } else if (x instanceof NativeObject) {
            JsonNodeFactory nodeFactory = JsonNodeFactory.withExactBigDecimals(false);
            ObjectNode r = new ObjectNode(nodeFactory);
            NativeObject x1 = (NativeObject) x;
            for (Object key : x1.keySet()) {
                r.put((String) key, fromRhino(x1.get((String) key, x1)));
            }
            return r;
        }   else {
            throw new UnsupportedOperationException("heh?");
        }
    }
}
