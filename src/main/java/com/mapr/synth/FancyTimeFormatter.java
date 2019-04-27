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

import com.google.common.collect.Lists;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Much like SimpleDateFormat, but fancier. The fancy bit is that
 * we support single character formats Q and s for time since epoch in
 * milli-seconds and seconds respectively. Also, multiple formats
 * can be specified to allow alternative parsing formats.
 */
public class FancyTimeFormatter {
    private static String[] defaultFormats = {"yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"};

    private List<String> formats = Lists.newArrayList();
    private List<SimpleDateFormat> formatter = Lists.newArrayList();

    public FancyTimeFormatter() {
        this(defaultFormats);
    }

    public FancyTimeFormatter(String format) {
        addFormatter(format);
    }

    @SuppressWarnings("WeakerAccess")
    public FancyTimeFormatter(String... formats) {
        for (String format : formats) {
            addFormatter(format);
        }
    }

    private void addFormatter(String format) {
        switch (format) {
            case "Q":
            case "s":
                this.formats.add("%t" + format);
                formatter.add(null);
                break;
            default:
                this.formats.add(format);
                formatter.add(new SimpleDateFormat(format));
        }
    }

    @SuppressWarnings("unused")
    public String format(long t) {
        if (formatter.get(0) != null) {
            return formatter.get(0).format(new Date(t));
        } else {
            return String.format(formats.get(0), t);
        }
    }

    public String format(Date t) {
        if (formatter.get(0) != null) {
            return formatter.get(0).format(t);
        } else {
            return String.format(formats.get(0), t);
        }
    }

    public Date parse(String t) throws ParseException {
        int i = 0;
        for (SimpleDateFormat format : formatter) {
            if (format == null) {
                String f = formats.get(i);
                assert f != null;
                try {
                    if ("%tQ".equals(f)) {
                        return new Date(Long.parseLong(t));
                    } else if ("%ts".equals(f)) {
                        return new Date(Long.parseLong(t) * 1000);
                    } else {
                        throw new ParseException(String.format("Invalid internal format %s", f), i);
                    }
                } catch (NumberFormatException e) {
                    throw new ParseException(String.format("Invalid number: %s", t), 0);
                }
            } else {
                try {
                    return format.parse(t);
                } catch (ParseException e) {
                    // ignore parse exceptions
                }
            }
        }
        throw new ParseException(String.format("Cannot parse %s as any of %s", t, formats), 0);
    }
}
