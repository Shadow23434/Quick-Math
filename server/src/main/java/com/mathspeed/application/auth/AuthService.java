package com.mathspeed.application.auth;

import com.mathspeed.domain.model.Player;
import com.mathspeed.domain.port.PlayerRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public class AuthService {
    private final PlayerRepository playerRepository;

    public AuthService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public AuthResult login(String username, String password) {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return new AuthResult(false, null, "Missing credentials", null);
        }

        try {
            // Try to find player. Implementation of findPlayer may handle hashing/verification.
            Player player = playerRepository.findPlayer(username, password);
            if (player == null) {
                return new AuthResult(false, null, "Invalid username/password", null);
            }
            try {
                playerRepository.updateStatus(username, "online");
            } catch (Exception e) {
                System.err.println("Failed to update status for user: " + username + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
            }
            String token = UUID.randomUUID().toString();
            return new AuthResult(true, token, null, player);
        } catch (Exception e) {
            return new AuthResult(false, null, "Authentication error: " + e.getMessage(), null);
        }
    }

    public boolean logout(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        try {
            playerRepository.updateStatus(username, "offline");
            return true;
        } catch (Exception e) {
            System.err.println("Failed to logout user: " + username + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    public AuthResult register(String username, String password, String displayName) {
        // Basic validation
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return new AuthResult(false, null, "Missing registration fields", null);
        }
        if (username.length() < 3) {
            return new AuthResult(false, null, "Username must be at least 3 characters", null);
        }
        if (password.length() < 6) {
            return new AuthResult(false, null, "Password must be at least 6 characters", null);
        }

        try {
            String hashed = playerRepository.hashPassword(password);
            Player player = new Player(username, hashed);
            player.setDisplayName((displayName == null || displayName.isEmpty()) ? username : displayName);
            player.setCreatedAt(LocalDateTime.now());
            player.setLastActiveAt(LocalDateTime.now());
            player.setStatus("offline");

            boolean inserted = playerRepository.insertPlayer(player);
            if (!inserted) {
                return new AuthResult(false, null, "Failed to create user", null);
            }

            try {
                playerRepository.updateStatus(username, "online");
            } catch (Exception e) {
                System.err.println("Failed to update status for new user: " + username + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }

            String token = UUID.randomUUID().toString();
            return new AuthResult(true, token, null, player);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("unique") || msg.contains("duplicate") || msg.contains("constraint") || msg.contains("exists")) {
                return new AuthResult(false, null, "Username already exists", null);
            }
            return new AuthResult(false, null, "Registration error: " + e.getMessage(), null);
        }
    }

    public static class AuthResult {
        public final boolean success;
        public final String token;
        public final String error;
        public final Player player;

        public AuthResult(boolean success, String token, String error, Player player) {
            this.success = success;
            this.token = token;
            this.error = error;
            this.player = player;
        }
    }
}
