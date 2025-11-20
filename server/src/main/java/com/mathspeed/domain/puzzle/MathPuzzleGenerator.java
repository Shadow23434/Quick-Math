package com.mathspeed.domain.puzzle;

import java.util.*;

public class MathPuzzleGenerator {
    private int difficultyLevel = 1; // 1: easy, 2: medium, 3: hard

    public MathPuzzleGenerator(int difficultyLevel){
        this.difficultyLevel = difficultyLevel;
    }

    public MathPuzzleFormat generatePuzzle(int difficultyLevel, Random rand){
        int target;

        switch(difficultyLevel){
            case 1: // Easy
                target = rand.nextInt(90) + 10;
                break;
            case 2: // Medium
                target = rand.nextInt(900) + 100;
                break;
            case 3: // Hard
                target = rand.nextInt(9000) + 1000;
                break;
            default:
                throw new IllegalArgumentException("Invalid difficulty level: " + difficultyLevel);
        }

        return new MathPuzzleFormat(target);
    }

    public void setDifficultyLevel(int difficultyLevel) { this.difficultyLevel = difficultyLevel; }

}

