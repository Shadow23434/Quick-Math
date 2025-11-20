package com.mathspeed.domain.puzzle;

import com.google.gson.Gson;

public class MathPuzzleFormat {

    private int target;

    public MathPuzzleFormat(int target) {
        this.target = target;
    }

    public int getTarget() {
        return target;
    }


    public String toJson() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    @Override
    public String toString(){
        return "Target: " + target;
    }

}

