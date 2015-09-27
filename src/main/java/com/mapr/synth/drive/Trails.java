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

package com.mapr.synth.drive;

import com.tdunning.math.stats.AVLTreeDigest;
import com.tdunning.math.stats.TDigest;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import processing.core.PApplet;
import processing.core.PFont;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by tdunning on 9/21/15.
 */
public class Trails extends PApplet {
    Queue<State> input;
    private State old = null;
    private TDigest speedDistribution;
    private Random noise;
    private Stripchart speed;
    private Stripchart throttle;
    private Stripchart rpm;
    private int clicks;

    @Override
    public void setup() {
        ExecutorService pool = Executors.newFixedThreadPool(1);
        BlockingQueue<State> q = new ArrayBlockingQueue<>(2000);
        input = q;
        pool.submit(new Producer(q));
        speedDistribution = new AVLTreeDigest(300);
        noise = new Random();

        speed = new Stripchart(10, 430, 460, 80, 1, 0, 0, 90);
        rpm = new Stripchart(10, 520, 460, 80, 1, 0, 0, 2200);
        throttle = new Stripchart(10, 610, 460, 80, 1, 0, 0, 100);

        frameRate(15);
    }

    @Override
    public void settings() {
        super.settings();
        size(500, 700);
        clicks = 5;
    }

    boolean started = false;

    public static class Rect2D {
        private double x, y, w, h;

        public Rect2D(double x, double y, double w, double h) {
            if (w < 0) {
                x = x + w;
                w = -w;
            }
            if (h < 0) {
                y = y + h;
                h = -h;
            }
            this.x = x;
            this.y = y;
            this.h = h;
            this.w = w;
        }

        public boolean contains(double x1, double y1) {
            boolean xwise = x1 >= x && x1 < x + w;
            boolean ywise = y1 >= y && y1 < y + h;
            return xwise && ywise;
        }
    }


    private Rect2D drawText(float x, float y, String format, Object... args) {
        String s = String.format(format, args);
        float w = textWidth(s);
        float up = textAscent();
        float down = textDescent();
        float width = w * 1.1F;
        float height = (up + down) * 1.1F;
//        rect(x, y + down, width, height);

        stroke(0, 0, 0, 100);
        fill(0, 0, 0, 100);
        text(s, x, y);
        return new Rect2D(x, y + down, width, -height);
    }

    @Override
    public void draw() {
        colorMode(RGB, 256);
        if (old != null) {
            // status displays at the top
            fill(0xee + dither(5), 0xee + dither(5), 0xff + dither(5), 20);
            stroke(0, 0, 0, 100);
            textSize(20F);
            fill(0, 0, 0, 100);
            Rect2D faster = drawText(300, 30, "Faster");
            Rect2D slower = drawText(300, 60, "Slower");
            if (mousePressed) {
                System.out.printf("%d %d\n", mouseX, mouseY);
                if (faster.contains(mouseX, mouseY)) {
                    clicks++;
                    if (clicks > 60) {
                        clicks = 60;
                    }
                    System.out.printf("%d\n", clicks);
                } else if (slower.contains(mouseX, mouseY)) {
                    clicks--;
                    if (clicks < 0) {
                        clicks = 0;
                    }
                    System.out.printf("%d\n", clicks);
                }
            }

//            fill(0xee + dither(5), 0xee + dither(5), 0xff + dither(5), 60);
//            drawText(250, 50, "%8.1f %8.1f", old.here.getX(), old.here.getY());
//            fill(0xee + dither(5), 0xee + dither(5), 0xff + dither(5), 60);
//            drawText(50, 50, "%8.0f %8.1f", old.car.getRpm(), old.car.getSpeed() / Constants.MPH);

            speed.display();
            rpm.display();
            throttle.display();
        }

        if (clicks > 0) {
            //Fade everything which is drawn
            if (frameCount % 10 == 0) {
//                noStroke();
//            fill(0xee + dither(5), 0xee + dither(5), 0xfe + dither(5), 3);
                colorMode(HSB, 100);
                stroke(0, 0, 0, 100);
                fill(0F, 0F, 120 + dither(11), 6F);
                rect(0, 90, width, height - 380);
            }

            translate(50, 390);
            scale(-30F, -30F);
            started = true;

            colorMode(HSB, 100);

            double meanSpeed = 0;

            noStroke();
            fill(25, 100, 80);
            ellipse(0, 0, 0.3F, 0.3F);

            fill(0, 100, 80);
            ellipse(-12, 7, 0.3F, 0.3F);

            for (int i = 0; !input.isEmpty() && i < clicks; i++) {
                State state = input.remove();
                Vector3D p = state.here;
                if (old != null) {
                    stroke(0, 0, 0);
                    strokeWeight(.1F);
                    double stepSize = old.here.subtract(p).getNorm();
                    if (stepSize < 10) {
                        meanSpeed += (old.car.getSpeed() - meanSpeed) * 0.4;
                        speedDistribution.add(meanSpeed);
//                        double hue = speedDistribution.cdf(old.car.getSpeed());
                        double hue = 100 * Math.pow(old.car.getSpeed() / Constants.MPH, 2) / Math.pow(100, 2);
                        stroke((float) hue, 70, 80);
                        line((float) old.here.getX(), (float) old.here.getY(), (float) p.getX(), (float) p.getY());
                    }
                    speed.addData((float) (old.car.getSpeed() / Constants.MPH));
                    rpm.addData((float) (old.car.getRpm()));
                    throttle.addData((float) old.car.getThrottle());
                }

                old = state;
            }
        }
    }

    private void fill(double c1, double c2, double c3, double alpha) {
        fill((float) c1, (float) c2, (float) c3, (float) alpha);
    }

    private double dither(double size) {
        return size * (noise.nextDouble() - noise.nextDouble());
    }

    public static void main(String[] args) {
        PApplet.main("com.mapr.synth.drive.Trails", new String[]{""});
    }


    public static class State {
        private final Engine car;
        private final Vector3D here;

        public State(Engine car, Vector3D here) {
            this.car = car;
            this.here = here;
        }
    }

    /**
     * Draws a stripchart recorder.
     */
    class Stripchart {
        int x;           // horizontal position of chart
        int y;           // vertical position of chart
        int nSamples;    // number of samples to display (affects width)
        int h;           // height of chart
        int colour;    // color of dots to plot
        int dataPos;     // where does next data point go?
        int startPos;    // where do we start plotting?
        int nPoints;     // number of points currently in the array
        int period;      // how often to draw a gray line
        double minValue;  // minimum value to display
        double maxValue;  // maximum value to display
        float[] points;  // the data points to plot

        private DecimalFormat d = new DecimalFormat("0.#");
        private String minString;  // minimum value as a string
        private String maxString;  // maximum value as a string

        private PFont legendFont = createFont("Arial", 10);
        private float prevX;    // remember previous point
        private float prevY;

        /*
          rightSpace tells how much room there is for the chart
          legend. VSPACE and HSPACE give the spacing from the border
          of the stripchart to the point plotting area.
        */
        private float rightSpace = 0;
        final static int VSPACE = 2;
        final static int HSPACE = 2;

        Stripchart(int x, int y, int nSamples, int h, int period, int c,
                   double minValue, double maxValue) {
            this.x = x;
            this.y = y;
            this.nSamples = nSamples;
            this.h = h;
            this.period = period;
            this.colour = c;
            // make sure minimum and max are in proper order
            this.minValue = Math.min(minValue, maxValue);
            this.maxValue = Math.max(minValue, maxValue);

            // and convert them to a string with minimal number of decimal places
            this.minString = d.format(minValue);
            this.maxString = d.format(maxValue);
            nPoints = 0;
            dataPos = 0;
            startPos = 0;
            points = new float[nSamples];
        }

        Stripchart(int x, int y, int w, int h, int period, int c) {
            this(x, y, w, h, period, c, h / 2.0, -h / 2.0);
        }

        /**
         * Add a data point to be plotted.
         * At this point you may be wondering why I am using an array
         * instead of an ArrayList. Although programmaticaly it may
         * be easier to add a new value to the list and remove the
         * first one, it takes much less compute time to calculate
         * a mod and keep track of where the oldest data is.
         *
         * @param value the value to plot
         */
        void addData(float value) {
            value = constrain(value, (float) minValue, (float) maxValue);
            points[dataPos] = value;
            dataPos = (dataPos + 1) % nSamples; // wrap around when array fills

    /*
     * If the array isn't full yet, add to the end of the array
     * Otherwise, the start point for plotting moves through
     * the array.
     */
            if (nPoints < nSamples) {
                nPoints++;
            } else {
                startPos = (startPos + 1) % nSamples;
            }
        }

        void display() {
            int arrayPos;
            float yPos;
            stroke(0);
            fill(255);
            pushMatrix();
            translate(x, y);
            textFont(legendFont);

            // reserve space for the max/min value legend
            rightSpace = Math.max(textWidth(minString), textWidth(maxString));
            rect(0, 0, nSamples + rightSpace + 2 * HSPACE, h + 2 * VSPACE);
            stroke(192);
            line(HSPACE, VSPACE + h / 2, nSamples + rightSpace - HSPACE, VSPACE + h / 2);
            line(nSamples + 1, VSPACE, nSamples + 1, h - VSPACE);

            // draw max and min values
            textFont(legendFont);
            fill(0);
            stroke(0);
            text(minString, nSamples + 2, h - VSPACE);
            text(maxString, nSamples + 2, VSPACE + 8);

            for (int i = 0; i < nPoints; i++) {
                arrayPos = (startPos + i) % nSamples;
                if (period > 0 && arrayPos % period == 0) {
                    stroke(192);
                    line(nSamples - nPoints + i, VSPACE, nSamples - nPoints + i, h - VSPACE);
                }
                stroke(colour);
                yPos = (float) (VSPACE + h * (1.0 - (points[arrayPos] - minValue) / (maxValue - minValue)));

                // Draw a point for the first item, then connect all the other points with lines
                if (i == 0) {
                    point(nSamples - nPoints + i, yPos);
                } else {
                    line(prevX, prevY, nSamples - nPoints + i, yPos);
                }
                prevX = nSamples - nPoints + i;
                prevY = yPos;
            }
            popMatrix();
        }

        /**
         * Add a data value and re-display the strip chart.
         * The addData() and display() methods are decoupled;
         * this lets you "speed up" the chart by adding
         * several points before displaying the chart.
         * This method is a convenience method that does
         * both actions.
         *
         * @param value the value to add and display
         */
        void plot(float value) {
            addData(value);
            display();
        }
    }

}
