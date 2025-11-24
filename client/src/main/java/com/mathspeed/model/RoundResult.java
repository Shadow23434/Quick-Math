package com.mathspeed.model;

import java.util.List;
import java.util.Map;

public class RoundResult {
    public int round_index;
    public int round_number;
    public String round_winner;
    public List<PlayerResult> players;

    public static class PlayerResult {
        public String id;
        public String username;
        public boolean correct;
        public long round_play_time_ms;
        public int total_score;
        public long total_play_time_ms;
    }
}