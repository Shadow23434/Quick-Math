package com.mathspeed.application.auth;

import com.mathspeed.domain.model.Player;
import com.mathspeed.domain.port.PlayerRepository;
import com.mathspeed.util.UuidUtil;

import java.time.LocalDateTime;

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
            String token = UuidUtil.randomUuid();
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

    public AuthResult register(String username, String password, String displayName, String gender, String countryCode) {
        // Basic validation
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return new AuthResult(false, null, "Missing registration fields", null);
        }
        if (username.length() < 3) {
            return new AuthResult(false, null, "Username must be at least 3 characters", null);
        }
        if (password.length() < 8) {
            return new AuthResult(false, null, "Password must be at least 8 characters", null);
        }

        try {
            // Ensure username is not already taken (defensive check)
            try {
                if (playerRepository.existsByUsername(username)) {
                    return new AuthResult(false, null, "Username already exists", null);
                }
            } catch (Exception ex) {
                System.err.println("Warning: existsByUsername check failed for " + username + ": " + ex.getMessage());
            }
            String hashed = playerRepository.hashPassword(password);
            String uuid = UuidUtil.randomUuid();
            Player player = new Player(username, hashed);
            player.setId(uuid);
            player.setDisplayName((displayName == null || displayName.isEmpty()) ? username : displayName);
            player.setGender(gender);
            player.setCountryCode(countryCode);
            player.setCreatedAt(LocalDateTime.now());
            player.setLastActiveAt(LocalDateTime.now());
            player.setStatus("online");

            // Default avatar URL to empty for now
            player.setAvatarUrl("https://tse1.mm.bing.net/th/id/OIP.pLa0MvBoBWBLYBwKtdbLhQAAAA?rs=1&amp;pid=ImgDetMain&amp;o=7&amp;rm=3");

            boolean inserted = playerRepository.insertPlayer(player);
            if (!inserted) {
                return new AuthResult(false, null, "Failed to create user", null);
            }

            try {
                playerRepository.updateStatus(username, "online");
            } catch (Exception e) {
                System.err.println("Failed to update status for new user: " + username + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }

            String token = UuidUtil.randomUuid();
            return new AuthResult(true, token, null, player);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("unique") || msg.contains("duplicate") || msg.contains("constraint") || msg.contains("exists")) {
                return new AuthResult(false, null, "Username already exists", null);
            }
            return new AuthResult(false, null, "Registration error: " + e.getMessage(), null);
        }
    }

    public Player getPlayerById(String id) throws Exception {
        if (id == null || id.isEmpty()) return null;
        return playerRepository.getPlayerById(id);
    }

    public boolean exitstsById(String id) {
        if (id == null || id.isEmpty()) return false;
        try {
            return playerRepository.existsById(id);
        } catch (Exception e) {
            System.err.println("existsById check failed for id " + id + ": " + e.getMessage());
            return false;
        }
    }

    public int getTotalPlayers() {
        try {
            return playerRepository.getTotalPlayers();
        } catch (Exception e) {
            System.err.println("getTotalPlayers failed: " + e.getMessage());
            return 0;
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
