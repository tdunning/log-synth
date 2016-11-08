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

import java.util.Random;
import java.util.concurrent.BlockingQueue;

public class Producer implements Runnable {
    BlockingQueue<Trails.State> output;

    public Producer(BlockingQueue<Trails.State> output) {
        this.output = output;
    }

    @Override
    public void run() {
        Random rand = new Random(3);
        GeoPoint start = new GeoPoint((rand.nextDouble() - 0.5) * Math.PI / 2, rand.nextDouble() * Math.PI * 2);
        final Vector3D east = start.east();
        final Vector3D north = start.north(east);

        GeoPoint end = new GeoPoint(start.as3D().add(east.scalarMultiply(-12.0 / Constants.EARTH_RADIUS_KM)).add(north.scalarMultiply(7.0 / Constants.EARTH_RADIUS_KM)));

        Vector3D zz = project(east, north, end.as3D());
        System.out.printf("==> %.2f %.2f\n", zz.getX(), zz.getY());

        //noinspection InfiniteLoopStatement
        while (true) {
            double t = 0;
            final Car car = new Car();

            System.out.printf("%.2f\n", start.distance(end));
            car.driveTo(rand, t, start, end, new Car.Callback() {
                @Override
                void call(double t, Engine eng, GeoPoint position) {
                    final Vector3D here = project(east, north, position.as3D());
                    try {
                        output.put(new Trails.State(new Engine(eng), here));
                    } catch (InterruptedException e) {
                        throw new RuntimeException("Interrupted", e);
                    }
                }
            });
        }
    }

    static Vector3D project(Vector3D east, Vector3D north, Vector3D step) {
        return new Vector3D(step.dotProduct(east) * Constants.EARTH_RADIUS_KM, step.dotProduct(north) * Constants.EARTH_RADIUS_KM, 0);
    }
}
