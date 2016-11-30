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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Model cars driving down a one dimensional road. Cars enter the road at Poisson distributed
 * times and then they drive down the road crossing sensors and emitting
 * pings as they cross each sensor.
 * <p>
 * Parameters include the arrival rate, how long the road is, the distribution
 * of speeds, number of lanes and sensor locations. You can also schedule slow-downs
 * for parts of the road at certain times.
 */
public class CarOnRoadSampler extends FieldSampler {
    private static ObjectMapper mapper = new ObjectMapper();

    private final Pattern ratePattern = Pattern.compile("([0-9.e\\-]+)\\s*(/(([smhd])|sec|second|m|min|minute|h|hour|d|day))?");
    private final Map<String, Double> rateMap =
            ImmutableMap.<String, Double>builder()
                    .put("/s", 1.0 / TimeUnit.SECONDS.toSeconds(1L))
                    .put("/sec", 1.0 / TimeUnit.SECONDS.toSeconds(1L))
                    .put("/second", 1.0 / TimeUnit.SECONDS.toSeconds(1L))
                    .put("/m", 1.0 / TimeUnit.MINUTES.toSeconds(1L))
                    .put("/min", 1.0 / TimeUnit.MINUTES.toSeconds(1L))
                    .put("/minute", 1.0 / TimeUnit.MINUTES.toSeconds(1L))
                    .put("/h", 1.0 / TimeUnit.HOURS.toSeconds(1L))
                    .put("/hour", 1.0 / TimeUnit.HOURS.toSeconds(1L))
                    .put("/d", 1.0 / TimeUnit.DAYS.toSeconds(1L))
                    .put("/day", 1.0 / TimeUnit.DAYS.toSeconds(1L))
                    .build();

    private final Pattern timePattern = Pattern.compile("([0-9.e\\-]+)\\s*([smhd])?");
    private final Map<String, Double> timeUnitMap = ImmutableMap.of(
            "s", (double) TimeUnit.SECONDS.toSeconds(1L),
            "m", (double) TimeUnit.MINUTES.toSeconds(1L),
            "h", (double) TimeUnit.HOURS.toSeconds(1L),
            "d", (double) TimeUnit.DAYS.toSeconds(1L));

    private static final double INCH = 2.54e-2;
    private static final double FOOT = 12 * INCH;
    private static final double MILE = 5280 * FOOT;
    private static final double HOUR = 3600.0;

    private final Pattern speedPattern = Pattern.compile("([0-9.e\\-]+)\\s*((mph)|(kph)|(m/s))?");

    private final Map<String, Double> speedMap = ImmutableMap.of(
            "mph", MILE / HOUR,
            "kph", 1000 / HOUR,
            "m/s", 1.0);

    private final Pattern distancePattern = Pattern.compile("([0-9.e\\-]+)\\s*((m)|(km)|(mile))?");

    private final Map<String, Double> distanceMap = ImmutableMap.of(
            "km", 1000.0,
            "mile", MILE,
            "m", 1.0);

    private JsonNodeFactory nodeFactory = JsonNodeFactory.withExactBigDecimals(false);

    private static abstract class DistanceEvent {
        abstract double getLocation();

        double adjustSpeed(double time, double speed) {
            return speed;
        }

        TrafficSensorEvent getMeasurement(double time, double speed) {
            return null;
        }
    }

    private static class Sensor extends DistanceEvent {
        String id;
        double location;

        public Sensor(String uuid, double position) {
            id = uuid;
            location = position;
        }

        @Override
        public double getLocation() {
            return location;
        }

        @Override
        public TrafficSensorEvent getMeasurement(double time, double speed) {
            return new TrafficSensorEvent(id, time, speed);
        }
    }

    class SlowDown extends DistanceEvent {
        double speed;
        double startPoint;
        double endPoint;
        double startTime;
        double endTime;

        public SlowDown() {
        }

        @Override
        double getLocation() {
            return startPoint;
        }

        @Override
        double adjustSpeed(double time, double speed) {
            if (time >= startTime && time < endTime) {
                return this.speed;
            } else {
                return speed;
            }
        }

        public void setSpeed(String speed) {
            this.speed = parseSpeed(speed);
        }

        final Pattern hyphenated = Pattern.compile("(.*)\\s*-\\s*(.*)");

        public void setLocation(String location) {
            parsePair(location, CarOnRoadSampler.this::parseDistance,
                    (Double start, Double end) -> {
                        this.startPoint = start;
                        this.endPoint = end;
                    });
        }

        public void setTime(String time) {
            parsePair(time, CarOnRoadSampler.this::parseTime,
                    (Double start, Double end) -> {
                        this.startTime = start;
                        this.endTime = end;
                    });
        }

        private void parsePair(String location, Function<String, Double> parser, BiConsumer<Double, Double> setter) {
            Matcher m = hyphenated.matcher(location);
            if (m.matches()) {
                setter.accept(parser.apply(m.group(1)), parser.apply(m.group(2)));
            } else {
                throw new IllegalArgumentException(String.format("Bad location: %s", location));
            }
        }
    }

    double meanInterval = 0;                                // in seconds/car
    double averageSpeed;                                    // in m/s
    double sdSpeed;                                         // speed variation

    List<DistanceEvent> events = Lists.newArrayList();      // all sensors and slowdowns go here

    double sensorUnit;                                      // what is the sensor distance unit

    Random rand = new Random();

    double currentTime;

    @SuppressWarnings("UnusedDeclaration")
    public void setArrival(String rate) {
        this.meanInterval = 1.0 / parseRate(rate);
    }

    public double parseSpeed(String speed) {
        return unitParse(speed, speedPattern, speedMap, "Invalid speed argument: %s");
    }

    public double parseRate(String rate) {
        return unitParse(rate, ratePattern, rateMap, "Invalid rate argument: %s");
    }

    public double parseDistance(String distance) {
        return unitParse(distance, distancePattern, distanceMap, "Invalid location argument: %s");
    }

    private double parseTime(String s) {
        return unitParse(s, timePattern, timeUnitMap, "Bad time expression: %s");
    }

    private double unitParse(String value, Pattern pattern, Map<String, Double> translation, String errorFormat) {
        Matcher m = pattern.matcher(value);
        if (m.matches()) {
            // group(1) is the number, group(2) is either empty (default to /s) or /d or some such.
            double unit = (m.groupCount() > 1) ? translation.get(m.group(2)) : 1.0;
            double raw = Double.parseDouble(m.group(1));
            return raw * unit;
        } else {
            throw new IllegalArgumentException(String.format(errorFormat, value));
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setSpeed(String speed) {
        this.averageSpeed = parseSpeed(speed);
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setVariance(String speed) {
        this.sdSpeed = parseSpeed(speed);
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setSensors(JsonNode sensors) {
        String unit = sensors.get("unit").asText();
        double sensorUnit = distanceMap.containsKey(unit) ? distanceMap.get(unit) : 1;

        JsonNode positions = sensors.get("locations");
        if (positions == null) {
            positions = sensors.get("positions");
        }
        if (positions == null) {
            throw new IllegalArgumentException("Needed \"locations\" parameter for sensors definition");
        }
        for (JsonNode position : positions) {
            String id = String.format("s-%04x-%02x-%06x", rand.nextInt(), rand.nextInt(), rand.nextInt());
            events.add(new Sensor(id, position.asDouble() * sensorUnit));
        }
        Collections.sort(events, (e1, e2) -> Double.compare(e1.getLocation(), e2.getLocation()));
    }


    public void setSlowdown(ArrayNode slowdowns) throws JsonProcessingException {
        for (JsonNode slowdown : slowdowns) {
            events.add(mapper.treeToValue(slowdown, SlowDown.class));
        }
        Collections.sort(events, (e1, e2) -> Double.compare(e1.getLocation(), e2.getLocation()));
    }

    private static class TrafficSensorEvent {
        private String id;
        private double time;
        private double speed;

        public TrafficSensorEvent(String id, double time, double speed) {
            this.id = id;
            this.time = time;
            this.speed = speed;
        }

        public String getId() {
            return id;
        }

        public double getSpeed() {
            return speed;
        }

        public double getTime() {
            return time;
        }
    }

    @Override
    public JsonNode sample() {
        currentTime += -Math.log(rand.nextDouble()) * meanInterval;

        double speed = averageSpeed + rand.nextGaussian() * sdSpeed;

        ArrayNode sensorMeasurments = nodeFactory.arrayNode();
        double start = currentTime;
        for (DistanceEvent event : events) {
            double t = start + event.getLocation() / speed;
            speed = event.adjustSpeed(t, speed);
            TrafficSensorEvent r = event.getMeasurement(t, speed);
            if (r != null) {
                JsonNode measurementAsJson = mapper.valueToTree(r);
                sensorMeasurments.add(measurementAsJson);
            }
        }
        return sensorMeasurments;
    }
}
