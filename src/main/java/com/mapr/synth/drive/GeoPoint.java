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

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import java.util.Random;

/**
 * Created by tdunning on 9/25/15.
 */
public class GeoPoint {
    private static final Vector3D X = new Vector3D(1, 0, 0);
    private static final Vector3D Z = new Vector3D(0, 0, 1);

    private static JsonNodeFactory nodeFactory = JsonNodeFactory.withExactBigDecimals(false);

    Vector3D r;

    public GeoPoint(double latitude, double longitude) {
        double c = Math.cos(latitude);
        r = new Vector3D(Math.cos(longitude) * c, Math.sin(longitude) * c, Math.sin(latitude));
    }

    public GeoPoint(Vector3D r) {
        this.r = r.normalize();
    }

    public Vector3D as3D() {
        return r;
    }

    public double distance(GeoPoint x) {
        // the dot product could also be used here, but we expect small distances mostly
        // so the haversine formulation is more accurate
        return Constants.EARTH_RADIUS_KM * 2 * Math.asin(r.subtract(x.r).getNorm() / 2);
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
        this.r = position.normalize();
    }

    public ObjectNode asJson(ObjectNode node) {
        node.set("latitude", nodeFactory.numberNode(180 / Math.PI * Math.asin(r.getZ())));
        node.set("longitude", nodeFactory.numberNode(180 / Math.PI * Math.atan2(r.getY(), r.getX())));
        return node;
    }
}
