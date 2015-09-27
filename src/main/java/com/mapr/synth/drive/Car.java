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

import com.google.common.collect.Lists;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import java.util.List;
import java.util.Random;

import static java.lang.Math.log;

/**
 * Produces quasi-plausible trip information for cars using the Engine
 * class for low level process simulation and a highly biased random
 * walk to pick way-points and speed targets.  <p> The basic idea is
 * that there are two special locations associated with a car. Call
 * them "home" and "work". The simulation proceeds by picking a script
 * to be followed and then elaborates that script with lower level
 * maneuvers to execute the script. All of the maneuvers start at
 * either home or work and end with the car being at one of those
 * locations.
 * <p>
 * The scripts available include:
 * <p>
 * a) commute from home to work or back
 * <p>
 * b) if at home, run an errand that involves driving to one or more
 * locations near the home.
 * <p>
 * A script consists of a sequence of way-points that must be
 * visited. To visit each waypoint, a driving strategy picks
 * maneuvers. The maneuvers available include short-term urban style
 * driving segments and longer highway-style segments. The choice of
 * urban or highway segment is made according to how far away the next
 * end-point is.
 * <p>
 * Each segment starts with a turn to a new bearing according to a
 * distribution that is biased based on where the destination is and
 * what the bearing for the current step was. Urban steps tend to
 * prefer a grid (ish) pattern of driving so the new bearing is either
 * the same as the current bearing or 90 degrees left or right of the
 * current bearing with some noise and some chance of a non-right
 * angle turn. Turns onto a highway segment have no grid bias, but are
 * heavily constrained by the direction to the next waypoint.
 * <p>
 * Urban segments also have more variable and lower speeds. Highway
 * segments have relative consistent high speeds.
 */
public class Car {
    // how well do the brakes work
    private static final double BRAKING_ACCELERATION = 0.1;

    private Engine engine;

    public Car(Engine engine) {
        this.engine = engine;
    }

    public Car() {
        this(new Engine());
    }

    public double simulate(double t, GeoPoint currentPosition, Random rand, Segment segment, Callback progress) {
        double targetSpeed = segment.travelSpeed();
        double currentSpeed = 0;
        final double dt = 1;
        final double dv = 0.1 * Constants.G * dt;

        Vector3D start = currentPosition.as3D();
        double distanceToGo = currentPosition.distance(segment.end);
        engine.setDistance(0);
        Vector3D travelDirection = segment.end.as3D().subtract(currentPosition.as3D()).normalize();
        double previousDistance = distanceToGo;
        while (distanceToGo <= previousDistance) {
            if (rand.nextDouble() < 0.05) {
                targetSpeed = Math.max(20 * Constants.MPH, targetSpeed + (rand.nextInt(5) - 2) * 5 * Constants.MPH);
            }
            targetSpeed = Math.min(segment.maxSpeed(), targetSpeed);

            if (currentSpeed < targetSpeed) {
                currentSpeed += dv;
            } else {
                currentSpeed -= dv;
            }
            currentSpeed = Math.min(currentSpeed, maxSpeed(distanceToGo * 1000, segment.exitSpeed()));
            engine.stepToTime(t, currentSpeed, BRAKING_ACCELERATION);
            t += dt;
            currentPosition.setPosition(start.add(travelDirection.scalarMultiply(engine.getDistance() / 1000 / Constants.EARTH_RADIUS_KM)));
            progress.call(t, engine, currentPosition);
            previousDistance = distanceToGo;
            distanceToGo = currentPosition.distance(segment.end);
        }
        return t;
    }

    /**
     * Produces a sequenct of segments that result in travel from start to end.
     *
     * @param start Where the trip starts
     * @param end   Where the trip ends
     * @param rand  Random number generator to use
     * @return A list of trip segments
     */
    public static List<Segment> plan(GeoPoint start, GeoPoint end, Random rand) {
        GeoPoint here = start;
        List<Segment> plan = Lists.newArrayList();
        double distanceToGo = here.distance(end);
        while (distanceToGo > Constants.GEO_FUZZ && here.distance(start) < 3) {
            Local step = new Local(here, end, rand);
            plan.add(step);
            here = step.getEnd();
            distanceToGo = here.distance(end);
        }
        while (distanceToGo > Constants.GEO_FUZZ) {
            if (pickHighway(distanceToGo, rand)) {
                Highway step = new Highway(end.nearby(distanceToGo / 10, rand));
                plan.add(step);
                here = step.getEnd();
            } else {
                Local step = new Local(here, end, rand);
                plan.add(step);
                here = step.getEnd();
            }
            distanceToGo = here.distance(end);
        }
        return plan;
    }

    double driveTo(Random rand, double t, GeoPoint start, GeoPoint end, Callback callback) {
        List<Segment> plan = plan(start, end, rand);
        final GeoPoint currentPosition = new GeoPoint(start.as3D());
        for (Segment segment : plan) {
            t = simulate(t, currentPosition, rand, segment, callback);
        }
        return t;
    }

    public Engine getEngine() {
        return engine;
    }

    public static abstract class Callback {
        abstract void call(double t, Engine arg, GeoPoint position);
    }

    /**
     * What is our current max speed given our distance to our segment end and our desired exit speed. Note
     * that we leave leave 20 meters margin and never quote a max speed less than 5 m/s.
     *
     * @param distance  How far to the end of the segment
     * @param exitSpeed How fast should we be going at the end
     * @return How fast we are allowed to be going right now.
     */
    private static double maxSpeed(double distance, double exitSpeed) {
        double margin = 0.5 * exitSpeed * exitSpeed / (BRAKING_ACCELERATION * Constants.G);
        return Math.max(5, Math.sqrt(2 * (distance + margin - 0.020) * BRAKING_ACCELERATION * Constants.G));
    }

    /**
     * Chooses whether to plan a "highway" or "local" segment based on the distance to be traveled.
     *
     * @return True if this should be a highway segment.
     */
    private static boolean pickHighway(double distance, Random rand) {
        // formula was picked heuristically to fit intuition
        // d(km) p
        //   1  0.002472623
        //   2  0.013828044
        //   5  0.121702566
        //   10  0.439414832
        //   20  0.815977791
        //   50  0.977687816
        //   100  0.995981922

        double logOdds = -6 + 2 * log(distance);
        double u = rand.nextDouble();
        return log(u / (1 - u)) < logOdds;
    }

    public static class Highway extends Segment {
        public Highway(GeoPoint end) {
            super.end = end;
        }

        @Override
        public double exitSpeed() {
            return 30 * Constants.MPH;
        }

        @Override
        public double travelSpeed() {
            return 65 * Constants.MPH;
        }

        @Override
        public double maxSpeed() {
            return 75 * Constants.MPH;
        }
    }

    public static class Local extends Segment {
        public Local(GeoPoint start, GeoPoint end, Random rand) {
            Vector3D dr = end.as3D().subtract(start.as3D());
            double distance = dr.getNorm();

            double step = Math.abs((rand.nextGaussian() + 2) / Constants.EARTH_RADIUS_KM);

            Vector3D east = start.east();
            double eastWest = dr.dotProduct(east);
            double p = eastWest / distance;
            if (rand.nextDouble() < Math.abs(p * p)) {

                // go east/west
                if (step > Math.abs(eastWest)) {
                    // don't overshoot
                    step = Math.abs(eastWest);
                }
                super.end = new GeoPoint(start.r.add(step * Math.signum(eastWest), east));
            } else {
                Vector3D north = start.north(east);
                double northSouth = dr.dotProduct(north);
                if (step > Math.abs(northSouth)) {
                    step = Math.abs(northSouth);
                }
                super.end = new GeoPoint(start.r.add(step * Math.signum(northSouth), north));
            }
        }

        @Override
        public double exitSpeed() {
            return 5 * Constants.MPH;
        }

        @Override
        public double travelSpeed() {
            return 35 * Constants.MPH;
        }

        @Override
        public double maxSpeed() {
            return 45 * Constants.MPH;

        }
    }

    public static abstract class Segment {
        private GeoPoint end;

        public GeoPoint getEnd() {
            return end;
        }

        public abstract double travelSpeed();

        public abstract double maxSpeed();

        public abstract double exitSpeed();
    }
}
