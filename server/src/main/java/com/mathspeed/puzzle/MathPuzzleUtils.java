package com.mathspeed.puzzle;

import java.util.Objects;

/**
 * Utility container for math helpers used across the puzzle generator.
 *
 * - Provides gcd helpers for int/long.
 * - Provides an immutable Fraction implementation as a public static nested class so
 *   other classes in the package can reuse exact rational arithmetic without relying on doubles.
 *
 * Usage examples:
 *   MathPuzzleUtils.gcd(12, 8);
 *   MathPuzzleUtils.Fraction f = new MathPuzzleUtils.Fraction(7, 3);
 *   f.wholePart(); f.fractionalPart(); f.add(...).toDouble();
 */
public final class MathPuzzleUtils {

    private MathPuzzleUtils() { /* utility class */ }

    /**
     * Compute gcd for int (non-negative inputs expected; method handles zero).
     */
    public static int gcd(int a, int b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            int t = b;
            b = a % b;
            a = t;
        }
        return a;
    }

    /**
     * Compute gcd for long (non-negative inputs expected; method handles zero).
     */
    public static long gcd(long a, long b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            long t = b;
            b = a % b;
            a = t;
        }
        return a;
    }

    /**
     * Immutable exact rational number represented by numerator/denominator (den > 0).
     * Public static nested class so callers can reference MathPuzzleUtils.Fraction.
     *
     * Note: Uses long for numerator/denominator. If you expect very large numbers,
     * consider changing to BigInteger.
     */
    public static final class Fraction {
        private final long numerator;   // can be negative
        private final long denominator; // always > 0

        public Fraction(long numerator, long denominator) {
            if (denominator == 0) throw new ArithmeticException("Denominator cannot be zero");
            // normalize sign: denominator > 0
            if (denominator < 0) {
                numerator = -numerator;
                denominator = -denominator;
            }
            long g = MathPuzzleUtils.gcd(Math.abs(numerator), denominator);
            this.numerator = numerator / g;
            this.denominator = denominator / g;
        }

        public static Fraction of(long numerator, long denominator) {
            return new Fraction(numerator, denominator);
        }

        public static Fraction whole(long w) {
            return new Fraction(w, 1);
        }

        public long getNumerator() {
            return numerator;
        }

        public long getDenominator() {
            return denominator;
        }

        public boolean isWhole() {
            return denominator == 1;
        }

        /** Whole part for mixed number (floor; works for negative numbers too). */
        public long wholePart() {
            // Floor division for negative numerators: use Math.floorDiv to be safe
            return Math.floorDiv(numerator, denominator);
        }

        /** Returns the fractional (proper) part as a reduced positive Fraction (0 <= num < den). */
        public Fraction fractionalPart() {
            long w = wholePart();
            long num = Math.abs(numerator - w * denominator);
            return new Fraction(num, denominator);
        }

        public Fraction add(Fraction other) {
            long num = this.numerator * other.denominator + other.numerator * this.denominator;
            long den = this.denominator * other.denominator;
            return new Fraction(num, den);
        }

        public Fraction subtract(Fraction other) {
            long num = this.numerator * other.denominator - other.numerator * this.denominator;
            long den = this.denominator * other.denominator;
            return new Fraction(num, den);
        }

        public Fraction multiply(Fraction other) {
            return new Fraction(this.numerator * other.numerator, this.denominator * other.denominator);
        }

        public Fraction divide(Fraction other) {
            if (other.numerator == 0) throw new ArithmeticException("Division by zero fraction");
            return new Fraction(this.numerator * other.denominator, this.denominator * other.numerator);
        }

        public double toDouble() {
            return (double) numerator / (double) denominator;
        }

        @Override
        public String toString() {
            if (denominator == 1) return Long.toString(numerator);
            return numerator + "/" + denominator;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Fraction)) return false;
            Fraction f = (Fraction) o;
            return this.numerator == f.numerator && this.denominator == f.denominator;
        }

        @Override
        public int hashCode() {
            return Objects.hash(numerator, denominator);
        }
    }
}