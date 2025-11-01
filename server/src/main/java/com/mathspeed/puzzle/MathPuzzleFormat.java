package com.mathspeed.puzzle;

import java.util.List;
import static com.mathspeed.puzzle.MathPuzzleUtils.*;

/**
 * MathPuzzleFormat - represents a puzzle with an exact Fraction target and numbers.
 *
 * toJson() uses getTargetDisplay() which now consistently prefers:
 *  - difficulty 1: integer display (whole)
 *  - difficulty 2: proper fraction (a/b) OR one-decimal (x.y) with denominator 10 (never mixed)
 *  - difficulty 3: mixed number "W a/b" or two-decimal (x.yy) where denominator is 100
 */
public class MathPuzzleFormat {
    private final MathPuzzleUtils.Fraction targetFraction;
    private final double targetApprox;
    private final List<Integer> numbers;
    private final String targetDisplay;
    private final int difficultyLevel;

    public MathPuzzleFormat(MathPuzzleUtils.Fraction targetFraction, List<Integer> numbers, int difficultyLevel) {
        this.targetFraction = targetFraction;
        this.targetApprox = targetFraction.toDouble();
        this.numbers = numbers;
        this.difficultyLevel = difficultyLevel;
        this.targetDisplay = buildDisplay(targetFraction, difficultyLevel);
    }

    private String buildDisplay(MathPuzzleUtils.Fraction f, int difficulty) {
        // difficulty = 1 -> integer (whole)
        if (difficulty == 1) {
            if (f.isWhole()) {
                return String.valueOf(f.getNumerator() / f.getDenominator());
            } else {
                // Defensive fallback: expose only whole part if a fractional slipped in
                return String.valueOf(f.wholePart());
            }
        }

        long denR = f.getDenominator();

        // difficulty = 2 -> MUST NOT show mixed numbers.
        // Rules:
        //  - If whole == 0 and denominator <= 10 => show proper fraction "a/b"
        //  - Otherwise show one-decimal representation (x.y) derived from exact fraction (denominator effectively 10)
        if (difficulty == 2) {
            if (denR != 10) {
                // proper fraction a/b
                return f.toString();
            } else {
                // approximate to one decimal (preserve one decimal digit)
                double value = f.toDouble();
                return String.format("%.1f", value).replace(",", ".");
            }
        }

        // difficulty = 3 -> prefer mixed number when sensible, otherwise two-decimal
        if (difficulty == 3) {
            if(denR == 100){
                // no whole part
                double value = f.toDouble();
                String s = String.format("%.2f", value).replace(",", ".");
                if (s.indexOf('.') >= 0) {
                    while (s.endsWith("0")) s = s.substring(0, s.length()-1);
                    if (s.endsWith(".")) s = s.substring(0, s.length()-1);
                }
                return s;
            } else {
                long whole = f.wholePart();
                Fraction frac = f.fractionalPart(); // reduced fractional part
                long den = frac.getDenominator();
                long num = frac.getNumerator();
                return whole + " " + Math.abs(num) + "/" + den;
            }
        }

        // default fallback: treat like difficulty 2 (never mixed)
        if (denR != 10) {
            return f.toString();
        } else {
            return String.format("%.1f", f.toDouble()).replace(",", ".");
        }
    }

    public long getTargetNumerator() { return targetFraction.getNumerator(); }
    public long getTargetDenominator() { return targetFraction.getDenominator(); }
    public long getTargetWholePart() { return targetFraction.wholePart(); }
    public long getTargetFractionalNumerator() { return targetFraction.fractionalPart().getNumerator(); }
    public long getTargetFractionalDenominator() { return targetFraction.fractionalPart().getDenominator(); }
    public MathPuzzleUtils.Fraction getTargetFraction() { return targetFraction; }
    public double getTargetApprox() { return targetApprox; }
    public String getTargetDisplay() { return targetDisplay; }
    public List<Integer> getNumbers() { return numbers; }

    public String toJson() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{");
        sb.append("\"target\":{");
        sb.append("\"numerator\":").append(getTargetNumerator()).append(",");
        sb.append("\"denominator\":").append(getTargetDenominator()).append(",");
        sb.append("\"whole\":").append(getTargetWholePart()).append(",");
        sb.append("\"fraction\":{");
        sb.append("\"num\":").append(getTargetFractionalNumerator()).append(",");
        sb.append("\"den\":").append(getTargetFractionalDenominator());
        sb.append("}");
        sb.append("},");
        sb.append("\"display\":\"").append(escape(targetDisplay)).append("\",");
        sb.append("\"approx\":").append(targetApprox).append(",");
        sb.append("\"numbers\":[");
        if (numbers != null && !numbers.isEmpty()) {
            for (int i = 0; i < numbers.size(); i++) {
                if (i>0) sb.append(",");
                sb.append(numbers.get(i));
            }
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private String escape(String s) { if (s == null) return ""; return s.replace("\\","\\\\").replace("\"","\\\""); }
    public String toString() { return "Target: " + targetDisplay + " (exact: " + targetFraction + ")\nSố: " + numbers.toString(); }
}