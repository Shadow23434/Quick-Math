package com.mathspeed.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
public class MatchStartInfo {
    public String type;
    public long seed;
    public long start_time;
    public long server_time;
    public long countdown_ms;
    public int question_count;
    public int per_question_seconds;
    public List<Player> players;
}