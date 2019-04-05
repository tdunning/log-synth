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
import com.google.common.collect.Lists;
import com.mapr.synth.samplers.FieldSampler;
import com.mapr.synth.samplers.SchemaSampler;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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
    private static final double WEEKEND_ERRAND_RATE = 0.9;

    private static final double WEEKDAY_COMMUTE_RATE = 2;
    private static final double WEEKDAY_PEAK_COMMUTE_RATE = 10;
    private static final double WEEKDAY_ERRAND_RATE = 0.5;

    private static final double DAY_IN_S = 24 * 3600.0;
    private static final JsonNodeFactory FACTORY = JsonNodeFactory.withExactBigDecimals(false);
    @SuppressWarnings("unused")
    private static final int RECORD_LIMIT = 2000;

    // simulation period in seconds
    private double start;
    private double end;

    // internal mechanics
    private Random rand = new Random();
    private DateFormat df;
    final static private ThreadLocal<GregorianCalendar> cal = ThreadLocal.withInitial(GregorianCalendar::new);

    // is the commuter at home?
    private boolean atHome;

    private double sampleTime = 1;
    private FieldSampler homeSampler;
    private FieldSampler workSampler;
    private List<FieldSampler> extraSchema = null;
    private SchemaSampler extrasSampler = null;

    @SuppressWarnings("unused")
    private BlockingQueue<JsonNode> resultBuffer = new LinkedBlockingQueue<>();

    private boolean isFlat;

    Commuter() throws ParseException {
        setFormat("yyyy-MM-dd HH:mm:ss");
        start = df.parse("2014-01-01 00:00:00").getTime() / 1000.0;
        end = df.parse("2014-01-05 00:00:00").getTime() / 1000.0;
    }

    static boolean isWeekend(double t) {
        GregorianCalendar c = cal.get();
        c.setTimeInMillis((long) (t * 1000));
        int d = c.get(GregorianCalendar.DAY_OF_WEEK);
        return d == GregorianCalendar.SATURDAY || d == GregorianCalendar.SUNDAY;
    }

    @Override
    public JsonNode sample() {
        final Car car = new Car();
        if (extraSchema != null) {
            extrasSampler = new SchemaSampler(extraSchema);
        }
        car.setSampleTime(sampleTime);
        car.getEngine().setTime(start);

        JsonNode homeLocation = homeSampler.sample();
        GeoPoint home = new GeoPoint(Util.toDegrees(homeLocation, "latitude"), Util.toDegrees(homeLocation, "longitude"));

        double radius = workSampler.sample().asDouble();
        GeoPoint work = home.nearby(radius, rand);

        //---------------
        ObjectNode base = new ObjectNode(FACTORY);
        base.putObject("home").setAll((ObjectNode) homeLocation);

        work.asJson(base.putObject("work"));
        ArrayNode trips = new ArrayNode(FACTORY);

        for (double t = start; t < end; ) {
            double tCommute = search(atHome, t, Util.nextExponentialTime(rand, 1));
            if (atHome) {
                double tErrand = t + Util.nextExponentialTime(rand, (isWeekend(t) ? WEEKEND_ERRAND_RATE : WEEKDAY_ERRAND_RATE) / DAY_IN_S);
                while (tErrand < tCommute && tErrand < end) {
                    GeoPoint nearby = home.nearby(ERRAND_SIZE_KM, rand);
                    ObjectNode trip = trips.addObject();
                    ArrayNode data = trip.putArray("data");
                    t = drive(tErrand, car, home, nearby, data);
                    recordTrip(tErrand, t - tErrand, "errand_out", 2 * home.distance(nearby), trip);
                    t += rand.nextDouble() * 900 + 300;
                    double t0 = t;
                    trip = trips.addObject();
                    data = trip.putArray("data");
                    t = drive(t0, car, nearby, home, data);
                    recordTrip(t0, t - t0, "errand_return", 2 * home.distance(nearby), trip);
                    tErrand = t + Util.nextExponentialTime(rand, WEEKEND_ERRAND_RATE / DAY_IN_S);
                }
                if (tCommute < end) {
                    ObjectNode trip = trips.addObject();
                    ArrayNode data = trip.putArray("data");
                    t = drive(tCommute, car, home, work, data);
                    recordTrip(tCommute, t - tCommute, "to_work", home.distance(work), trip);

                    atHome = !atHome;
                }
            } else {
                ObjectNode trip = trips.addObject();
                ArrayNode data = trip.putArray("data");
                t = drive(tCommute, car, work, home, data);
                recordTrip(tCommute, t - tCommute, "to_home", home.distance(work), trip);
                atHome = !atHome;
            }
        }

        if (!isFlat) {
            ObjectNode x = base.deepCopy();
            x.set("trips", trips);
            return x;
        } else {
            ArrayNode r = new ArrayNode(FACTORY);
            for (JsonNode trip : trips) {
                ObjectNode x = base.deepCopy();
                Iterator<String> jx = trip.fieldNames();
                while (jx.hasNext()) {
                    String field = jx.next();
                    if (!field.equals("data")) {
                        x.set(field, trip.get(field));
                    }
                }
                for (JsonNode dataPoint : trip.get("data")) {
                    ObjectNode y = x.deepCopy();
                    y.setAll((ObjectNode) dataPoint);
                    r.add(y);
                }
            }

            return r;
        }
    }

    private void recordTrip(double start, double duration, String type, double distance, ObjectNode trip) {
        trip.put("t", duration);
        //noinspection SynchronizeOnNonFinalField
        synchronized (df) {
            trip.put("start", df.format(new Date((long) (start * 1000))));
        }
        trip.put("timestamp", (long) start * 1000);
        trip.put("type", type);
        trip.put("distance_km", distance);
        trip.put("duration", duration);
    }

    int hourOfDay(double t) {
        GregorianCalendar c = cal.get();
        c.setTimeInMillis((long) (t * 1000.0));
        c.setTimeZone(TimeZone.getTimeZone("US/Central"));
        return c.get(GregorianCalendar.HOUR_OF_DAY);
    }

    double search(boolean toWork, double t, double bound) {
        while (true) {
            double nextHour = Util.evenHour(t + 3600);
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

    private static double lookupRate(boolean isWeekend, boolean toWork, int hour) {
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

    private double drive(double t, Car car, GeoPoint start, GeoPoint end, ArrayNode data) {
        car.getEngine().setTime(t);
        return car.driveTo(rand, t, start, end, new Car.Callback() {
            @Override
            void call(double t, Engine car, GeoPoint position) {
                ObjectNode sample = data.addObject();
                position.asJson(sample);
                sample.put("t", t);
                //noinspection SynchronizeOnNonFinalField
                synchronized (df) {
                    sample.put("timestamp", df.format(new Date((long) (t * 1000))));
                }
                sample.put("mph", car.getSpeed() * Constants.MPH);
                sample.put("rpm", car.getRpm());
                sample.put("throttle", car.getThrottle());
                if (extraSchema != null) {
                    sample.setAll((ObjectNode) extrasSampler.sample());
                }
            }
        });
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

    @SuppressWarnings("unused")
    public void setSampleTime(double sampleTime) {
        this.sampleTime = sampleTime;
    }

    @SuppressWarnings("unused")
    public void setHome(JsonNode value) throws IOException {
        if (value.isObject()) {
            homeSampler = FieldSampler.newSampler(value.toString());
        } else {
            throw new IllegalArgumentException("Expected geo-location sampler");
        }
    }

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
    public void setExtras(JsonNode value) {
        if (value.isArray()) {
            extraSchema = Lists.newArrayList();
            for (JsonNode jsonNode : value) {
                extraSchema.add(FieldSampler.newSampler(jsonNode));
            }
        } else if (value.isObject()) {
            extraSchema = Collections.singletonList(FieldSampler.newSampler(value));
        } else {
            throw new IllegalArgumentException("Must have JSON object or list as definition of extra fields");
        }
    }

    @SuppressWarnings("unused")
    public void setFlat(boolean isFlat) {
        this.isFlat = isFlat;
    }

    @Override
    public boolean isFlat() {
        return isFlat;
    }
}
