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

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handy static routines
 */
public class Util {
    private static final Pattern ratePattern = Pattern.compile("([0-9.e\\-]+)(/[smhd])?");
    private static final Map<String, TimeUnit> unitMap = ImmutableMap.of(
            "s", TimeUnit.SECONDS,
            "m", TimeUnit.MINUTES,
            "h", TimeUnit.HOURS,
            "d", TimeUnit.DAYS);

    public static Integer parseInteger(String argument) {
        int n = Integer.parseInt(argument.replaceAll("[KMG]?$", ""));

        switch (argument.charAt(argument.length() - 1)) {
            case 'G':
                n *= 1e9;
                break;
            case 'M':
                n *= 1e6;
                break;
            case 'K':
                n *= 1e3;
                break;
            default:
                // no suffix leads here
                break;
        }
        return n;
    }

    public static int parseInteger(JsonNode max) {
        if (max.isTextual()) {
            return parseInteger(max.asText());
        } else if (max.isInt()) {
            return max.asInt();
        } else {
            throw new IllegalArgumentException("Needed an integer or a string defining an integer");
        }
    }

    public static double parseRateAsInterval(String rate) {
        Matcher m = ratePattern.matcher(rate);
        if (m.matches()) {
            // group(1) is the number, group(2) is either empty (default to /s) or /d or some such.
            TimeUnit sourceUnit = (m.groupCount() > 1) ? unitMap.get(m.group(2).substring(1)) : TimeUnit.SECONDS;
            double count = Double.parseDouble(m.group(1));
            return TimeUnit.MILLISECONDS.convert(1, sourceUnit) / count;
        } else {
            throw new IllegalArgumentException(String.format("Invalid rate argument: %s", rate));
        }
    }
}
