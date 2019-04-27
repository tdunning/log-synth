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

package com.mapr.synth.distributions;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

public class IpAddressDistribution {
    private double ipV4Probability = 0.3;
    private Random base = new Random();

    public void setIpV4Probability(double ipV4Probability) {
        this.ipV4Probability = ipV4Probability;
    }

    public void setSeed(long seed) {
        base.setSeed(seed);
    }

    public InetAddress sample() {
        if (base.nextDouble() < ipV4Probability) {
            byte[] bits = new byte[4];
            base.nextBytes(bits);
            try {
                return Inet4Address.getByAddress(bits);
            } catch (UnknownHostException e) {
                throw new IllegalStateException("Can't happen ... we gave the right number of bytes");
            }
        } else {
            byte[] bits = new byte[16];
            base.nextBytes(bits);
            if (base.nextDouble() < 0.1) {
                for (int i = 2; i < 8; i++) {
                    bits[i] = 0;
                }
            }
            try {
                return Inet6Address.getByAddress(bits);
            } catch (UnknownHostException e) {
                throw new IllegalStateException("Can't happen ... we gave the right number of bytes");
            }
        }
    }
}
