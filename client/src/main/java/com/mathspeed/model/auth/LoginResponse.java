package com.mathspeed.model.auth;

import com.google.gson.annotations.SerializedName;
import com.mathspeed.model.Player;

public class LoginResponse {
    private String token;
    @SerializedName(value = "message", alternate = {"error"})
    private String message;
    // Optional status code returned by backend (HTTP-style or app-specific). Accept either "statusCode" or "status".
    @SerializedName(value = "statusCode", alternate = {"status"})
    private Integer statusCode;
    @SerializedName(value = "success", alternate = {"ok"})
    private boolean success;
    private Player player;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public int getStatusCodeOrDefault(int defaultValue) {
        return (statusCode != null) ? statusCode.intValue() : defaultValue;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }
}
