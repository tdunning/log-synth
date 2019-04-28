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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mapr.synth.FancyTimeFormatter;
import org.apache.mahout.math.jet.random.Gamma;

import java.text.ParseException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The changer sampler emulates data evolution over time.  The idea is that you give a schema for the
 * base record.  Fields in the base record will be changed at random and the value of the record will
 * be recorded in a list. In addition to the fields in the record, there will be a list of change flags,
 * one per field that are set to 1 when a field changed and left as 0 otherwise. The time of each change
 * is also recorded.
 * <p>
 * The final result is the list of all record states and change flags.
 */
class Changer extends FieldSampler {
    private final Pattern ratePattern = Pattern.compile("([0-9.e\\-]+)(?:/([smhdw]))?");
    private final Pattern timePattern = Pattern.compile("([0-9.e\\-]+)([smhdw])?");

    public static abstract class MilliConverter {
        public abstract double toMillis(double x);

        @SuppressWarnings("WeakerAccess")
        public static double toMillis(String unit, double x) {
            if (unit == null) {
                unit = "s";
            }
            MilliConverter converter = Changer.unitMap.get(unit);
            if (converter == null) {
                converter = unitMap.get("s");
            }
            return converter.toMillis(x);
        }
    }

    private static final Map<String, ? extends MilliConverter> unitMap = ImmutableMap.of(
            "s", new MilliConverter() {
                @Override
                public double toMillis(double x) {
                    return TimeUnit.SECONDS.toMillis(1) * x;
                }
            },
            "m", new MilliConverter() {
                @Override
                public double toMillis(double x) {
                    return TimeUnit.MINUTES.toMillis(1) * x;
                }
            },
            "h", new MilliConverter() {
                @Override
                public double toMillis(double x) {
                    return TimeUnit.HOURS.toMillis(1) * x;
                }
            },
            "d", new MilliConverter() {
                @Override
                public double toMillis(double x) {
                    return TimeUnit.DAYS.toMillis(1) * x;
                }
            },
            "w", new MilliConverter() {
                @Override
                public double toMillis(double x) {
                    return 7 * TimeUnit.DAYS.toMillis(1) * x;
                }
            }
    );

    private List<FieldSampler> fields;
    private List<String> fieldNames;
    private String prefix = "change-";

    private JsonNodeFactory nodeFactory = JsonNodeFactory.withExactBigDecimals(false);

    private Random gen = new Random();
    private double end = System.currentTimeMillis();
    private double start = System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(100, TimeUnit.DAYS);

    // these are used to sample which field to change
    private Gamma x, y;

    private double meanInterval = 1000;  // interval - offset will have this mean
    private double minInterval = 0;      // no interval can be less than this
    private FancyTimeFormatter df = new FancyTimeFormatter("yyyy-MM-dd");

    public Changer(@JsonProperty("values") List<FieldSampler> fields) {
        this.fields = fields;
        fieldNames = Lists.newArrayList();
        for (FieldSampler field : fields) {
            fieldNames.add(field.getName());
        }
        x = new Gamma(1, 1, gen);
        y = new Gamma(3, 1, gen);
    }

    @SuppressWarnings("unused")
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setSkew(double skew) {
        if (skew < 0) {
            x = new Gamma(skew, 1, gen);
            y = new Gamma(1, 1, gen);
        } else {
            x = new Gamma(1, 1, gen);
            y = new Gamma(skew, 1, gen);
        }
    }

    /**
     * Determines the rate at which simulated events arrive.  This rate can be a number in which case
     * it is interpreted as a number of events per second.  The rate can also be a string like 5/m
     * which means 5 events per minute.  The supported units are seconds (s), minutes (m), hours (h),
     * and days (d).
     *
     * @param rate   The rate at which events arrive.
     */
    @SuppressWarnings("UnusedDeclaration")
    public void setRate(String rate) {
        Matcher m = ratePattern.matcher(rate);
        if (m.matches()) {
            // group(1) is the number, group(2) is either empty or a unit abbreviation letter
            this.meanInterval = MilliConverter.toMillis(m.group(2), 1) / Double.parseDouble(m.group(1));
        } else {
            throw new IllegalArgumentException(String.format("Invalid rate argument: %s", rate));
        }
    }

    /**
     * Sets a lower bound on the time between events.  This bound is enforced by generating events
     * with an exponential distribution and then adding this offset.  The offset is specified in
     * seconds.
     *
     * @param offset The minimum separation between events
     */
    @SuppressWarnings("UnusedDeclaration")
    public void setOffset(String offset) {
        Matcher m = timePattern.matcher(offset);
        if (m.matches()) {
            // group(1) is the number, group(2) is either empty (default to s) or d or some such.
            this.minInterval = MilliConverter.toMillis(m.group(2), Double.parseDouble(m.group(1)));
        } else {
            throw new IllegalArgumentException(String.format("Invalid time interval argument: %s", offset));
        }
    }

    /**
     * Sets the format to be used in outputing event times.  Standard Java date formatting rules apply.  The
     * default format is yyyy-MM-dd.  Another popular option is "yyyy-MM-dd HH:mm:ss.SS X".
     *
     * As a special treat, "s" can be used for seconds since epoch and "Q" can be used for milliseconds since
     * the epoch.
     *
     * @param format The preferred data format.
     */
    @SuppressWarnings("unused")
    public void setFormat(String format) {
        df = new FancyTimeFormatter(format);
    }

    /**
     * Sets the starting time for events. This will be exactly the time of the first event. Note that
     * the format for the starting time will be the default format unless the format argument precedes
     * this attribute.
     *
     * @param start The start time for the sequence
     */
    @SuppressWarnings("UnusedDeclaration")
    public void setStart(String start) throws ParseException {
        this.start = df.parse(start).getTime();
    }

    /**
     * Sets the ending time for events. This will be after the time of any event we generate.
     *
     * @param end   The upper bound for event time
     */
    @SuppressWarnings("UnusedDeclaration")
    public void setEnd(String end) throws ParseException {
        this.end = df.parse(end).getTime();
    }

    @Override
    public JsonNode sample() {
        ArrayNode history = new ArrayNode(nodeFactory);

        Map<String, JsonNode> current = Maps.newLinkedHashMap();
        Map<String, JsonNode> changes = Maps.newLinkedHashMap();
        for (int i = 0; i < fieldNames.size(); i++) {
            current.put(fieldNames.get(i), fields.get(i).sample());
            changes.put(fieldNames.get(i), IntNode.valueOf(0));
        }

        double t = start - meanInterval * Math.log(1 - gen.nextDouble());
        while (t < end) {
            Date now = new Date((long) t);

            int change = pickField();
            JsonNode newValue = fields.get(change).sample();

            if (fields.get(change).isFlat()) {
                Iterator<String> fx = newValue.fieldNames();
                while (fx.hasNext()) {
                    String fieldName = fx.next();
                    current.put(fieldName, newValue.get(fieldName));
                    changes.put(fieldName, new IntNode(1));
                }
            } else {
                current.put(fieldNames.get(change), newValue);
                changes.put(fieldNames.get(change), new IntNode(1));
            }

            history.add(asJson(df.format(now), current, changes));

            if (fields.get(change).isFlat()) {
                Iterator<String> fx = newValue.fieldNames();
                while (fx.hasNext()) {
                    String fieldName = fx.next();
                    current.put(prefix + fieldName, new IntNode(0));
                }
            } else {
                changes.put(fieldNames.get(change), IntNode.valueOf(0));
            }

            t += minInterval - meanInterval * Math.log(1 - gen.nextDouble());
        }
        return history;
    }

    private ObjectNode asJson(String now, Map<String, JsonNode> current, Map<String, JsonNode> changes) {
        ObjectNode r = new ObjectNode(nodeFactory);
        r.put("time", now);
        ObjectNode r1 = r.putObject("values");
        ObjectNode r2 = r.putObject("changes");
        for (String key : current.keySet()) {
            r1.set(key, current.get(key));
        }
        for (String key : changes.keySet()) {
            r2.set(key, changes.get(key));
        }

        return r;
    }

    private int pickField() {
        double xValue = x.nextDouble();
        double yValue = y.nextDouble();
        double beta = xValue / (xValue + yValue);
        return (int) Math.floor(beta * fieldNames.size());
    }
}
