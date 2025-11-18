package com.mathspeed.dao.impl;

import com.mathspeed.dao.BaseDAO;
import com.mathspeed.dao.PlayerDAO;
import com.mathspeed.model.Player;

import java.sql.*;
import java.time.LocalDateTime;
import org.mindrot.jbcrypt.BCrypt;

public class PlayerDAOImpl extends BaseDAO implements PlayerDAO {

    public PlayerDAOImpl() {
        super();
    }

    public String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(12));
    }

    public boolean checkPassword(String plain, String hashed) {
        return BCrypt.checkpw(plain, hashed);
    }

    public boolean insertPlayer(Player player) throws SQLException {
        String sql = "INSERT INTO players (id, username, display_name, password_hash, gender, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, player.getId());
            stmt.setString(2, player.getUsername());
            stmt.setString(3, player.getDisplayName());
            stmt.setString(4, player.getPasswordHash());
            stmt.setString(5, player.getGender());
            stmt.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));

            int affected = stmt.executeUpdate();
            return affected == 1;
        } catch (SQLIntegrityConstraintViolationException e) {
            // username đã tồn tại
            return false;
        }
    }

    public Player findPlayer(String username, String password) throws SQLException {
        String sql = "SELECT * FROM players WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("User " + username + " not found.");
                }

                String hashed = rs.getString("password_hash");
                if (!checkPassword(password, hashed)) {
                    throw new SQLException("Invalid password for user " + username);
                }

                Player p = new Player();
                p.setId(rs.getString("id"));
                p.setUsername(rs.getString("username"));
                p.setDisplayName(rs.getString("display_name"));
                p.setGender(rs.getString("gender"));
                p.setAvatarUrl(rs.getString("avatar_url"));

                System.out.println("Player " + username + " found in database.");
                return p;
            }
        }
    }

    public void updateLastLogin(String username) throws SQLException {
        String sql = "UPDATE players SET created_at = ? WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setString(2, username);
            stmt.executeUpdate();
        }
    }
}
