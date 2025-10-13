package org.example.server;

import java.io.Serializable;
import java.time.LocalDateTime;

public class User implements Serializable {
    private String username;
    private String passwordHash; // In a real system, store hashed passwords
    private LocalDateTime lastLogin;
    private boolean isOnline;

    public User(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.lastLogin = null;
        this.isOnline = false;
    }

    public String getUsername() {
        return username;
    }
    public String getPasswordHash() {
        return passwordHash;
    }
    public LocalDateTime getLastLogin() {
        return lastLogin;
    }
    public boolean isOnline() {
        return isOnline;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }
    public void setOnline(boolean online) {
        isOnline = online;
    }

}

//User represents WHO they are (identity, credentials).
//ClientHandler represents HOW they connect (socket, network).
//Player would represent GAME-SPECIFIC data (score, rank).