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

// Example of post-processing function.  This function looks at the zip field and
// extracts a few fields and generates a "conversion" value using a simple
// logistic regression model

function transform(input) {
    r = {
        latitude: input.zip.latitude,
        longitude: input.zip.longitude
    };

    var x1 = distance(r, 34, -120);
    var x2 = distance(r, 28, -84);
    var z = Math.min(x1 / 5, x2 / 5);

    var z1 = 3 * (z - 2) - 2;

    r.p = logistic(z1);
    r.conversion = (Math.random() < r.p) ? "T" : "F";

    return r;
}

function distance(r, y, x) {
    dx = r.longitude - x;
    dy = r.latitude - y;
    return (Math.sqrt(dx * dx + dy * dy))
}

function logistic(x) {
    if (x < -5) {
        return 0;
    } else if (x > 5) {
        return 1;
    } else {
        return 1 / (1 + Math.exp(-x));
    }
}


