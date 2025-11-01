package com.mathspeed.puzzle;

import java.util.*;

import static com.mathspeed.puzzle.MathPuzzleUtils.*;

/**
 * Generator creates a puzzle with:
 * - an exact target represented by MathPuzzleUtils.Fraction
 * - a list of selectable integers (numbers) that players can use to build expressions
 *
 * Improvements:
 * - Defensive generation to ensure difficulty-2 targets are never whole.
 * - BFS uses a single FractionState nested class.
 * - Adds a debug log (System.out) showing generated target and display for easier debugging.
 */
public class MathPuzzleGenerator {
    private static final int MIN_NUMBERS = 5;
    private static final int MAX_NUMBERS = 10;
    private static final Random random = new Random();
    private int difficultyLevel = 1; // 1: de, 2: trung, 3: kho

    public MathPuzzleGenerator(int difficultyLevel){
        this.difficultyLevel = difficultyLevel;
    }

    private static final class FractionState {
        final Fraction value;
        final List<Integer> used;
        FractionState(Fraction value, List<Integer> used) { this.value = value; this.used = used; }
    }

    public MathPuzzleFormat generatePuzzle(){
        Fraction targetFraction = generateTargetFraction();

        // debug: print generated target and whether it's whole
        System.out.println("MathPuzzleGenerator: generated target=" + targetFraction + " isWhole=" + targetFraction.isWhole() + " difficulty=" + difficultyLevel);

        List<Integer> numbers = generateNumberSet(targetFraction);
        MathPuzzleFormat fmt = new MathPuzzleFormat(targetFraction, numbers, difficultyLevel);

        // debug: print the display string
        System.out.println("MathPuzzleGenerator: targetDisplay=" + fmt.getTargetDisplay());

        return fmt;
    }

    private Fraction generateTargetFraction(){
        switch(difficultyLevel){
            case 1:
                return Fraction.whole(random.nextInt(1000) + 1);

            case 2: {
                Fraction f;
                // Defensive: ensure non-whole target for difficulty 2
                do {
                    if (random.nextBoolean()) {
                        int whole = random.nextInt(51);           // 0..50
                        int tenth = random.nextInt(9) + 1;       // 1..9 (cannot be 0)
                        long num = whole * 10L + tenth;
                        f = new Fraction(num, 10);
                    } else {
                        int den = random.nextInt(9) + 2;         // 2..10
                        int num = random.nextInt(den - 1) + 1;  // 1..den-1
                        int g = gcd(num, den);
                        num /= g; den /= g;
                        f = new Fraction(num, den);
                    }
                } while (f.isWhole()); // check xem co phai so nguyen khong
                return f;
            }

            case 3: {
                Fraction f;
                do {
                    if (random.nextBoolean()) {
                        int whole = random.nextInt(101);         // 0..100
                        int hund = random.nextInt(99) + 1;       // 1..99 (cannot be 0)
                        long num = whole * 100L + hund;
                        f = new Fraction(num, 100);
                    } else {
                        int wholePart = random.nextInt(10) + 1;
                        int den = random.nextInt(9) + 2;
                        int num = random.nextInt(den - 1) + 1;
                        int g = gcd(num, den);
                        num /= g; den /= g;
                        long total = wholePart * (long) den + num;
                        f = new Fraction(total, den);
                    }
                } while (f.isWhole());
                return f;
            }
            default:
                return Fraction.whole(random.nextInt(100000) + 1);
        }
    }

    private List<Integer> generateNumberSet(Fraction targetFraction){
        int setSize;
        switch(difficultyLevel){
            case 1: setSize = random.nextInt(3) + MIN_NUMBERS; break;
            case 2: setSize = random.nextInt(4) + MIN_NUMBERS; break;
            case 3: setSize = random.nextInt(MAX_NUMBERS - MIN_NUMBERS + 1) + MIN_NUMBERS; break;
            default: setSize = MIN_NUMBERS;
        }

        Set<Integer> numberSet = new LinkedHashSet<>();
        List<Integer> baseNumbers = findWayToTarget(targetFraction);

        boolean targetIsWhole = targetFraction.isWhole();
        long exactTargetInt = targetIsWhole ? targetFraction.getNumerator() / targetFraction.getDenominator() : Long.MIN_VALUE;

        for (Integer b : baseNumbers) {
            if (b == null || b <= 0) continue;
            if (targetIsWhole && b.longValue() == exactTargetInt) continue;
            numberSet.add(b);
        }

        while (numberSet.size() < setSize) {
            int noise = generateNoiseNumber();
            if (targetIsWhole && noise == exactTargetInt) {
                int alt1 = Math.max(1, (int)Math.min(10, exactTargetInt / 2));
                int alt2 = Math.max(1, (int)Math.min(20, exactTargetInt / 3 + 1));
                numberSet.add(alt1);
                if (numberSet.size() < setSize) numberSet.add(alt2);
                continue;
            }
            numberSet.add(noise);
        }

        if (targetIsWhole && numberSet.remove((int) exactTargetInt)) {
            numberSet.add(1);
            while (numberSet.size() < setSize) numberSet.add(generateNoiseNumber());
        }

        List<Integer> result = new ArrayList<>(numberSet);
        Collections.shuffle(result);
        return result;
    }

    private int generateNoiseNumber() {
        switch (difficultyLevel) {
            case 1: return random.nextInt(9) + 1;
            case 2: return random.nextInt(19) + 1;
            case 3: return random.nextInt(29) + 1;
            default: return random.nextInt(9) + 1;
        }
    }

    private List<Integer> findWayToTarget(Fraction target) {
        List<Integer> initialNumbers = new ArrayList<>();
        int maxInitialNumber, initialCount;
        switch (difficultyLevel) {
            case 1: maxInitialNumber = 10; initialCount = random.nextInt(2) + 2; break;
            case 2: maxInitialNumber = 15; initialCount = random.nextInt(2) + 2; break;
            case 3: maxInitialNumber = 20; initialCount = random.nextInt(3) + 2; break;
            default: maxInitialNumber = 10; initialCount = 2;
        }

        for (int i = 0; i < initialCount; i++) initialNumbers.add(random.nextInt(maxInitialNumber) + 1);

        boolean canReach = canReachTargetWithFraction(initialNumbers, target);
        int attempts = 0;
        while (!canReach && attempts < 10) {
            initialNumbers.clear();
            for (int i = 0; i < initialCount; i++) initialNumbers.add(random.nextInt(maxInitialNumber) + 1);
            canReach = canReachTargetWithFraction(initialNumbers, target);
            attempts++;
        }

        if (!canReach) {
            initialNumbers.clear();
            long whole = target.wholePart();
            Fraction fracPart = target.fractionalPart();

            switch (difficultyLevel) {
                case 1:
                    if (target.isWhole()) {
                        long t = target.getNumerator() / target.getDenominator();
                        int p1 = (int)Math.max(1, Math.min(9, t/2));
                        int p2 = (int)Math.max(1, t - p1);
                        initialNumbers.add(p1); initialNumbers.add(p2);
                    } else {
                        initialNumbers.add((int)Math.max(1, target.wholePart()));
                        initialNumbers.add(1);
                    }
                    break;
                case 2:
                    if (target.isWhole()) {
                        long t = target.getNumerator() / target.getDenominator();
                        int p1 = (int)Math.max(1, Math.min(10, t/2));
                        int p2 = (int)Math.max(1, t - p1);
                        initialNumbers.add(p1); initialNumbers.add(p2);
                    } else {
                        if (whole > 0) initialNumbers.add((int) whole);
                        if (!fracPart.isWhole()) {
                            long den = fracPart.getDenominator();
                            long num = fracPart.getNumerator();
                            if (den <= 10) {
                                initialNumbers.add((int) num); initialNumbers.add((int) den);
                            } else {
                                int denom = 10;
                                int n = (int)Math.round(fracPart.toDouble() * denom);
                                int g = gcd(Math.abs(n), denom);
                                initialNumbers.add(n / g); initialNumbers.add(denom / g);
                            }
                        }
                    }
                    break;
                case 3:
                    if (whole > 0) {
                        int p1 = (int)Math.max(1, Math.min(20, whole/3));
                        int p2 = (int)Math.max(1, whole - p1);
                        initialNumbers.add(p1); initialNumbers.add(p2);
                    }
                    if (!fracPart.isWhole()) {
                        long den = fracPart.getDenominator();
                        long num = fracPart.getNumerator();
                        if (den <= 100) {
                            initialNumbers.add((int) num); initialNumbers.add((int) den);
                        } else {
                            int denom = 100;
                            int n = (int)Math.round(fracPart.toDouble() * denom);
                            int g = gcd(Math.abs(n), denom);
                            initialNumbers.add(n / g); initialNumbers.add(denom / g);
                        }
                    }
                    break;
                default:
                    initialNumbers.add(1); initialNumbers.add(2);
            }

            if (initialNumbers.isEmpty()) { initialNumbers.add(1); initialNumbers.add(2); }
            initialNumbers.add(random.nextInt(10) + 1);
        }

        initialNumbers.removeIf(Objects::isNull);
        initialNumbers.removeIf(x -> x <= 0);
        return initialNumbers;
    }

    private boolean canReachTargetWithFraction(List<Integer> numbers, Fraction target) {
        Queue<FractionState> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        for (Integer n : numbers) {
            Fraction f = Fraction.whole(n);
            List<Integer> used = new ArrayList<>(); used.add(n);
            queue.add(new FractionState(f, used));
            visited.add(keyFor(f, used));
        }
        while (!queue.isEmpty()) {
            FractionState cur = queue.poll();
            if (cur.value.equals(target)) return true;
            for (Integer n : numbers) {
                if (!cur.used.contains(n)) {
                    List<Integer> newUsed = new ArrayList<>(cur.used); newUsed.add(n);
                    Fraction fn = Fraction.whole(n);
                    try { addIfNew(queue, visited, cur.value.add(fn), newUsed); } catch (Exception ignored) {}
                    try { addIfNew(queue, visited, cur.value.subtract(fn), newUsed); } catch (Exception ignored) {}
                    try { addIfNew(queue, visited, cur.value.multiply(fn), newUsed); } catch (Exception ignored) {}
                    if (n != 0) try { addIfNew(queue, visited, cur.value.divide(fn), newUsed); } catch (Exception ignored) {}
                }
            }
        }
        return false;
    }

    private String keyFor(Fraction f, List<Integer> used) {
        return f.getNumerator() + "/" + f.getDenominator() + ":" + used.toString();
    }

    private void addIfNew(Queue<FractionState> queue, Set<String> visited, Fraction value, List<Integer> used) {
        String key = value.getNumerator() + "/" + value.getDenominator() + ":" + used.toString();
        if (!visited.contains(key)) { queue.add(new FractionState(value, new ArrayList<>(used))); visited.add(key); }
    }

    public void setDifficultyLevel(int difficultyLevel) { this.difficultyLevel = difficultyLevel; }
}