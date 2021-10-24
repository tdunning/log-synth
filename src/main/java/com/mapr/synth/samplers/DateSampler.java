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

package com.mapr.synth.samplers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.mapr.synth.FancyTimeFormatter;
import org.apache.mahout.common.RandomUtils;
import org.apache.mahout.math.jet.random.AbstractContinousDistribution;
import org.apache.mahout.math.jet.random.Exponential;
import org.apache.mahout.math.jet.random.Uniform;

import java.text.ParseException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.concurrent.TimeUnit;

/**
 * Sample dates that are all before a fixed epoch.  On average, the generated dates
 * should be 100 days before the epoch, but some will be closer and some much earlier.
 *
 * If you set the end point, this will change the epoch.  If you set the start point the
 * dates will be selected uniformly between start and end.  Start and end can be specified
 * as dates in yyyy-MM-dd default format or whatever format is specified with the format
 * option (note that options are parsed in order).
 *
 * Thread safe
 */
public class DateSampler extends FieldSampler implements ComparableField{
    private static final long EPOCH = new GregorianCalendar(2021, Calendar.AUGUST, 1).getTimeInMillis();
    private long start = 0;
    private long end = EPOCH;

    private FancyTimeFormatter df = new FancyTimeFormatter("yyyy-MM-dd");
    private AbstractContinousDistribution base =
            new Exponential(1.0 / TimeUnit.MILLISECONDS.convert(100, TimeUnit.DAYS), RandomUtils.getRandom());

    public DateSampler() {
    }

    @SuppressWarnings("unused")
    public void setFormat(String format) {
        df = new FancyTimeFormatter(format);
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setStart(String start) throws ParseException {
        this.start = df.parse(start).getTime();
        base = new Uniform(0, this.end - this.start, RandomUtils.getRandom());
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setEnd(String end) throws ParseException {
        this.end = df.parse(end).getTime();
        base = new Uniform(0, this.end - this.start, RandomUtils.getRandom());
    }
    
    public FancyTimeFormatter getFormat() {
    	return df;
    }

    public long getStart() {
		return start;
	}

	public void setStartL(long start) {
		this.start = start;
		base = new Uniform(0, this.end - this.start, RandomUtils.getRandom());
	}

	public long getEnd() {
		return end;
	}

	public void setEndL(long end) {
		this.end = end;
        base = new Uniform(0, this.end - this.start, RandomUtils.getRandom());
	}

	@Override
    public JsonNode doSample() {
      synchronized (this) {
        long t = (long) Math.rint(base.nextDouble());
        return new TextNode(df.format(new java.util.Date(end - t)));
      }
    }

	@Override
	public String getMaxAsString() {
		return df.format(new java.util.Date(end));
	}

	@Override
	public String getMinAsString() {
		return df.format(new java.util.Date(start));
	}

	@Override
	public int compareTo(String c) {
		try {
			System.out.println("doff " + (df.parse(lastSampled.asText()).getTime() - df.parse(c).getTime()));
			long l = (df.parse(lastSampled.asText()).getTime() - df.parse(c).getTime());
			if(l > 0) return 1;
			if(l == 0) return 0;
			if(l < 0) return -1;
 		} catch (NumberFormatException | ParseException e) {
			e.printStackTrace();
		}
		return 0;
	}

	@Override
	public void setMaxAsString(String c, boolean plusOne) {
		try {
			setEnd(c);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		
	}

	@Override
	public void setMinAsString(String c, boolean plusOne) {
		try {
			System.out.println("old start " + start);
			setStart(c);
			System.out.println("new start " + start);
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	@Override
	public String getLastSampledAsString() {
		return lastSampled.asText();
	}
}
