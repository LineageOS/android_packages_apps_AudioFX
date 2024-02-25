/*
 * Copyright (C) 2016 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lineageos.audiofx.widget;

/**
 * Evaluate transfer functions of biquad filters in direct form 1.
 *
 * @author alankila
 */
public class Biquad {
    private Complex mB0, mB1, mB2, mA0, mA1, mA2;

    public void setHighShelf(double centerFrequency, double samplingFrequency,
                             double dbGain, double slope) {
        double w0 = 2 * Math.PI * centerFrequency / samplingFrequency;
        double a = Math.pow(10, dbGain / 40);
        double alpha = Math.sin(w0) / 2 * Math.sqrt((a + 1 / a) * (1 / slope - 1) + 2);

        mB0 = new Complex(a * ((a + 1) + (a - 1) * Math.cos(w0) + 2 * Math.sqrt(a) * alpha), 0);
        mB1 = new Complex(-2 * a * ((a - 1) + (a + 1) * Math.cos(w0)), 0);
        mB2 = new Complex(a * ((a + 1) + (a - 1) * Math.cos(w0) - 2 * Math.sqrt(a) * alpha), 0);
        mA0 = new Complex((a + 1) - (a - 1) * Math.cos(w0) + 2 * Math.sqrt(a) * alpha, 0);
        mA1 = new Complex(2 * ((a - 1) - (a + 1) * Math.cos(w0)), 0);
        mA2 = new Complex((a + 1) - (a - 1) * Math.cos(w0) - 2 * Math.sqrt(a) * alpha, 0);
    }

    public Complex evaluateTransfer(Complex z) {
        Complex zSquared = z.mul(z);
        Complex nom = mB0.add(mB1.div(z)).add(mB2.div(zSquared));
        Complex den = mA0.add(mA1.div(z)).add(mA2.div(zSquared));
        return nom.div(den);
    }
}
