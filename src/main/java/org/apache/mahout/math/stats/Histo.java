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
import com.google.common.collect.Lists;
import org.apache.mahout.common.RandomUtils;

import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adaptive histogram based on something like streaming k-means.
 */
public class Histo {
    private double compression = 1000;
    private GroupTree summary = new GroupTree();
    private int count = 0;
    private boolean recordAllData = false;

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
        // note that because of a zero id, this will be sorted *before* any existing Group with the same mean
        Group base = createGroup(x, 0);
        Group start = summary.floor(base);
        if (start == null) {
            start = summary.ceiling(base);
        }

        if (start == null) {
            summary.add(createGroup(x));
            count = 1;
        } else {
            Iterable<Group> neighbors = summary.tailSet(start);
            double minDistance = Double.MAX_VALUE;
            int lastNeighbor = 0;
            int i = summary.headCount(start);
            for (Group neighbor : neighbors) {
                double z = Math.abs(neighbor.mean() - x);
                if (z <= minDistance) {
                    minDistance = z;
                    lastNeighbor = i;
                } else {
                    break;
                }
                i++;
            }

            Group closest = null;
            int sum = summary.headSum(start);
            i = summary.headCount(start);
            double n = 1;
            for (Group neighbor : neighbors) {
                if (i > lastNeighbor) {
                    break;
                }
                double z = Math.abs(neighbor.mean() - x);
                double q = (sum + neighbor.count() / 2.0) / count;
                double k = 4 * count * q * (1 - q) / compression;

                // this slightly clever selection method improves accuracy with lots of repeated points
                if (z == minDistance && neighbor.count() <= k) {
                    if (gen.nextDouble() < 1 / n) {
                        closest = neighbor;
                    }
                    n++;
                }
                sum += neighbor.count();
                i++;
            }

            if (closest == null) {
                summary.add(createGroup(x));
            } else {
                summary.remove(closest);
                closest.add(x);
                summary.add(closest);
            }
            count++;
        }
    }

    private Group createGroup(double mean, int id) {
        return new Group(mean, id, recordAllData);
    }

    private Group createGroup(double mean) {
        return new Group(mean, recordAllData);
    }

    private Random gen = RandomUtils.getRandom();

    public int size() {
        return count;
    }

    public double cdf(double x) {
        GroupTree values = summary;
        if (values.size() == 0) {
            return Double.NaN;
        } else if (values.size() == 1) {
            return x < values.first().mean() ? 0 : 1;
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
                    return (r + a.count() * interpolate(x, a.mean() - left, a.mean() + right)) / count;
                }
                r += a.count();

                a = b;
                b = it.next();

                left = right;
                right = (b.mean() - a.mean()) / 2;
            }

            // for the last element, assume right width is same as left
            left = right;
            a = b;
            if (x < a.mean() + right) {
                return (r + a.count() * interpolate(x, a.mean() - left, a.mean() + right)) / count;
            } else {
                return 1;
            }
        }
    }

    private double interpolate(double x, double x0, double x1) {
        return (x - x0) / (x1 - x0);
    }

    public double quantile(double q) {
        GroupTree values = summary;
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

            if (q <= a.count()) {
                return a.mean() + left * (2 * q - a.count()) / a.count();
            } else {
                double t = a.count();
                while (it.hasNext()) {
                    if (t + b.count() / 2 >= q) {
                        // left of b
                        return b.mean() - left * 2 * (q - t) / b.count();
                    } else if (t + b.count() >= q) {
                        // right of b but left of the left-most thing beyond
                        return b.mean() + right * 2 * (q - t - b.count() / 2.0) / b.count();
                    }
                    t += b.count();

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

    public Iterable<? extends Group> centroids() {
        return summary;
    }

    public double compression() {
        return compression;
    }

    public void recordAllData() {
        recordAllData = true;
    }

    public static class Group implements Comparable<Group> {
        private static final AtomicInteger uniqueCount = new AtomicInteger(1);

        private double centroid = 0;
        private int count = 0;
        private int id;

        private List<Double> actualData = null;

        private Group(boolean record) {
            if (record) {
                actualData = Lists.newArrayList();
            }
        }

        public Group(double x) {
            this(false);
            start(x, uniqueCount.getAndIncrement());
        }

        public Group(double x, int id) {
            this(false);
            start(x, id);
        }

        public Group(double x, boolean record) {
            this(record);
            start(x, uniqueCount.getAndIncrement());
        }

        public Group(double x, int id, boolean record) {
            this(record);
            start(x, id);
        }

        private void start(double x, int id) {
            this.id = id;
            add(x);
        }

        public void add(double x) {
            if (actualData != null) {
                actualData.add(x);
            }
            count++;
            double delta = x - centroid;
            centroid += delta / count;
        }

        public double mean() {
            return centroid;
        }

        public int count() {
            return count;
        }

        public int id() {
            return id;
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

        public Iterable<? extends Double> data() {
            return actualData;
        }
    }

}
