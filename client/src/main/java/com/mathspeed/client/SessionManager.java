package com.mathspeed.client;

import com.mathspeed.model.Player;

public class SessionManager {
    private static SessionManager instance;
    private String authToken;
    private Player currentPlayer;

    private SessionManager() {}

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public void startSession(String token, Player player) {
        this.authToken = token;
        this.currentPlayer = player;
    }

    public void endSession() {
        this.authToken = null;
        this.currentPlayer = null;
    }

    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    public String getAuthToken() {
        return authToken;
    }

    public boolean isLoggedIn() {
        return authToken != null;
    }
}