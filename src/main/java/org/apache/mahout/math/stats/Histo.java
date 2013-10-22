/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.mahout.math.stats;

import com.google.common.collect.Maps;

import java.util.Collection;
import java.util.Iterator;
import java.util.SortedMap;

/**
 * Adaptive histogram based on something like streaming k-means.
 */
public class Histo {
    private double compression = 1000;
    private SortedMap<Double, Group> summary = Maps.newTreeMap();
    private int count = 0;

    /**
     * A histogram structure that will record a sketch of a distribution.
     *
     * @param compression How should accuracy be traded for size?  A value of N here will give quantile errors
     *                    almost always less than 3/N with considerably smaller errors expected for extreme
     *                    quantiles.  Conversely, you should expect to track about 5 N centroids for this
     *                    accuracy.
     */
    public Histo(double compression) {
        this.compression = compression;
    }

    /**
     * Adds a sample to a histogram.
     * @param x  The value to add.
     */
    public void add(double x) {
        SortedMap<Double, Group> before = summary.headMap(x);
        SortedMap<Double, Group> after = summary.tailMap(x);

        Group closest;
        double q;
        if (before.size() == 0) {
            if (after.size() == 0) {
                summary.put(x, new Group(x));
                count = 1;
                return;
            } else {
                closest = after.get(after.firstKey());
                q = 0;
            }
        } else {
            if (after.size() == 0) {
                closest = before.get(before.firstKey());
                q = 1;
            } else {
                double beforeGap = x - before.lastKey();
                double afterGap = after.firstKey() - x;
                if (beforeGap < afterGap) {
                    closest = before.get(before.lastKey());
                } else {
                    closest = after.get(after.firstKey());
                }
                // this tells us how many buckets are earlier
                q = (double) before.size() / summary.size();
                // and this converts to a true quantile assuming that the buckets have the correct distribution
                // The tricky bit is that we then use this to enforce the correct distribution
                q = q * q * (3 - 2 * q);
            }
        }
        // the use of q here makes the threshold for addition small at each end.  This
        // helps keep the relative error very small at the cost of increasing the number
        // of centroids by a small integer factor
        if (closest.count > count * 4 * q * (1 - q) / compression) {
            summary.put(x, new Group(x));
            count++;
        } else {
            summary.remove(closest.centroid);
            closest.add(x);
            summary.put(closest.centroid, closest);
            count++;
        }
    }

    public int size() {
        return count;
    }

    public SortedMap<Double, Group> centroids() {
        return summary;
    }

    public double cdf(double x) {
        Collection<Group> values = summary.values();
        if (values.size() == 0) {
            return Double.NaN;
        } else if (values.size() == 1) {
            return x < values.iterator().next().mean() ? 0 : 1;
        } else {
            double r = 0;

            // we scan a across the centroids
            Iterator<Group> it = values.iterator();
            Group a = it.next();

            // b is the look-ahead to the next centroid
            Group b = it.next();

            // initially, we set left width equal to right width
            double left = (b.mean() - a.mean()) / 2;
            double right = left;

            // scan to next to last element
            while (it.hasNext()) {
                if (x < a.mean() + right) {
                    return (r + a.size() * interpolate(x, a.mean() - left, a.mean() + right)) / count;
                }
                r += a.size();

                a = b;
                b = it.next();

                left = right;
                right = (b.mean() - a.mean()) / 2;
            }

            // for the last element, assume right width is same as left
            left = right;
            a = b;
            if (x < a.mean() + right) {
                return (r + a.size() * interpolate(x, a.mean() - left, a.mean() + right)) / count;
            } else {
                return 1;
            }
        }
    }

    private double interpolate(double x, double x0, double x1) {
        return (x - x0) / (x1 - x0);
    }


    public static class Group implements Comparable<Group> {
        private double centroid;
        private int count;

        public Group(double x) {
            centroid = x;
            count = 1;
        }

        public void add(double x) {
            count++;
            double delta = x - centroid;
            centroid += delta / count;
        }

        public double mean() {
            return centroid;
        }

        public int size() {
            return count;
        }

        @Override
        public String toString() {
            return "Group{" +
                    "centroid=" + centroid +
                    ", count=" + count +
                    '}';
        }

        @Override
        public int compareTo(Group o) {
            int r = Double.compare(centroid, o.centroid);
            if (r == 0) {
                r = count - o.count;
            }
            return r;
        }
    }
}
