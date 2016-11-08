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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Much like SimpleDateFormat, but only for output. The fancy bit is that
 * we support single character formats Q and s for time since epoch in
 * milli-seconds and seconds respectively.
 */
public class FancyTimeFormatter {
    private String format = null;
    private SimpleDateFormat formatter = null;

    public FancyTimeFormatter(String format) {
        switch (format) {
            case "Q":
            case "s":
                this.format = "%t" + format;
                break;
            default:
                this.format = format;
                formatter = new SimpleDateFormat(format);
        }
    }

    @SuppressWarnings("unused")
    public String format(long t) {
        if (formatter != null) {
            return formatter.format(new Date(t));
        } else if (format != null) {
            return String.format(format, t);
        } else {
            throw new IllegalArgumentException("No default format for FancyTimeFormatter");
        }
    }

    public String format(Date t) {
        if (formatter != null) {
            return formatter.format(t);
        } else if (format != null) {
            return String.format(format, t);
        } else {
            throw new IllegalArgumentException("No default format for FancyTimeFormatter");
        }
    }

    public Date parse(String start) throws ParseException {
        if (formatter == null) {
            switch (format) {
                case "%ts":
                    return new Date(Long.parseLong(start) * 1000);
                case "%tQ":
                    return new Date(Long.parseLong(start));
                default:
                    throw new IllegalArgumentException("Can't parse date string if format is undefined");
            }
        } else {
            return formatter.parse(start);
        }
    }
}
