package com.mathspeed.infrastructure.persistence;

import com.mathspeed.domain.port.PlayerRepository;
import com.mathspeed.domain.model.Player;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.time.LocalDateTime;

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
        String sql = "INSERT INTO players (id, username, display_name, password_hash, gender, created_at) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, player.getId());
            stmt.setString(2, player.getUsername());
            stmt.setString(3, player.getDisplayName());
            stmt.setString(4, player.getPasswordHash());
            stmt.setString(5, player.getGender());
            stmt.setTimestamp(6, Timestamp.valueOf(player.getCreatedAt() != null ? player.getCreatedAt() : LocalDateTime.now()));

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

                    if (checkPassword(password, player.getPasswordHash())) {
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
}
