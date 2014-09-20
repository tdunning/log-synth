package com.mapr.synth.samplers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.mapr.synth.distributions.ChineseRestaurant;
import org.apache.mahout.common.RandomUtils;
import org.apache.mahout.math.jet.random.Exponential;
import org.apache.mahout.math.jet.random.Gamma;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * In this model, users do some number of transactions, the rate for which is
 * selected from a Gamma distribution.  The merchants for the transactions
 * are chosen from a long-tailed distribution so there are a few very common
 * merchants and a few less common ones.  All users generate events for the
 * entire period of data generation.
 * <p/>
 * Users also visit ATM's.  For a period of time, one ATM is "compromised" and
 * all users who visit that ATM during that time will be switched to the
 * vulnerable state.  During the vulnerable period, transactions have a higher
 * probability of being marked as "possible fraud".  The vulnerable period starts
 * a short period of time after the compromise and extends a short period after
 * that.  Transactions at a compromised ATM are marked.
 * <p/>
 * There is no correlation between the content of the transactions and the fraud flag.
 * <p/>
 * A transaction has the following fields:
 * <p/>
 * <ul>
 * <li><em>merchant</em>merchant ID, 0 is ATM</li>
 * <li><em>date</em>date and time in YYYY-MM-dd HH:mm:ss format</li>
 * <li><em>timestamp</em>unix timestamp</li>
 * <li><em>compromise</em>1 if this transaction is at a compromised ATM</li>
 * <li><em>fraud</em>fraud flag, 1 for suspected fraud, 0 otherwise</li>
 * </ul>
 */
public class CommonPointOfCompromise extends FieldSampler {
    Random gen = RandomUtils.getRandom();

    // how many average transactions per day?
    Gamma transactionsPerDay = new Gamma(2, 1, gen);

    ChineseRestaurant merchant = new ChineseRestaurant(100, 0.3);

    DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


    private JsonNodeFactory nodeFactory = JsonNodeFactory.withExactBigDecimals(false);
    private final long end;
    private final long start;
    private final long compromiseStart;
    private final long compromiseEnd;
    private final long exploitStart;
    private final long exploitEnd;

    public CommonPointOfCompromise() throws ParseException {
        start = df.parse("2014-01-01 00:00:00").getTime();
        end = df.parse("2014-02-15 00:00:00").getTime();

        compromiseStart = df.parse("2014-01-15 00:00:00").getTime();
        compromiseEnd = df.parse("2014-01-18 00:00:00").getTime();

        exploitStart = df.parse("2014-01-20 00:00:00").getTime();
        exploitEnd = df.parse("2014-01-31 00:00:00").getTime();
    }

    public void setSeed(long seed) {
        gen.setSeed(seed);
        merchant.setSeed(seed);
    }

    @Override
    public JsonNode sample() {
        ArrayNode r = nodeFactory.arrayNode();

        double t = start;
        double averageInterval = TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS) / transactionsPerDay.nextDouble();
        Exponential interval = new Exponential(1 / averageInterval, gen);

        Date date = new Date();
        boolean compromised = false;
        while (t < end) {
            ObjectNode transaction = new ObjectNode(nodeFactory);
            t += interval.nextDouble();
            date.setTime((long) t);
            transaction.set("timestamp", new LongNode((long) (t / 1000)));
            transaction.set("date", new TextNode(df.format(date)));
            Integer merchantId = merchant.sample();
            transaction.set("merchant", new IntNode(merchantId));

            if (merchantId == 0 && t >= compromiseStart && t < compromiseEnd) {
                compromised = true;
                transaction.set("compromise", new IntNode(1));
            } else {
                transaction.set("compromise", new IntNode(0));
            }

            if (t > exploitEnd) {
                compromised = false;
            }

            double pFraud;
            if (t >= exploitStart && compromised) {
                pFraud = 0.3;
            } else {
                pFraud = 0.001;
            }

            transaction.set("fraud", new IntNode((gen.nextDouble() < pFraud) ? 1 : 0));

            r.add(transaction);
        }
        return r;
    }
}
