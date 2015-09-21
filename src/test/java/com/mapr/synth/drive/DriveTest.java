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
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class DriveTest {
    @Test
    public void testPlanning() throws FileNotFoundException {
        Random rand = new Random(3);
        Drive.GeoPoint start = new Drive.GeoPoint((rand.nextDouble() - 0.5) * Math.PI / 2, rand.nextDouble() * Math.PI * 2);
        Drive.GeoPoint end = start.nearby(10, rand);

        Vector3D east = start.east();
        Vector3D north = start.north(east);
        try (PrintWriter out = new PrintWriter(new File("short-drive.csv"))) {
            out.printf("i, highway, x0, y0, x1, y1");
            double highway = 0;
            int n = 0;
            for (int i = 0; i < 50; i++) {
                List<Drive.Segment> plan = Drive.plan(start, end, rand);
                assertTrue(plan.size() < 20);
                Vector3D here = project(east, north, start.as3D());
                for (Drive.Segment segment : plan) {
                    Vector3D step = project(east, north, segment.getEnd().as3D());
                    n++;
                    if (segment instanceof Drive.Highway) {
                        highway++;
                    }
                    out.printf("%d, %d, %.4f, %.4f, %.4f, %.4f\n", i, (segment instanceof Drive.Local) ? 1 : 0,
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
        Drive.GeoPoint start = new Drive.GeoPoint((rand.nextDouble() - 0.5) * Math.PI / 2, rand.nextDouble() * Math.PI * 2);
        Drive.GeoPoint end = start.nearby(10, rand);

        final Vector3D east = start.east();
        final Vector3D north = start.north(east);
        try (PrintWriter out = new PrintWriter(new File("drive-sim.csv"))) {
            out.printf("i, t, x, y, rpm, throttle, speed, gear");
            for (int i = 0; i < 50; i++) {
                final int trial = i;
                double t = 0;
                final Engine car = new Engine();

                List<Drive.Segment> plan = Drive.plan(start, end, rand);
                assertTrue(plan.size() < 20);
                final Drive.GeoPoint currentPosition = new Drive.GeoPoint(start.as3D());
                for (Drive.Segment segment : plan) {
                    t = Drive.simulate(t, currentPosition, rand, segment, car, new Drive.Callback() {
                        @Override
                        void call(double t, Engine arg, Drive.GeoPoint position) {
                            final Vector3D here = project(east, north, currentPosition.as3D());
                            out.printf("%d, %.2f, %.2f, %.2f, %.1f, %.1f, %.1f, %d\n",
                                    trial, t, here.getX(), here.getY(),
                                    car.getRpm(), car.getThrottle(), car.getSpeed(), car.getGear());
                        }
                    });
                    assertEquals(0, currentPosition.distance(segment.getEnd()), 0.01);
                }

                // should arrive at our destination!
                assertEquals(0.0, currentPosition.distance(end), 0.01 );
            }
        }
    }

    Vector3D project(Vector3D east, Vector3D north, Vector3D step) {
        return new Vector3D(step.dotProduct(east) * Constants.EARTH_RADIUS_KM, step.dotProduct(north) * Constants.EARTH_RADIUS_KM, 0);
    }
}