package com.mathspeed.puzzle;

import java.util.ArrayList;
import java.util.List;

/**
 * PuzzleQuestionProvider - builds JSON payloads for client and returns payload + canonical answer.
 *
 * Contract:
 *   getQuestions(n) -> list of strings in form "<jsonPayload>@@<canonicalAnswer>"
 *
 * This implementation supports difficulty-aware calls via getQuestions(count, difficulty).
 * For backward compatibility, getQuestions(count) delegates to getQuestions(count, defaultDifficulty).
 */
public class PuzzleQuestionProvider implements QuestionProvider {

    // default difficulty used by no-arg getQuestions
    private static final int DEFAULT_DIFFICULTY = 1;

    // Keep a default constructor (no stored generator) to avoid holding a generator with a fixed difficulty.
    public PuzzleQuestionProvider() {
    }

    /**
     * Produce up to {@code count} question payloads with the requested difficulty.
     * Each returned element is: "<jsonPayload>@@<canonicalAnswer>"
     *
     * jsonPayload example:
     * {"target":{"numerator":10,"denominator":3,"whole":3,"fraction":{"num":1,"den":3}},"display":"3 1/3","approx":3.3333333333,"numbers":[1,2,3,4,5]}
     *
     * canonicalAnswer example: "10/3"
     */
    public List<String> getQuestions(int count, int difficulty) {
        List<String> out = new ArrayList<>(Math.max(0, count));
        // create a generator configured for the requested difficulty
        MathPuzzleGenerator generator = new MathPuzzleGenerator(Math.max(1, difficulty));
        for (int i = 0; i < count; i++) {
            MathPuzzleFormat puzzle = generator.generatePuzzle();

            String json = puzzle.toJson();

            // canonical answer: improper fraction "numerator/denominator"
            long num = puzzle.getTargetNumerator();
            long den = puzzle.getTargetDenominator();
            String canonical = num + "/" + den;

            out.add(json + "@@" + canonical);
        }
        return out;
    }
}