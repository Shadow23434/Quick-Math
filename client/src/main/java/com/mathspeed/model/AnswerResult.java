package com.mathspeed.model;

import com.google.gson.annotations.SerializedName;

public class AnswerResult {
    @SerializedName("type")
    public String type;

    @SerializedName("server_time")
    public long server_time;

    @SerializedName("correct")
    public boolean correct;

    @SerializedName("accepted")
    public boolean accepted;

    @SerializedName("score_gained")
    public int score_gained;

    @SerializedName("result")
    public double result;

    @SerializedName("target")
    public int target;

    @SerializedName("expression")
    public String expression;

    @SerializedName("player_id")
    public String player_id;

    @SerializedName("round_number")
    public int round_number;
}