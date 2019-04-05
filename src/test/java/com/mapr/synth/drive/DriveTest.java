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

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DriveTest {
    @Test
    public void testPlanning() throws FileNotFoundException {
        Random rand = new Random(3);
        GeoPoint start = new GeoPoint((rand.nextDouble() - 0.5) * Math.PI / 2, rand.nextDouble() * Math.PI * 2);
        GeoPoint end = start.nearby(10, rand);

        Vector3D east = start.east();
        Vector3D north = start.north(east);
        try (PrintWriter out = new PrintWriter(new File("short-drive.csv"))) {
            out.printf("i, highway, x0, y0, x1, y1");
            double highway = 0;
            int n = 0;
            for (int i = 0; i < 50; i++) {
                List<Car.Segment> plan = Car.plan(start, end, rand);
                assertTrue(plan.size() < 20);
                Vector3D here = project(east, north, start.as3D());
                for (Car.Segment segment : plan) {
                    Vector3D step = project(east, north, segment.getEnd().as3D());
                    n++;
                    if (segment instanceof Car.Highway) {
                        highway++;
                    }
                    out.printf("%d, %d, %.4f, %.4f, %.4f, %.4f\n", i, (segment instanceof Car.Local) ? 1 : 0,
                            here.getX(), here.getY(), step.getX(), step.getY());
                    here = step;
                }

                // should arrive at our destination!
                assertEquals(0.0, here.subtract(project(east, north, end.as3D())).getNorm(), 0.01);
            }
            // most steps should be local, not highway
            assertEquals(0.0, highway / n, 0.1);
        }
    }

    @Test
    public void testDriving() throws FileNotFoundException {
        Random rand = new Random(3);
        GeoPoint start = new GeoPoint((rand.nextDouble() - 0.5) * Math.PI / 2, rand.nextDouble() * Math.PI * 2);
        GeoPoint end = start.nearby(10, rand);

        final Vector3D east = start.east();
        final Vector3D north = start.north(east);
        try (PrintWriter out = new PrintWriter(new File("drive-sim.csv"))) {
            out.printf("i, t, x, y, rpm, throttle, speed, gear");
            for (int i = 0; i < 50; i++) {
                final int trial = i;
                double t = 0;
                Car car = new Car();

                List<Car.Segment> plan = Car.plan(start, end, rand);
                assertTrue(plan.size() < 20);
                final GeoPoint currentPosition = new GeoPoint(start.as3D());
                for (Car.Segment segment : plan) {
                    t = car.simulate(t, currentPosition, rand, segment, new Car.Callback() {
                        @Override
                        void call(double t, Engine arg, GeoPoint position) {
                            final Vector3D here = project(east, north, currentPosition.as3D());
                            Engine engine = car.getEngine();
                            out.printf("%d, %.2f, %.2f, %.2f, %.1f, %.1f, %.1f, %d\n",
                                    trial, t, here.getX(), here.getY(),
                                    engine.getRpm(), engine.getThrottle(), engine.getSpeed(), engine.getGear());
                        }
                    });
                    assertEquals(0, currentPosition.distance(segment.getEnd()), 0.04);
                }

                // should arrive at our destination!
                assertEquals(0.0, currentPosition.distance(end), 0.01);
            }
        }
    }

    @Test
    public void testCrazyPlan() {
        Random rand = new Random(3);
        GeoPoint start = new GeoPoint(new Vector3D(0.84338521, 0.40330691, 0.35502805));
        GeoPoint end = new GeoPoint(new Vector3D(0.84293820, 0.40512281, 0.35402076));
        for (int i = 0; i < 100000; i++) {
            List<Car.Segment> plan = Car.plan(start, end, rand);
            GeoPoint old = start;
            for (Car.Segment segment : plan.subList(1, plan.size())) {
                double distance = old.distance(segment.getEnd());
                if (distance > 100 || Double.isNaN(distance)) {
                    Assert.fail("Got bad distance");
                }
                old = segment.getEnd();
            }
        }
    }

    @SuppressWarnings("WeakerAccess")
    static Vector3D project(Vector3D east, Vector3D north, Vector3D step) {
        return new Vector3D(step.dotProduct(east) * Constants.EARTH_RADIUS_KM, step.dotProduct(north) * Constants.EARTH_RADIUS_KM, 0);
    }
}