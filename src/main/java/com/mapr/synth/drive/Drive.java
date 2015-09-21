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
public class Drive {
    // how far away must points be to be considered distinct?
    private static final double GEO_FUZZ = 0.005;
    private static final double ERRAND = 0.5;
    private static final double ERRAND_SIZE_KM = 20;

    // how well do the brakes work
    private static final double BRAKING_ACCELERATION = 0.1;

    // convert MPH to m/s
    private static final double MPH = (5280 * 12 * 2.54) / (100 * 3600);

    Random rand = new Random();

    GeoPoint home;
    GeoPoint work;

    List<Segment> currentTrip = Lists.newArrayList();
    GeoPoint currentPosition;

    double sampleTime = 1;

    public static double simulate(double t, GeoPoint currentPosition, Random rand, Segment segment, Engine car, Callback progress) {
        double targetSpeed = segment.travelSpeed();
        double currentSpeed = 0;
        final double dt = 1;
        final double dv = 0.1 * Constants.G * dt;

        Vector3D start = currentPosition.as3D();
        double distanceToGo = currentPosition.distance(segment.end);
        car.setDistance(0);
        Vector3D travelDirection = segment.end.as3D().subtract(currentPosition.as3D()).normalize();
        while (distanceToGo > GEO_FUZZ) {
            if (rand.nextDouble() < 0.05) {
                targetSpeed = Math.max(20 * MPH, targetSpeed + (rand.nextInt(5) - 2) * 5 * MPH);
            }
            targetSpeed = Math.min(segment.maxSpeed(), targetSpeed);

            if (currentSpeed < targetSpeed) {
                currentSpeed += dv;
            } else {
                currentSpeed -= dv;
            }
            currentSpeed = Math.min(currentSpeed, maxSpeed(distanceToGo * 1000, segment.exitSpeed()));
            car.stepToTime(t, currentSpeed, BRAKING_ACCELERATION);
            t += dt;
            currentPosition.setPosition(start.add(travelDirection.scalarMultiply(car.getDistance() / 1000 / Constants.EARTH_RADIUS_KM)));
            progress.call(t, car, currentPosition);
            distanceToGo = currentPosition.distance(segment.end);
        }
        return t;
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
        while (distanceToGo > GEO_FUZZ && here.distance(start) < 3) {
            Local step = new Local(here, end, rand);
            plan.add(step);
            here = step.getEnd();
            distanceToGo = here.distance(end);
        }
        while (distanceToGo > GEO_FUZZ) {
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

    public static class GeoPoint {
        private static final Vector3D X = new Vector3D(1, 0, 0);
        private static final Vector3D Z = new Vector3D(0, 0, 1);

        Vector3D r;

        public GeoPoint(double latitude, double longitude) {
            double c = Math.cos(latitude);
            r = new Vector3D(Math.cos(longitude) * c, Math.sin(longitude) * c, Math.sin(latitude));
        }

        public GeoPoint(Vector3D r) {
            this.r = r;
        }

        public Vector3D as3D() {
            return r;
        }

        public double distance(GeoPoint x) {
            // the dot product could also be used here, but we expect small distances mostly
            // so the sine formulation is more accurate
            return Constants.EARTH_RADIUS_KM * Math.asin(r.crossProduct(x.r).getNorm());
        }

        public GeoPoint nearby(double distance, Random rand) {
            distance = distance / Constants.EARTH_RADIUS_KM;
            double u = rand.nextGaussian();
            double v = rand.nextGaussian();
            return project(distance * u, distance * v);
        }

        public GeoPoint project(double u, double v) {
            Vector3D ux = east();
            Vector3D vx = north(ux);

            return new GeoPoint(r.add(ux.scalarMultiply(u).add(vx.scalarMultiply(v))).normalize());
        }

        public Vector3D north(Vector3D ux) {
            return r.crossProduct(ux).normalize();
        }

        public Vector3D east(Vector3D r) {
            Vector3D ux = r.crossProduct(Z);
            if (ux.getNorm() < 1e-4) {
                // near the poles (i.e. < 640 meters from them), the definition of east is difficult
                ux = this.r.crossProduct(X);
            }
            ux = ux.normalize();
            return ux;
        }

        public Vector3D east() {
            return east(r);
        }

        public void setPosition(Vector3D position) {
            this.r = position;
        }
    }

    public static class Highway extends Segment {
        public Highway(GeoPoint end) {
            super.end = end;
        }

        @Override
        public double exitSpeed() {
            return 30 * MPH;
        }

        @Override
        public double travelSpeed() {
            return 65 * MPH;
        }

        @Override
        public double maxSpeed() {
            return 75 * MPH;
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
            return 5 * MPH;
        }

        @Override
        public double travelSpeed() {
            return 35 * MPH;
        }

        @Override
        public double maxSpeed() {
            return 45 * MPH;

        }
    }
}
