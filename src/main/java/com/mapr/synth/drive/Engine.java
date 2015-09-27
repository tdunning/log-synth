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

import java.io.Serializable;

/**
 * Simulates engine behavior with automatic transmission.
 * <p>
 * The basic idea is that we have a transmission with different gears.
 * The transmission will shift to a higher gear as RPM's reach a high
 * shift point and will shift down as RPM's reach a low shift point.
 * <p>
 * The shift points depend a bit on load which, in turn, depends on
 * whether we are accelerating or decelerating.
 * <p>
 * The input is a desired speed. The throttle setting will be selected
 * based on whether we are too slow or fast. That will determine the load
 * which in turn determines the shift points. The engine load and transmission
 * setting will determine how the speed changes and off we go.
 * <p>
 * So far, this works pretty well, but it doesn't the engine braking emulation
 * is kind of a hack.
 */
public class Engine implements Serializable {

    private static final double THROTTLE_CONTROL_GAIN = 50;
    private static final double MAX_THROTTLE = 100;
    private static final double ACCELERATION_BACKOFF = 30;

    // observed transmission properties for typical turbo-diesel
    private static final double[] MPS_BY_RPM = {
            4.4704 / 2000, 8.9408 / 2000, 13.4112 / 2000, 13.4112 / 1500, 17.8816 / 1500, 22.3520 / 1500, 22.3520 / 1000
    };
    private static final int TOP_GEAR = MPS_BY_RPM.length - 1;
    private static final double ZERO_TORQUE_RPM = 3500;

    private static final double LOW_SHIFT = 1000;
    private static final double HIGH_SHIFT = 2000;

    // The throttle has some turbo delay.
    public static final double THROTTLE_TIME_CONSTANT = 0.8;

    // in kg, not a super light car
    private static final double VEHICLE_MASS = 2000;

    // in watts (this is about 200 HP)
    private static final double MAX_POWER = 150e3;
    private static final double TORQUE_AT_ZERO = (4 * MAX_POWER / ZERO_TORQUE_RPM);

    // assuming 150 MPH absolute max speed
    private static final double DRAG_COEFFICIENT = 0.4875334;
    private static final double SHIFT_TIME = 0.1;
    private static final double BRAKING_GAIN = 1;

    // this determines the time resolution of our computation (in s)
    private double dt = 0.01;

    private double currentTime = 0;

    // throttle is a slow threshold function that
    // rises asymptotically to a maximum as long as we are too slow
    // and falls fairly more quickly when we are too fast
    private double currentThrottle = 0;
    private double brakeForce = 0;
    private double shiftTimeOut = 0;

    private int currentGear = 0;

    private double currentSpeed = 0;
    private double currentRPM = 0;
    private double currentAcceleration;
    private double currentDistance = 0;

    public Engine() {
    }

    public Engine(Engine eng) {
        this();
        brakeForce = eng.brakeForce;
        currentAcceleration = eng.currentAcceleration;
        currentDistance = eng.currentDistance;
        currentGear = eng.currentGear;
        currentRPM = eng.currentRPM;
        currentSpeed = eng.currentSpeed;
        currentThrottle = eng.currentThrottle;
        currentTime = eng.currentTime;
        dt = eng.dt;
    }

    /**
     * Runs the simulation up to just past the desired sampleTime with a specified
     * target speed.
     *
     * @param sampleTime  When to stop the simulation and return
     * @param speedTarget The speed we would like to reach
     * @param maxBrake    The maximum amount of braking in g's. Typically 0.1 for gentle driving, 1 for maniacs.
     */
    public void stepToTime(double sampleTime, double speedTarget, double maxBrake) {
        while (currentTime < sampleTime) {

            // throttle rises or falls quite fast unless we are close to the desired speed
            // when speed is close, we switch to a bit of a proportional control
            double desiredThrottle = THROTTLE_CONTROL_GAIN * (speedTarget - currentSpeed) - ACCELERATION_BACKOFF * currentAcceleration;
            desiredThrottle = Math.min(MAX_THROTTLE, desiredThrottle);
            desiredThrottle = Math.max(0, desiredThrottle);

            currentThrottle += (desiredThrottle - currentThrottle) / THROTTLE_TIME_CONSTANT * dt;

            // gear box with a bit of slip. We need the slip to get non-zero power when speed == 0
            currentRPM = currentSpeed / MPS_BY_RPM[currentGear] + 200;

            // shifting algorithm is simplistic, but good enough
            if (currentRPM > HIGH_SHIFT && currentGear < TOP_GEAR) {
                currentGear++;
                shiftTimeOut = currentTime + SHIFT_TIME;
            } else if (currentRPM < LOW_SHIFT && currentGear > 0) {
                currentGear--;
                shiftTimeOut = currentTime + SHIFT_TIME;
            }

            // a shift might have changed our RPM's.
            currentRPM = currentSpeed / MPS_BY_RPM[currentGear] + 200;

            // if we are moving along, then we allow for 5% of the throttle setting to emulate engine braking
            // the constant negative value represents engine losses
            double powerSetting = currentThrottle / 100.0 - 0.05;
            if (shiftTimeOut > currentTime) {
                powerSetting = 0;
            }

            // power is a combination of assumed linear decrease in torque from zero to some high RPM
            double engineForce = TORQUE_AT_ZERO / MPS_BY_RPM[currentGear] * (1.0 - currentRPM / ZERO_TORQUE_RPM) * powerSetting;

            // drag is based on an asymptotic max speed of 150 MPH where aerodynamic drag == enginePower
            // the extra drag is engine drag
            double dragForce = DRAG_COEFFICIENT * currentSpeed * currentSpeed;

            if (maxBrake > 0 && currentThrottle < 2 && speedTarget < currentSpeed) {
                brakeForce += VEHICLE_MASS * BRAKING_GAIN * (currentSpeed - speedTarget) * dt;
                brakeForce = Math.min(brakeForce, VEHICLE_MASS * maxBrake * Constants.G);
            } else {
                brakeForce = 0;
            }

            // force applied to change speed is a simple net, but with a hack to avoid infinite torque at zero speed
            double netForce = engineForce - dragForce - brakeForce;

            // note that we don't allow crazy acceleration ... the tires would break loose
            currentAcceleration = Math.min(8, netForce / VEHICLE_MASS);

            double oldSpeed = currentSpeed;
            currentSpeed += currentAcceleration * dt;
            currentSpeed = Math.max(0, currentSpeed);
            currentDistance += (oldSpeed + currentSpeed) * dt / 2;

            currentTime += dt;
        }
    }

    public double getSpeed() {
        return currentSpeed;
    }

    public double getThrottle() {
        return currentThrottle;
    }

    public double getRpm() {
        return currentRPM;
    }

    public int getGear() {
        return currentGear;
    }

    public void setDistance(double distance) {
        this.currentDistance = distance;
    }

    public double getDistance() {
        return currentDistance;
    }

    public void setTime(double time) {
        this.currentTime = time;
    }
}
