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

package com.mapr.stats;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Retains top samples from a stream by using a heap data structure.
 */
public class UpperQuantile {
    private static Logger log = LoggerFactory.getLogger(UpperQuantile.class);

    private int n = 0;
    private Heap biggest;
    private double[] sorted;

    public UpperQuantile(int maxRetained) {
        biggest = new Heap(maxRetained);
    }

    void add(double x) {
        sorted = null;
        biggest.add(x);
        n++;
    }

    /**
     * To find a particular quantile, we have to look at the top values in sorted order.
     *
     * @param q The quantile to estimate.
     * @return The value of the quantile.
     */
    double quantile(double q) {
        Preconditions.checkState(biggest.size() > 0, "Can't get quantile with no data");
        Preconditions.checkArgument(q >= 0, "Q must be >= 0");
        Preconditions.checkArgument(q <= 1, "Q must be <= 1");

        // how far from the max value?
        double item = (n - 1) * (1 - q);
        Preconditions.checkArgument(item <= biggest.size() - 1,
                "Can't get %.2 %-ile, only retained %d / %d items",
                100 * q, biggest.size(), n);

        // and how far is that from the beginning of our retained samples?
        item = biggest.size() - item;

        // only sort the data once
        if (sorted == null) {
            sorted = biggest.data.clone();
            Arrays.sort(sorted, 1, sorted.length);
        }

        // may want to interpolate values
        int i = (int) Math.floor(item);
        double fraction = item - i;
        if (fraction > 0) {
            return sorted[i] * (1 - fraction) + sorted[i + 1] * fraction;
        } else {
            return sorted[i];
        }
    }

    public void clear() {
        biggest.clear();
    }

    /**
     * Verify that the heap is well formed.
     */
    public void validate() {
        biggest.validate(1);
    }

    /**
     * Print the heap to standard out.
     */
    public void print() {
        biggest.print(1);
    }

    /**
     * See http://en.wikipedia.org/wiki/Heap_(data_structure)
     */
    private static final class Heap {
        private int count = 0;
        private double[] data;

        private Heap(int size) {
            Preconditions.checkArgument(size > 0);
            data = new double[size + 1];
        }

        private void add(double x) {
            if (count < data.length - 1) {
                // if we delayed initialization until the heap fills, we could go a bit faster (Floyd's algo)
                // if we often only add as many samples as are allowed in the heap, then that could make a significant
                // difference
                count++;
                data[count] = x;
                bubble(count);
            } else if (x > data[1]) {
                // x is bigger than the root, so x deserves to be in the heap and the current root does not
                pop();
                add(x);
            }
        }

        /**
         * After a leaf node is placed at location i, the invariants may be violated.
         * This method restores the invariant.
         *
         * @param i The index of the leaf node which has been changed.
         */
        private void bubble(int i) {
            if (i > 1) {
                int parent = i / 2;
                if (data[i] < data[parent]) {
                    double tmp = data[i];
                    data[i] = data[parent];
                    data[parent] = tmp;
                    bubble(parent);
                }
            }
        }

        /**
         * Remove the root of the heap and restore the invariant.
         *
         * @return The smallest value in the heap.
         */
        private double pop() {
            Preconditions.checkState(count > 0, "Can't pop from empty heap");
            double r = data[1];
            pop(1);
            return r;
        }

        private double peek() {
            return data[1];
        }

        private void pop(int i) {
            int left = 2 * i;
            if (left <= count) {
                int right = left + 1;
                if (right <= count) {
                    if (data[left] < data[right]) {
                        data[i] = data[left];
                        pop(left);

                    } else {
                        data[i] = data[right];
                        pop(right);
                    }
                } else {
                    data[i] = data[left];
                    pop(left);
                }
            } else if (i < count) {
                // leaf ... move the last element to here and rebalance upward
                data[i] = data[count];
                count--;
                bubble(i);
            } else {
                count--;
            }
        }

        public double size() {
            return count;
        }

        public void clear() {
            count = 0;
        }

        /**
         * Rebalances the entire tree from the top-down.  Floyd's algorithm would do it from
         * the bottom up and be faster, but this isn't used so it doesn't matter.
         *
         * @param base The base of the sub-heap to be balanced.
         */
        private void rebalance(int base) {
            int left = 2 * base;
            if (left <= count && data[base] > data[left]) {
                double tmp = data[left];
                data[left] = data[base];
                data[base] = tmp;
                rebalance(left);
            }

            int right = left + 1;
            if (right <= count && data[base] > data[right]) {
                double tmp = data[right];
                data[right] = data[base];
                data[base] = tmp;
                rebalance(right);
            }
        }

        /**
         * Verify the invariant.  Bitch if an error are found.
         *
         * @param i The index of the root of the tree to check.
         */
        private boolean validate(int i) {
            if (i >= count) {
                return true;
            }

            int left = 2 * i;
            if (left <= count) {
                if (data[i] > data[left]) {
                    log.warn("Data at {} > {}", i, left);
                    log.warn("Data at {} > {}", data[i], data[left]);
                    return false;
                }
                if (!validate(left)) {
                    return false;
                }

                int right = left + 1;
                if (right <= count) {
                    if (data[i] > data[right]) {
                        log.warn("Data at {} > {}", i, right);
                        log.warn("Data at {} > {}", data[i], data[right]);
                        return false;
                    }
                    if (!validate(right)) {
                        return false;
                    }
                }
            }
            return true;
        }

        private void print(int i) {
            if (i <= count) {
                System.out.printf("%4d, %4d ", i, 31 - Integer.numberOfLeadingZeros(i));
                indent(31 - Integer.numberOfLeadingZeros(i));
                System.out.printf("%.3f\n", data[i]);
                print(2 * i);
                print(2 * i + 1);
            }
        }

        private void indent(int indent) {
            for (int j = 0; j < indent; j++) {
                System.out.printf("|  ");
            }
        }
    }
}
