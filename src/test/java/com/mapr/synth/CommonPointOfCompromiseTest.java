package com.mapr.synth;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.mapr.synth.samplers.SchemaSampler;
import org.junit.Test;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class CommonPointOfCompromiseTest {

    @Test
    public void testCompromise() throws IOException, ParseException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long start = df.parse("2014-01-01 00:00:00").getTime();
        GregorianCalendar cal = new GregorianCalendar();
        SchemaSampler s = new SchemaSampler(Resources.asCharSource(Resources.getResource("schema013.json"), Charsets.UTF_8).read());

        int[] transactionsByDay = new int[100];
        int[] compromiseByDay = new int[100];
        int[] fraudByDay = new int[100];
        for (int i = 0; i < 10000; i++) {
            JsonNode sample = s.sample();
//            if (i < 10) {
//                System.out.printf("%s\n", sample);
//            }
            for (JsonNode record : sample.get("history")) {
                int day = (int) ((record.get("timestamp").asLong() * 1000 - start) / TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS));
                if (day >= 100) {
                    break;
                }
                if (record.get("compromise").asInt() > 0) {
                    compromiseByDay[day]++;
                }
                if (record.get("fraud").asInt() > 0) {
                    fraudByDay[day]++;
                }
                transactionsByDay[day]++;
            }
        }
        System.out.printf("day\tcompromises\tfrauds\ttransactions\n");

        for (int i = 0; i < compromiseByDay.length; i++) {
            System.out.printf("%d\t%d\t%d\t%d\n", i, compromiseByDay[i], fraudByDay[i], transactionsByDay[i]);
        }
    }
}