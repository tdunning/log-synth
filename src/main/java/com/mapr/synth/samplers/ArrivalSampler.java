package com.mapr.synth.samplers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.ImmutableMap;
import org.apache.mahout.common.RandomUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Samples progressive times that look like event arrival times.
 *
 * You can set the
 *
 * <il>
 * <li><em>rate</em> - use something like 5/m to indicate 5 events per minute.  The unit is optional, seconds are the default.</li>
 * <li><em>offset</em> - the minimum time between events, default is 0</li>
 * <li><em>format </em>- the format to use when outputting the times</li>
 * <li><em>start </em>- the time of the first event</li>
 * </il>
 */
public class ArrivalSampler extends FieldSampler {
    private final Random base;
    private final Pattern ratePattern = Pattern.compile("([0-9.e\\-]+)(/[smhd])?");
    private final Map<String, TimeUnit> unitMap = ImmutableMap.of(
            "s", TimeUnit.SECONDS,
            "m", TimeUnit.MINUTES,
            "h", TimeUnit.HOURS,
            "d", TimeUnit.DAYS);

    private double meanInterval = 1000;  // interval - offset will have this mean
    private double minInterval = 0;      // no interval can be less than this
    private SimpleDateFormat df;

    private double start = System.currentTimeMillis();

    public ArrivalSampler() {
        base = RandomUtils.getRandom();
    }

    public void setRate(String rate) {
        Matcher m = ratePattern.matcher(rate);
        if (m.matches()) {
            // group(1) is the number, group(2) is either empty (default to /s) or /d or some such.
            TimeUnit sourceUnit = (m.groupCount() > 1) ? unitMap.get(m.group(2).substring(1)) : TimeUnit.SECONDS;
            double count = Double.parseDouble(m.group(1));
            this.meanInterval = TimeUnit.MILLISECONDS.convert(1, sourceUnit) / count;
        }
    }

    public void setOffset(double offset) {
        minInterval = offset;
    }

    public void setFormat(String format) {
        df = new SimpleDateFormat(format);
    }

    public void setStart(String start) throws ParseException {
        this.start = df.parse(start).getTime();
    }

    @Override
    public JsonNode sample() {
        TextNode r = new TextNode(df.format(new Date((long) start)));
        start += minInterval - meanInterval * Math.log(1 - base.nextDouble());
        return r;
    }
}
