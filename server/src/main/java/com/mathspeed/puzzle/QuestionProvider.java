package com.mathspeed.puzzle;

import java.util.List;

/**
 * Provides questions as payload strings in the format "<questionPayload>@@<answer>".
 *
 * This is a functional interface so you can pass method references (e.g. provider::getQuestions)
 * or implement the interface in a class (e.g. PuzzleQuestionProvider).
 */
@FunctionalInterface
public interface QuestionProvider {
    /**
     * Produce up to {@code count} question payloads. Each element MUST follow "<questionPayload>@@<answer>".
     *
     * @param count number of questions requested (>=1)
     * @return list of payload strings; size may be <= count if provider cannot produce more
     */
    List<String> getQuestions(int count, int difficulty);
}
