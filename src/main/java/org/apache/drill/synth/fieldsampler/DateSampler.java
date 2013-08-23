package org.apache.drill.synth.fieldsampler;

import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.concurrent.TimeUnit;

import org.apache.drill.synth.FieldSampler;
import org.apache.mahout.common.RandomUtils;
import org.apache.mahout.math.jet.random.Exponential;

/**
 * Sample dates that are all before a fixed epoch.  On average, the generated dates
 * should be 100 days before the epoch, but some will be closer and some much earlier.
 */
public class DateSampler extends FieldSampler {
    private static final long EPOCH = new GregorianCalendar(2013, 7, 1).getTimeInMillis();
    private SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
    private Exponential base = new Exponential(1.0 / TimeUnit.MILLISECONDS.convert(100, TimeUnit.DAYS), RandomUtils.getRandom());

    public DateSampler() {
    }

    public void setFormat(String format) {
        df = new SimpleDateFormat(format);
    }

    @Override
    public String sample() {
        long t = (long) Math.rint(base.nextDouble());
        return df.format(new java.util.Date(EPOCH - t));
    }
}
