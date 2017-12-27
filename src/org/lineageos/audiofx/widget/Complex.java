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
 * Java support for complex numbers.
 *
 * @author alankila
 */
public class Complex {
    private final double mReal, mIm;

    public Complex(double real, double im) {
        mReal = real;
        mIm = im;
    }

    /**
     * Length of complex number
     *
     * @return length
     */
    public double rho() {
        return Math.sqrt(mReal * mReal + mIm * mIm);
    }

    /**
     * Argument of complex number
     *
     * @return angle in radians
     */
    public double theta() {
        return Math.atan2(mIm, mReal);
    }

    /**
     * Complex conjugate
     *
     * @return conjugate
     */
    public Complex con() {
        return new Complex(mReal, -mIm);
    }

    /**
     * Complex addition
     *
     * @param other
     * @return sum
     */
    public Complex add(Complex other) {
        return new Complex(mReal + other.mReal, mIm + other.mIm);
    }

    /**
     * Complex multipply
     *
     * @param other
     * @return multiplication result
     */
    public Complex mul(Complex other) {
        return new Complex(mReal * other.mReal - mIm * other.mIm,
                mReal * other.mIm + mIm * other.mReal);
    }

    /**
     * Complex multiply with real value
     *
     * @param a
     * @return multiplication result
     */
    public Complex mul(double a) {
        return new Complex(mReal * a, mIm * a);
    }

    /**
     * Complex division
     *
     * @param other
     * @return division result
     */
    public Complex div(Complex other) {
        double lengthSquared = other.mReal * other.mReal + other.mIm * other.mIm;
        return mul(other.con()).div(lengthSquared);
    }

    /**
     * Complex division with real value
     *
     * @param a
     * @return division result
     */
    public Complex div(double a) {
        return new Complex(mReal / a, mIm / a);
    }
}
