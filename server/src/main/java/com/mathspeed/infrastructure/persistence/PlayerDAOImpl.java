package com.mathspeed.infrastructure.persistence;

import com.mathspeed.domain.port.PlayerRepository;
import com.mathspeed.domain.model.Player;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;

public class PlayerDAOImpl extends BaseDAO implements PlayerRepository {
    public PlayerDAOImpl() {
        super();
    }

    @Override
    public String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(12));
    }

    @Override
    public boolean checkPassword(String plain, String hashed) {
        try {
            // BCrypt hashes start with $2a$, $2b$, $2x$, or $2y$
            // jBCrypt only supports $2a$, so we need to handle $2b$ from Node.js bcrypt
            if (hashed != null && hashed.startsWith("$2b$")) {
                // Convert $2b$ to $2a$ - they are compatible
                hashed = "$2a$" + hashed.substring(4);
            }
            return BCrypt.checkpw(plain, hashed);
        } catch (IllegalArgumentException e) {
            // Invalid hash format (not a BCrypt hash)
            return false;
        }
    }

    @Override
    public boolean insertPlayer(Player player) throws SQLException {
        String sql = "INSERT INTO players (id, username, display_name, password_hash, gender, avatar_url, country_code, created_at, status, last_active_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, player.getId());
            stmt.setString(2, player.getUsername());
            stmt.setString(3, player.getDisplayName());
            stmt.setString(4, player.getPasswordHash());
            stmt.setString(5, player.getGender());
            stmt.setString(6, player.getAvatarUrl());
            stmt.setString(7, player.getCountryCode());
            stmt.setTimestamp(8, Timestamp.valueOf(player.getCreatedAt() != null ? player.getCreatedAt() : LocalDateTime.now()));
            stmt.setString(9, player.getStatus());
            stmt.setTimestamp(10, Timestamp.valueOf(player.getLastActiveAt() != null ? player.getLastActiveAt() : LocalDateTime.now()));

            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public Player findPlayer(String username, String password) throws SQLException {
        String sql = "SELECT * FROM players WHERE username = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Player player = new Player();
                    player.setId(rs.getString("id"));
                    player.setUsername(rs.getString("username"));
                    player.setDisplayName(rs.getString("display_name"));
                    player.setPasswordHash(rs.getString("password_hash"));
                    player.setGender(rs.getString("gender"));
                    player.setAvatarUrl(rs.getString("avatar_url"));
                    player.setStatus(rs.getString("status"));

                    String country = rs.getString("country_code");
                    if (country != null) player.setCountryCode(country);

                    Timestamp lastActiveTs = rs.getTimestamp("last_active_at");
                    if (lastActiveTs != null) {
                        player.setLastActiveAt(lastActiveTs.toLocalDateTime());
                    }

                    Timestamp ts = rs.getTimestamp("created_at");
                    if (ts != null) {
                        player.setCreatedAt(ts.toLocalDateTime());
                    }

                    if (checkPassword(password.trim(), player.getPasswordHash())) {
                        return player;
                    }
                }
                return null;
            }
        }
    }

    @Override
    public void updateStatus(String username, String status) throws SQLException {
        String sql = "UPDATE players SET last_active_at = NOW(), status = ? WHERE username = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setString(2, username);
            stmt.executeUpdate();
        }
    }

    @Override
    public Player getPlayerById(String id) throws SQLException {
        if (id == null) return null;
        id = id.trim();
        if (id.isEmpty()) return null;

        String sql = "SELECT * FROM players WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Player player = new Player();
                    player.setId(rs.getString("id"));
                    player.setUsername(rs.getString("username"));
                    player.setDisplayName(rs.getString("display_name"));
                    player.setPasswordHash(rs.getString("password_hash"));
                    player.setGender(rs.getString("gender"));
                    player.setAvatarUrl(rs.getString("avatar_url"));
                    player.setStatus(rs.getString("status"));

                    String country = rs.getString("country_code");
                    if (country != null) player.setCountryCode(country);

                    Timestamp lastActiveTs = rs.getTimestamp("last_active_at");
                    if (lastActiveTs != null) {
                        player.setLastActiveAt(lastActiveTs.toLocalDateTime());
                    }

                    Timestamp ts = rs.getTimestamp("created_at");
                    if (ts != null) {
                        player.setCreatedAt(ts.toLocalDateTime());
                    }
                    return player;
                }
                return null;
            }
        }
    }

    @Override
    public boolean existsByUsername(String username) throws SQLException {
        String sql = "SELECT 1 FROM players WHERE username = ? LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public boolean existsById(String id) throws Exception {
        String sql = "SELECT 1 FROM players WHERE id = ? LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public List<Player> getAllPlayers(String excludePlayerId) throws Exception {
        boolean exclude = excludePlayerId != null && !excludePlayerId.isEmpty();
        String sql = (exclude)
                ? "SELECT * FROM players WHERE id <> ?"
                : "SELECT * FROM players";

        List<Player> players = new java.util.ArrayList<>();

        try (Connection conn = getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                if (exclude) {
                    stmt.setString(1, excludePlayerId);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Player player = new Player();
                        player.setId(rs.getString("id"));
                        player.setUsername(rs.getString("username"));
                        player.setDisplayName(rs.getString("display_name"));
                        player.setPasswordHash(rs.getString("password_hash"));
                        player.setGender(rs.getString("gender"));
                        player.setAvatarUrl(rs.getString("avatar_url"));
                        player.setStatus(rs.getString("status"));

                        String country = rs.getString("country_code");
                        if (country != null) player.setCountryCode(country);

                        Timestamp lastActiveTs = rs.getTimestamp("last_active_at");
                        if (lastActiveTs != null) {
                            player.setLastActiveAt(lastActiveTs.toLocalDateTime());
                        }

                        Timestamp ts = rs.getTimestamp("created_at");
                        if (ts != null) {
                            player.setCreatedAt(ts.toLocalDateTime());
                        }

                        players.add(player);
                    }
                }
            }
        }

        return players;
    }

    @Override
    public List<Player> getOnlinePlayers(String excludePlayerId) throws Exception {
        boolean exclude = excludePlayerId != null && !excludePlayerId.isEmpty();
        String sql = exclude
                ? "SELECT * FROM players WHERE status = ? AND id <> ?"
                : "SELECT * FROM players WHERE status = ?";

        List<Player> players = new java.util.ArrayList<>();

        try (Connection conn = getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, "online");
                if (exclude) {
                    stmt.setString(2, excludePlayerId);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Player player = new Player();
                        player.setId(rs.getString("id"));
                        player.setUsername(rs.getString("username"));
                        player.setDisplayName(rs.getString("display_name"));
                        player.setPasswordHash(rs.getString("password_hash"));
                        player.setGender(rs.getString("gender"));
                        player.setAvatarUrl(rs.getString("avatar_url"));
                        player.setStatus(rs.getString("status"));

                        String country = rs.getString("country_code");
                        if (country != null) player.setCountryCode(country);

                        Timestamp lastActiveTs = rs.getTimestamp("last_active_at");
                        if (lastActiveTs != null) {
                            player.setLastActiveAt(lastActiveTs.toLocalDateTime());
                        }

                        Timestamp ts = rs.getTimestamp("created_at");
                        if (ts != null) {
                            player.setCreatedAt(ts.toLocalDateTime());
                        }

                        players.add(player);
                    }
                }
            }
        }

        return players;
    }

    @Override
    public List<Player> searchPlayers(String keyword, String excludePlayerId) throws Exception {
        String sql = "SELECT * FROM players WHERE display_name LIKE ? AND id <> ?";
        List<Player> players = new java.util.ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            String pattern = "%" + (keyword != null ? keyword : "") + "%";
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Player player = new Player();
                    player.setId(rs.getString("id"));
                    player.setUsername(rs.getString("username"));
                    player.setDisplayName(rs.getString("display_name"));
                    player.setPasswordHash(rs.getString("password_hash"));
                    player.setGender(rs.getString("gender"));
                    player.setAvatarUrl(rs.getString("avatar_url"));
                    player.setStatus(rs.getString("status"));

                    String country = rs.getString("country_code");
                    if (country != null) player.setCountryCode(country);

                    Timestamp lastActiveTs = rs.getTimestamp("last_active_at");
                    if (lastActiveTs != null) {
                        player.setLastActiveAt(lastActiveTs.toLocalDateTime());
                    }

                    Timestamp ts = rs.getTimestamp("created_at");
                    if (ts != null) {
                        player.setCreatedAt(ts.toLocalDateTime());
                    }

                    players.add(player);
                }
            }
        }

        return players;
    }

}
