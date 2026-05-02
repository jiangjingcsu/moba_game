package com.moba.battle.util;

public class FixedPoint {
    private static final int FRACTION_BITS = 16;
    private static final int SCALE = 1 << FRACTION_BITS;
    private final long rawValue;

    private FixedPoint(long rawValue) {
        this.rawValue = rawValue;
    }

    public static FixedPoint fromInt(int value) {
        return new FixedPoint((long) value << FRACTION_BITS);
    }

    public static FixedPoint fromFloat(float value) {
        return new FixedPoint(Math.round(value * SCALE));
    }

    public static FixedPoint fromDouble(double value) {
        return new FixedPoint(Math.round(value * SCALE));
    }

    public int toInt() {
        return (int) (rawValue >> FRACTION_BITS);
    }

    public float toFloat() {
        return (float) rawValue / SCALE;
    }

    public double toDouble() {
        return (double) rawValue / SCALE;
    }

    public long getRawValue() {
        return rawValue;
    }

    public FixedPoint add(FixedPoint other) {
        return new FixedPoint(this.rawValue + other.rawValue);
    }

    public FixedPoint sub(FixedPoint other) {
        return new FixedPoint(this.rawValue - other.rawValue);
    }

    public FixedPoint mul(FixedPoint other) {
        return new FixedPoint((this.rawValue * other.rawValue) >> FRACTION_BITS);
    }

    public FixedPoint div(FixedPoint other) {
        if (other.rawValue == 0) {
            throw new ArithmeticException("除以零");
        }
        return new FixedPoint((this.rawValue << FRACTION_BITS) / other.rawValue);
    }

    public FixedPoint mulInt(int value) {
        return new FixedPoint(this.rawValue * value);
    }

    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        return this.rawValue == ((FixedPoint) obj).rawValue;
    }

    public int hashCode() {
        return Long.hashCode(rawValue);
    }

    public String toString() {
        return Float.toString(toFloat());
    }

    public boolean lessThan(FixedPoint other) {
        return this.rawValue < other.rawValue;
    }

    public boolean greaterThan(FixedPoint other) {
        return this.rawValue > other.rawValue;
    }

    public boolean lessThanOrEqual(FixedPoint other) {
        return this.rawValue <= other.rawValue;
    }

    public boolean greaterThanOrEqual(FixedPoint other) {
        return this.rawValue >= other.rawValue;
    }

    public FixedPoint abs() {
        return new FixedPoint(rawValue < 0 ? -rawValue : rawValue);
    }

    public FixedPoint sqrt() {
        if (rawValue < 0) {
            throw new ArithmeticException("无法计算负数的平方根");
        }
        if (rawValue == 0) return new FixedPoint(0);
        
        long x = rawValue;
        long y = (x + 1) >> 1;
        while (y < x) {
            x = y;
            y = (x + (rawValue << FRACTION_BITS) / x) >> 1;
        }
        return new FixedPoint(x);
    }

    public static FixedPoint min(FixedPoint a, FixedPoint b) {
        return a.rawValue < b.rawValue ? a : b;
    }

    public static FixedPoint max(FixedPoint a, FixedPoint b) {
        return a.rawValue > b.rawValue ? a : b;
    }
}
