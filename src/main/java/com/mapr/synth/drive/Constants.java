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

class Constants {
    static final double EARTH_RADIUS_KM = 6371.39;
    // acceleration of gravity in m/s/s
    static final double G = 9.80665;
    // convert MPH to m/s
    static final double MPH = (5280 * 12 * 2.54) / (100 * 3600);
    // how far away must points be to be considered distinct?
    static final double GEO_FUZZ = 0.005;
}
