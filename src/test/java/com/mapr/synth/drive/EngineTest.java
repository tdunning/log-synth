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

import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

import static org.junit.Assert.*;

public class EngineTest {
    public static final double MPH_TO_M_S = (5280.0 * 12 * 2.54 / 100 / 3600);

    @Test
    public void testTimeTo60() {
        // the time to get to 60 MPH should be 6.5 to 8 seconds
        Engine car = new Engine();
        double t = 0;
        while (car.getSpeed() < 60 * MPH_TO_M_S) {
            t += 0.1;
            car.stepToTime(t, 90 * MPH_TO_M_S, 0);
            assertTrue("RPM limits", car.getRpm() >= 200 && car.getRpm() <= 2000);
        }
        assertEquals("Time to 60", t, (6.5 + 8) / 2, (8 - 6.5) / 2);
    }

    @Test
    public void testConvergeToTarget() {
        // the time to get to 60 MPH should be 6.5 to 8 seconds
        Engine car = new Engine();
        double t = 0;
        t = checkConvergence(car, t, 20);
        t = checkConvergence(car, t, 30);
        t = checkConvergence(car, t, 40);
        t = checkConvergence(car, t, 20);
        t = checkConvergence(car, t, 10);
        checkConvergence(car, t, 0);
    }

    private double checkConvergence(Engine car, double t, double target) {
        double t0 = t;
        target *= MPH_TO_M_S;
        double timeToTarget = 4;
        if (target < car.getSpeed()) {
            timeToTarget = 60;
        }
        while (Math.abs(car.getSpeed() - target) > 0.3 || car.getThrottle() > 20) {
            t += 0.1;
            car.stepToTime(t, target, 0);
        }
        assertTrue("Convergence to target time", t - t0 < timeToTarget);
        double limit = t + 10;
        while (t < limit) {
            t += 0.1;
            car.stepToTime(t, target, 0);
            assertEquals("Maintain speed", target, car.getSpeed(), 0.5);
        }
        return t;
    }

    @Test
    public void testPlotData() throws FileNotFoundException {
        try (PrintWriter out = new PrintWriter(new File("plot1.csv"))) {
            out.printf("t, target, speed, throttle, rpm, gear\n");
            Engine car = new Engine();
            // compute time to 90 MPH
            double[] target = {20, 40, 20, 30, 10, 0};
            double t = 0;
            for (; t < 240; t += 0.1) {
                double speedTarget = target[(int) t / 40] * 0.44704;
                car.stepToTime(t, speedTarget, 0);
                out.printf("%.2f, %.1f, %.1f, %.1f, %.1f, %d\n", t, speedTarget, car.getSpeed(), car.getThrottle(), car.getRpm(), car.getGear());
            }

            for (; t < 300; t += 0.1) {
                double speedTarget = 90 * 0.44704;
                car.stepToTime(t, speedTarget, 0);
                out.printf("%.2f, %.1f, %.1f, %.1f, %.1f, %d\n", t, speedTarget, car.getSpeed(), car.getThrottle(), car.getRpm(), car.getGear());
            }
        }
    }


    @Test
    public void testBraking() throws FileNotFoundException {
        try (PrintWriter out = new PrintWriter(new File("plot2.csv"))) {
            out.printf("t, target, speed, throttle, rpm, gear\n");
            Engine car = new Engine();
            // get to speed
            double t = 0;
            for (; t < 20; t += 0.1) {
                double speedTarget = 60 * 0.44704;
                car.stepToTime(t, speedTarget, 0);
                out.printf("%.2f, %.1f, %.1f, %.1f, %.1f, %d\n", t, speedTarget, car.getSpeed(), car.getThrottle(), car.getRpm(), car.getGear());
            }

            // stop gently
            for (; t < 50; t += 0.1) {
                double speedTarget = 0;
                car.stepToTime(t, speedTarget, 0.2);
                out.printf("%.2f, %.1f, %.1f, %.1f, %.1f, %d\n", t, speedTarget, car.getSpeed(), car.getThrottle(), car.getRpm(), car.getGear());
            }

            for (; t < 60; t += 0.1) {
                double speedTarget = 60 * 0.44704;
                car.stepToTime(t, speedTarget, 0);
                out.printf("%.2f, %.1f, %.1f, %.1f, %.1f, %d\n", t, speedTarget, car.getSpeed(), car.getThrottle(), car.getRpm(), car.getGear());
            }

            // stop gently
            for (; t < 80; t += 0.1) {
                double speedTarget = 0;
                car.stepToTime(t, speedTarget, 0.8);
                out.printf("%.2f, %.1f, %.1f, %.1f, %.1f, %d\n", t, speedTarget, car.getSpeed(), car.getThrottle(), car.getRpm(), car.getGear());
            }
        }
    }
}