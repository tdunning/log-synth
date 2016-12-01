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
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.mapr.synth.UnitParser;

import java.util.Collections;
import java.util.List;
import java.util.Random;
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
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final JsonNodeFactory nodeFactory = JsonNodeFactory.withExactBigDecimals(false);

    private double meanInterval = 0;                                // in seconds/car
    private double averageSpeed;                                    // in m/s
    private double sdSpeed;                                         // speed variation

    private final List<DistanceEvent> events = Lists.newArrayList();      // all sensors and slowdowns go here

    private final Random rand = new Random();

    private double currentTime;

    @SuppressWarnings("UnusedDeclaration")
    public void setArrival(String rate) {
        this.meanInterval = 1.0 / UnitParser.parseRate(rate);
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setSpeed(String speed) {
        this.averageSpeed = UnitParser.parseSpeed(speed);
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setVariance(String speed) {
        this.sdSpeed = UnitParser.parseSpeed(speed);
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setSensors(JsonNode sensors) {
        double sensorUnit = UnitParser.parseDistanceUnit(sensors.get("unit").asText());

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
        Collections.sort(events);
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setSlowdown(ArrayNode slowdowns) throws JsonProcessingException {
        for (JsonNode slowdown : slowdowns) {
            SlowDown jam = mapper.treeToValue(slowdown, SlowDown.class);
            events.add(jam);
            events.add(jam.releaseEvent());
        }
        Collections.sort(events);
    }

    @Override
    public JsonNode sample() {
        currentTime += -Math.log(rand.nextDouble()) * meanInterval;

        double nominalSpeed = averageSpeed + rand.nextGaussian() * sdSpeed;
        double speed = nominalSpeed;

        ArrayNode sensorMeasurements = nodeFactory.arrayNode();
        double t = currentTime;
        double location = 0;
        for (DistanceEvent event : events) {
            t += (event.getLocation() - location) / speed;
            location = event.getLocation();
            speed = event.adjustSpeed(t, speed, nominalSpeed);
            TrafficSensorEvent r = event.getMeasurement(t, speed);
            if (r != null) {
                JsonNode measurementAsJson = mapper.valueToTree(r);
                sensorMeasurements.add(measurementAsJson);
            }
        }
        return sensorMeasurements;
    }

    public static class TrafficSensorEvent {
        private final String id;
        private final double time;
        private final double speed;

        public TrafficSensorEvent(String id, double time, double speed) {
            this.id = id;
            this.time = time;
            this.speed = speed;
        }

        @SuppressWarnings("UnusedDeclaration")
        public String getId() {
            return id;
        }

        @SuppressWarnings("UnusedDeclaration")
        public double getSpeed() {
            return speed;
        }

        @SuppressWarnings("UnusedDeclaration")
        public double getTime() {
            return time;
        }
    }

    public abstract static class DistanceEvent implements Comparable<DistanceEvent> {
        abstract double getLocation();

        public double adjustSpeed(double time, double speed, double originalSpeed) {
            return speed;
        }

        public TrafficSensorEvent getMeasurement(double time, double speed) {
            return null;
        }

        @Override
        public int compareTo(DistanceEvent other) {
            return Double.compare(getLocation(), other.getLocation());
        }
    }

    private static class Sensor extends DistanceEvent {
        final String id;
        final double location;

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

    public static class SlowDown extends DistanceEvent {
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
        public double adjustSpeed(double time, double speed, double originalSpeed) {
            if (time >= startTime && time < endTime) {
                return this.speed;
            } else {
                return speed;
            }
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setSpeed(String speed) {
            this.speed = UnitParser.parseSpeed(speed);
        }

        final Pattern hyphenated = Pattern.compile("(.*)\\s*-\\s*(.*)");

        @SuppressWarnings("UnusedDeclaration")
        public void setLocation(String location) {
            parsePair(location, UnitParser::parseDistance,
                    (Double start, Double end) -> {
                        this.startPoint = start;
                        this.endPoint = end;
                    });
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setTime(String time) {
            parsePair(time, UnitParser::parseTime,
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


        @SuppressWarnings("UnusedDeclaration")
        public double getSpeed() {
            return speed;
        }

        public DistanceEvent releaseEvent() {
            return new Speedup(endPoint);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SlowDown slowDown = (SlowDown) o;
            return Objects.equal(speed, slowDown.speed) &&
                    Objects.equal(startPoint, slowDown.startPoint) &&
                    Objects.equal(endPoint, slowDown.endPoint) &&
                    Objects.equal(startTime, slowDown.startTime) &&
                    Objects.equal(endTime, slowDown.endTime);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(speed, startPoint, endPoint, startTime, endTime);
        }
    }

    public static class Speedup extends  DistanceEvent {
        private final double location;

        public Speedup(double location) {
            this.location = location;
        }

        @Override
        double getLocation() {
            return location;
        }

        @Override
        public double adjustSpeed(double time, double speed, double originalSpeed) {
            return originalSpeed;
        }
    }
}
