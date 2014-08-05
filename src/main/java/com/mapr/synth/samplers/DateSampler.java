package com.mapr.synth.samplers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.mahout.common.RandomUtils;
import org.apache.mahout.math.jet.random.AbstractContinousDistribution;
import org.apache.mahout.math.jet.random.Exponential;
import org.apache.mahout.math.jet.random.Uniform;

import java.text.ParseException;
import java.text.SimpleDateFormat;
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
 */
public class DateSampler extends FieldSampler {
    private static final long EPOCH = new GregorianCalendar(2013, 7, 1).getTimeInMillis();
    private long start = 0;
    private long end = EPOCH;

    private SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
    private AbstractContinousDistribution base =
            new Exponential(1.0 / TimeUnit.MILLISECONDS.convert(100, TimeUnit.DAYS), RandomUtils.getRandom());

    public DateSampler() {
    }

    public void setFormat(String format) {
        df = new SimpleDateFormat(format);
    }

    public void setStart(String start) throws ParseException {
        this.start = df.parse(start).getTime();
        base = new Uniform(0, this.end - this.start, RandomUtils.getRandom());
    }

    public void setEnd(String end) throws ParseException {
        this.end = df.parse(end).getTime();
        base = new Uniform(0, this.end - this.start, RandomUtils.getRandom());
    }

    @Override
    public JsonNode sample() {
        long t = (long) Math.rint(base.nextDouble());
        return new TextNode(df.format(new java.util.Date(end - t)));
    }
}
