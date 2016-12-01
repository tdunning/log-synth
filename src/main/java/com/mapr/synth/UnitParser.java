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

package com.mapr.synth;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handy routines for parsing strings like "5 min", "3 mph" or "10/day"
 */
public class UnitParser {
    private static final double INCH = 2.54e-2;
    private static final double FOOT = 12 * INCH;
    private static final double MILE = 5280 * FOOT;
    private static final double HOUR = TimeUnit.HOURS.toSeconds(1);

    public static final String TIME = "(([smhd])|sec|second|m|min|minute|h|hour|d|day)";
    private static final Pattern ratePattern = Pattern.compile("([0-9.e\\-]+)\\s*/\\s*" + TIME);
    private static final Pattern timePattern = Pattern.compile("([0-9.e\\-]+)\\s*" + TIME + "?");
    private static final Pattern speedPattern = Pattern.compile("([0-9.e\\-]+)\\s*((mph)|(kph)|(m/s))?");
    private static final Pattern distancePattern = Pattern.compile("([0-9.e\\-]+)\\s*((m)|(km)|(mile))?");

    private static final Map<String, Double> timeUnitMap = ImmutableMap.<String, Double>builder()
            .put("s", (double) TimeUnit.SECONDS.toSeconds(1L))
            .put("sec", (double) TimeUnit.SECONDS.toSeconds(1L))
            .put("second", (double) TimeUnit.SECONDS.toSeconds(1L))
            .put("seconds", (double) TimeUnit.SECONDS.toSeconds(1L))
            .put("m", (double) TimeUnit.MINUTES.toSeconds(1L))
            .put("min", (double) TimeUnit.MINUTES.toSeconds(1L))
            .put("minute", (double) TimeUnit.MINUTES.toSeconds(1L))
            .put("minutes", (double) TimeUnit.MINUTES.toSeconds(1L))
            .put("h", (double) TimeUnit.HOURS.toSeconds(1L))
            .put("hour", (double) TimeUnit.HOURS.toSeconds(1L))
            .put("hours", (double) TimeUnit.HOURS.toSeconds(1L))
            .put("d", (double) TimeUnit.DAYS.toSeconds(1L))
            .put("day", (double) TimeUnit.DAYS.toSeconds(1L))
            .put("days", (double) TimeUnit.DAYS.toSeconds(1L))
            .build();

    private static final Map<String, Double> distanceMap = ImmutableMap.of(
            "km", 1000.0,
            "mile", MILE,
            "miles", MILE,
            "m", 1.0);

    private static final Map<String, Double> speedMap = ImmutableMap.of(
            "mph", MILE / HOUR,
            "kph", 1000 / HOUR,
            "m/s", 1.0);


    private static double unitParse(String value, Pattern pattern, Map<String, Double> translation, String errorFormat, boolean invertUnit) {
        Matcher m = pattern.matcher(value.trim());
        if (m.matches()) {
            // group(1) is the number, group(2) is either empty or mph, /day or some such.
            double unit = 1;
            if (m.groupCount() > 1) {
                String unitString = m.group(2).trim().replaceAll("\\s", "");
                if (translation.containsKey(unitString)) {
                    unit = translation.get(unitString);
                } else {
                    // can't happen because we have matched the pattern already
                    throw new InternalError(String.format("Inconsistent pattern and table for \"%s\", %s vs %s", value, pattern, translation.keySet()));
                }
            }
            double raw = Double.parseDouble(m.group(1).trim());
            if (invertUnit) {
                return raw / unit;
            } else {
                return raw * unit;
            }
        } else {
            throw new IllegalArgumentException(String.format(errorFormat, value));
        }
    }

    public static double parseSpeed(String speed) {
        return unitParse(speed, speedPattern, speedMap, "Invalid speed argument: %s", false);
    }

    public static double parseDistance(String distance) {
        return unitParse(distance, distancePattern, distanceMap, "Invalid location argument: %s", false);
    }

    public static double parseTime(String s) {
        return unitParse(s, timePattern, timeUnitMap, "Invalid time expression: %s", false);
    }

    public static double parseRate(String rate) {
        return unitParse(rate, ratePattern, timeUnitMap, "Invalid rate argument: %s", true);
    }

    public static double parseDistanceUnit(String unit) {
        unit = unit.trim();
        if (distanceMap.containsKey(unit)) {
            return UnitParser.distanceMap.get(unit);
        } else {
            throw new IllegalArgumentException("Bad unit");
        }
    }
}
