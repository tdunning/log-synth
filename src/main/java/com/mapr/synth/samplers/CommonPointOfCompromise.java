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
import com.fasterxml.jackson.databind.node.*;
import com.mapr.synth.distributions.ChineseRestaurant;
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
 * <p>
 * Users also visit ATM's.  For a period of time, one ATM is "compromised" and
 * all users who visit that ATM during that time will be switched to the
 * vulnerable state.  During the vulnerable period, transactions have a higher
 * probability of being marked as "possible fraud".  The vulnerable period starts
 * a short period of time after the compromise and extends a short period after
 * that.  Transactions at a compromised ATM are marked.
 * <p>
 * There is no correlation between the content of the transactions and the fraud flag.
 * <p>
 * A transaction has the following fields:
 * <p>
 * <ul>
 * <li><em>merchant</em>merchant ID, 0 is ATM</li>
 * <li><em>date</em>date and time in YYYY-MM-dd HH:mm:ss format</li>
 * <li><em>timestamp</em>unix timestamp</li>
 * <li><em>compromise</em>1 if this transaction is at a compromised ATM</li>
 * <li><em>fraud</em>fraud flag, 1 for suspected fraud, 0 otherwise</li>
 * </ul>
 */
public class CommonPointOfCompromise extends FieldSampler {
    Random gen = new Random();

    // how many average transactions per day?
    Gamma transactionsPerDay = new Gamma(2, 1, gen);

    ChineseRestaurant merchant = new ChineseRestaurant(100, 0.3);

    DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


    private JsonNodeFactory nodeFactory = JsonNodeFactory.withExactBigDecimals(false);
    private long end;
    private long start;
    private long compromiseStart;
    private long compromiseEnd;
    private long exploitStart;
    private long exploitEnd;
    private double compromisedFraudRate;
    private double uncompromisedFraudRate;

    public CommonPointOfCompromise() throws ParseException {
        start = df.parse("2014-01-01 00:00:00").getTime();
        end = df.parse("2014-02-15 00:00:00").getTime();

        compromiseStart = df.parse("2014-01-15 00:00:00").getTime();
        compromiseEnd = df.parse("2014-01-18 00:00:00").getTime();

        exploitStart = df.parse("2014-01-20 00:00:00").getTime();
        exploitEnd = df.parse("2014-01-31 00:00:00").getTime();

        compromisedFraudRate = 0.3;
        uncompromisedFraudRate = 0.001;
    }

    public void setSeed(long seed) {
        gen.setSeed(seed);
        merchant.setSeed(seed);
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setEnd(String end) throws ParseException {
        this.end = df.parse(end).getTime();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setStart(String start) throws ParseException {
        this.start = df.parse(start).getTime();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setCompromiseStart(String start) throws ParseException {
        this.compromiseStart = df.parse(start).getTime();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setCompromiseEnd(String end) throws ParseException {
        this.compromiseEnd = df.parse(end).getTime();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setExploitStart(String exploitStart) throws ParseException {
        this.exploitStart = df.parse(exploitStart).getTime();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setExploitEnd(String exploitEnd) throws ParseException {
        this.exploitEnd = df.parse(exploitEnd).getTime();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setCompromisedFraudRate(double compromisedFraudRate) {
        this.compromisedFraudRate = compromisedFraudRate;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setUncompromisedFraudRate(double uncompromisedFraudRate) {
        this.uncompromisedFraudRate = uncompromisedFraudRate;
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
                pFraud = compromisedFraudRate;
            } else {
                pFraud = uncompromisedFraudRate;
            }

            transaction.set("fraud", new IntNode((gen.nextDouble() < pFraud) ? 1 : 0));

            r.add(transaction);
        }
        return r;
    }
}
