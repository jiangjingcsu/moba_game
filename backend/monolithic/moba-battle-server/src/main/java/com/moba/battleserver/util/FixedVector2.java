package com.moba.battleserver.util;

public class FixedVector2 {
    private final FixedPoint x;
    private final FixedPoint y;

    public FixedVector2(FixedPoint x, FixedPoint y) {
        this.x = x;
        this.y = y;
    }

    public static FixedVector2 zero() {
        return new FixedVector2(FixedPoint.fromInt(0), FixedPoint.fromInt(0));
    }

    public FixedPoint getX() { return x; }
    public FixedPoint getY() { return y; }

    public FixedVector2 add(FixedVector2 other) {
        return new FixedVector2(x.add(other.x), y.add(other.y));
    }

    public FixedVector2 sub(FixedVector2 other) {
        return new FixedVector2(x.sub(other.x), y.sub(other.y));
    }

    public FixedVector2 mul(FixedPoint scalar) {
        return new FixedVector2(x.mul(scalar), y.mul(scalar));
    }

    public FixedPoint distanceTo(FixedVector2 other) {
        FixedPoint dx = x.sub(other.x);
        FixedPoint dy = y.sub(other.y);
        return dx.mul(dx).add(dy.mul(dy)).sqrt();
    }

    public FixedPoint length() {
        return x.mul(x).add(y.mul(y)).sqrt();
    }

    public FixedVector2 normalize() {
        FixedPoint len = length();
        if (len.getRawValue() == 0) {
            return zero();
        }
        return new FixedVector2(x.div(len), y.div(len));
    }

    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        FixedVector2 other = (FixedVector2) obj;
        return x.equals(other.x) && y.equals(other.y);
    }

    public int hashCode() {
        return x.hashCode() * 31 + y.hashCode();
    }

    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
