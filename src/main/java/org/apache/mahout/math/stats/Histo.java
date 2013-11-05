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

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adaptive histogram based on something like streaming k-means.
 */
public class Histo {
    private double compression = 1000;
    private NavigableSet<Group> summary = Sets.newTreeSet();
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
     *
     * @param x The value to add.
     */
    public void add(double x) {
        Group base = new Group(x, 0);
        Group before = summary.floor(base);
        Group after = summary.ceiling(base);

        Group closest;
        double q;
        if (before == null) {
            if (after == null) {
                summary.add(new Group(x));
                count = 1;
                return;
            } else {
                closest = smallestGroup(summary, after);
                q = 0;
            }
        } else {
            if (after == null) {
                closest = smallestGroup(summary, before);
                q = 1;
            } else {
                double beforeGap = x - before.mean();
                double afterGap = after.mean() - x;
                if (beforeGap < afterGap) {
                    closest = smallestGroup(summary, before);
                } else {
                    closest = smallestGroup(summary, after);
                }
                // this tells us how many buckets are earlier
                q = (double) summary.headSet(base).size() / summary.size();
                // and this converts to a true quantile assuming that the buckets have the correct distribution
                // The tricky bit is that we then use this to enforce the correct distribution
                q = q * q * (3 - 2 * q);
            }
        }
        // the use of q here makes the threshold for addition small at each end.  This
        // helps keep the relative error very small at the cost of increasing the number
        // of centroids by a small integer factor
        if (closest.count > count * 4 * q * (1 - q) / compression) {
            summary.add(new Group(x));
            count++;
        } else {
            summary.remove(closest);
            closest.add(x);
            summary.add(closest);
            count++;
        }
    }

    private Group smallestGroup(SortedSet<Group> groups, Group base) {
        groups = groups.tailSet(base);
        int minSize = Integer.MAX_VALUE;
        Group closest = null;
        for (Group group : groups) {
            if (group.mean() != base.mean()) {
                break;
            }
            if (group.size() < minSize) {
                minSize = group.size();
                closest = group;
            }
        }
        return closest;
    }

    public int size() {
        return count;
    }

    public double cdf(double x) {
        Collection<Group> values = summary;
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

    public double quantile(double q) {
        Collection<Group> values = summary;
        Preconditions.checkArgument(values.size() > 1);

        Iterator<Group> it = values.iterator();
        Group a = it.next();
        Group b = it.next();
        if (!it.hasNext()) {
            // both a and b have to have just a single element
            double diff = (b.mean() - a.mean()) / 2;
            if (q > 0.75) {
                return b.mean() + diff * (4 * q - 3);
            } else {
                return a.mean() + diff * (4 * q - 1);
            }
        } else {
            q *= count;
            double right = (b.mean() - a.mean()) / 2;
            // we have nothing else to go on so make left hanging width same as right to start
            double left = right;

            if (q <= a.size()) {
                return a.mean() + left * (2 * q - a.size()) / a.size();
            } else {
                double t = a.size();
                while (it.hasNext()) {
                    if (t + b.size() / 2 >= q) {
                        // left of b
                        return b.mean() - left * 2 * (q - t) / b.size();
                    } else if (t + b.size() >= q) {
                        // right of b but left of the left-most thing beyond
                        return b.mean() + right * 2 * (q - t - b.size() / 2.0) / b.size();
                    }
                    t += b.size();

                    a = b;
                    b = it.next();
                    left = right;
                    right = (b.mean() - a.mean()) / 2;
                }
                // shouldn't be possible but we have an answer anyway
                return b.mean() + right;
            }
        }
    }

    public int centroidCount() {
        return summary.size();
    }


    public static class Group implements Comparable<Group> {
        private static final AtomicInteger uniqueCount = new AtomicInteger(0);

        private double centroid;
        private int count;
        private int id;

        public Group(double x) {
            centroid = x;
            count = 1;
            id = uniqueCount.getAndIncrement();
        }

        public Group(double x, int id) {
            this(x);
            this.id = id;
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
        public int hashCode() {
            return id;
        }

        @Override
        public int compareTo(Group o) {
            int r = Double.compare(centroid, o.centroid);
            if (r == 0) {
                r = id - o.id;
            }
            return r;
        }
    }
}
