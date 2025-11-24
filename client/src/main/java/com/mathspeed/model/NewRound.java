package com.mathspeed.model;

public class NewRound {
    private int round;
    private int difficulty;
    private int target;
    private int time;
    private long server_round_start;
    private long server_round_end;
    private long server_time;

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }

    public int getTarget() {
        return target;
    }

    public void setTarget(int target) {
        this.target = target;
    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public long getServer_round_start() {
        return server_round_start;
    }

    public void setServer_round_start(long server_round_start) {
        this.server_round_start = server_round_start;
    }

    public long getServer_round_end() {
        return server_round_end;
    }

    public void setServer_round_end(long server_round_end) {
        this.server_round_end = server_round_end;
    }

    public long getServer_time() {
        return server_time;
    }

    public void setServer_time(long server_time) {
        this.server_time = server_time;
    }
}