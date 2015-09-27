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

package com.mapr.synth.drive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mapr.synth.samplers.FieldSampler;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Random;
import java.util.TimeZone;

/**
 * Emulates a commuter who drives to work and back home and who runs errands around their home.
 * <p>
 * The model for deciding the next action is
 * <p>
 * 1) commuting to work happens mostly happens with a strong peak from 7-9, but could happen anytime
 * 2) commuting back home happens mostly from 15 to 20, with a strong peak from 17-19, but could happen any time.
 * 3) errands happen much more during the day than at night and only happen if the commuter is at home.
 * <p>
 * Once the next action is selected, the trip is planned (one-way for commuting, round-trip for errands)
 * <p>
 * The configuration of this sampler is a little bit fancy. The home location is specified as a sampler that
 * returns an object with latitude and longitude fields (typically a zip). The work location is specified as
 * one of several forms:
 * <p>
 * - as a number. This number is used as the mean distance and the work location is picked as a normal distribution
 * around the home.  This is the common case.
 * <p>
 * - as an object. The object is interpreted as a sampler that returns an object containing (at least) latitude
 * and longitude fields. Typically this would be a zip.
 * <p>
 * In addition, you need to specify the start and stop time for the simulation.
 */
public class Commuter extends FieldSampler {
    private static final double ERRAND_SIZE_KM = 20;

    private static final double WEEKEND_COMMUTE_RATE = 0.1;
    public static final double WEEKEND_ERRAND_RATE = 0.9;

    private static final double WEEKDAY_COMMUTE_RATE = 2;
    private static final double WEEKDAY_PEAK_COMMUTE_RATE = 10;
    public static final double WEEKDAY_ERRAND_RATE = 0.5;

    private static final double DAY_IN_S = 24 * 3600.0;
    private static final JsonNodeFactory FACTORY = JsonNodeFactory.withExactBigDecimals(false);

    // simulation period in seconds
    private double start;
    private double end;

    // internal mechanics
    Random rand = new Random();
    DateFormat df;
    final GregorianCalendar cal = new GregorianCalendar();

    // is the commuter at home?
    boolean atHome;

    double sampleTime = 1;
    private FieldSampler homeSampler;
    private FieldSampler workSampler;

    public Commuter() throws ParseException {
        setFormat("yyyy-MM-dd HH:mm:ss");
        start = df.parse("2014-01-01 00:00:00").getTime() / 1000.0;
        end = df.parse("2014-01-05 00:00:00").getTime() / 1000.0;
    }

    @Override
    public JsonNode sample() {
        final Car car = new Car();

        ObjectNode r = new ObjectNode(FACTORY);
        JsonNode homeLocation = homeSampler.sample();
        r.putObject("home").setAll((ObjectNode) homeLocation);
        GeoPoint home = new GeoPoint(toDegrees(homeLocation, "latitude"), toDegrees(homeLocation, "longitude"));

        double radius = workSampler.sample().asDouble();
        GeoPoint work = home.nearby(radius, rand);
        work.asJson(r.putObject("work"));

        ArrayNode data = r.putArray("data");
        ArrayNode trips = r.putArray("trips");
        car.getEngine().setTime(start);
        for (double t = start; t < end; ) {
            double tCommute = search(atHome, t, nextExponentialTime(1));
            if (atHome) {
                double tErrand = t + nextExponentialTime((isWeekend(t) ? WEEKEND_ERRAND_RATE : WEEKDAY_ERRAND_RATE) / DAY_IN_S);
                while (tErrand < tCommute && tErrand < end) {
                    GeoPoint nearby = home.nearby(ERRAND_SIZE_KM, rand);
                    t = drive(tErrand, car, home, nearby, data);
                    recordTrip(trips, tErrand, t - tErrand, "errand_out", 2 * home.distance(nearby));
                    t += rand.nextDouble() * 900 + 300;
                    double t0 = t;
                    t = drive(t, car, nearby, home, data);
                    recordTrip(trips, t0, t - t0, "errand_return", 2 * home.distance(nearby));
                    tErrand = t + nextExponentialTime(WEEKEND_ERRAND_RATE / DAY_IN_S);
                }
                if (tCommute < end) {
                    t = drive(tCommute, car, home, work, data);
                    recordTrip(trips, tCommute, t - tCommute, "to_work", home.distance(work));

                    atHome = !atHome;
                }
            } else {
                t = drive(tCommute, car, work, home, data);
                recordTrip(trips, tCommute, t - tCommute, "to_home", home.distance(work));
                atHome = !atHome;
            }
        }

        return r;
    }

    private double toDegrees(JsonNode h, String fieldName) {
        return h.get(fieldName).asDouble() * Math.PI / 180;
    }

    private void recordTrip(ArrayNode trips, double start, double duration, String type, double distance) {
        ObjectNode trip = trips.addObject();
        trip.put("t", duration);
        trip.put("timestamp", df.format(new Date((long) (duration *1000))));
        trip.put("type", type);
        trip.put("distance_km", distance);
        trip.put("duration", duration);
    }

    public long evenHour(double t) {
        return 3600 * ((long) t / 3600);
    }

    public int hourOfDay(double t) {
        synchronized (cal) {
            cal.setTimeInMillis((long) (t * 1000.0));
            cal.setTimeZone(TimeZone.getTimeZone("US/Central"));
            return cal.get(GregorianCalendar.HOUR_OF_DAY);
        }
    }

    public double search(boolean toWork, double t, double bound) {
        while (true) {
            double nextHour = evenHour(t + 3600);
            int hour = hourOfDay(t);

            double rate = lookupRate(isWeekend(t), toWork, hour);
            double step = rate * (nextHour - t);
            if (step > bound) {
                t = t + bound / step * (nextHour - t);
                return t;
            } else {
                bound -= step;
                t = nextHour;
            }
        }
    }

    public double lookupRate(boolean isWeekend, boolean toWork, int hour) {
        if (isWeekend) {
            return WEEKEND_COMMUTE_RATE / DAY_IN_S;
        } else {
            if (toWork) {
                if (hour >= 7 && hour < 9) {
                    return WEEKDAY_PEAK_COMMUTE_RATE / DAY_IN_S;
                } else {
                    return WEEKDAY_COMMUTE_RATE / DAY_IN_S;
                }
            } else {
                if (hour >= 16 && hour < 18) {
                    return WEEKDAY_PEAK_COMMUTE_RATE / DAY_IN_S;
                } else {
                    return WEEKDAY_COMMUTE_RATE / DAY_IN_S;
                }
            }
        }
    }

    public boolean isWeekend(double t) {
        synchronized (cal) {
            cal.setTimeInMillis((long) (t * 1000));
            int d = cal.get(GregorianCalendar.DAY_OF_WEEK);
            return d == GregorianCalendar.SATURDAY || d == GregorianCalendar.SUNDAY;
        }
    }

    private double drive(double t, Car car, GeoPoint start, GeoPoint end, ArrayNode data) {
        car.getEngine().setTime(t);
        return car.driveTo(rand, t, start, end, new Car.Callback() {
            @Override
            void call(double t, Engine car, GeoPoint position) {
                ObjectNode sample = data.addObject();
                position.asJson(sample);
                sample.put("t", t);
                sample.put("timestamp", df.format(new Date((long) (t * 1000))));
                sample.put("mph", car.getSpeed() * Constants.MPH);
                sample.put("rpm", car.getRpm());
                sample.put("throttle", car.getThrottle());
            }
        });
    }

    private double nextExponentialTime(double rate) {
        return -Math.log(1 - rand.nextDouble()) / rate;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setFormat(String format) {
        df = new SimpleDateFormat(format);
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setStart(String start) throws ParseException {
        this.start = df.parse(start).getTime() / 1000.0;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setEnd(String end) throws ParseException {
        this.end = df.parse(end).getTime() / 1000.0;
    }

    public void setHome(JsonNode value) throws IOException {
        if (value.isObject()) {
            homeSampler = FieldSampler.newSampler(value.toString());
        } else {
            throw new IllegalArgumentException("Expected geo-location sampler");
        }
    }

    public void setWork(JsonNode value) throws IOException {
        if (value.isObject()) {
            workSampler = new FieldSampler() {
                FieldSampler base = FieldSampler.newSampler(value.toString());

                @Override
                public JsonNode sample() {
                    return new DoubleNode(Math.sqrt(1 / base.sample().asDouble()));
                }
            };
        } else if (value.isNumber()) {
            workSampler = constant(value.asDouble());
        }
    }
}
