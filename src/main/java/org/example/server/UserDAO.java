package org.example.server;

import javax.sql.DataSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.LocalDateTime;

public class UserDAO {
    private final DataSource dataSource;

    public UserDAO() {
        this.dataSource = DatabaseConfig.getDataSource();
    }

    public UserDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean register(String username, String password) {
        String passwordHash = hashPassword(password);
        String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, passwordHash);
            int rows = pstmt.executeUpdate();

            if (rows > 0) {
                System.out.println("✓ Saved to database: " + username);
                return true;
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) { // Duplicate key error
                System.err.println("✗ Username already exists: " + username);
            } else {
                System.err.println("✗ Database error: " + e.getMessage());
            }
        }
        return false;
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public User login(String username, String password) {
        String hashedPassword = hashPassword(password); // Hash the input password
        String sql = "SELECT * FROM users WHERE username = ? AND password_hash = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, hashedPassword); // Use hashed password for comparison

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    User user = new User(
                            rs.getString("username"),
                            rs.getString("password_hash")
                    );

                    Timestamp lastLoginTimestamp = rs.getTimestamp("last_login");
                    if (lastLoginTimestamp != null) {
                        user.setLastLogin(lastLoginTimestamp.toLocalDateTime());
                    }

                    updateLastLogin(username);
                    user.setOnline(true);
                    return user;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Login failed", e);
        }
        return null;
    }


    private void updateLastLogin(String username) {
        String sql = "UPDATE users SET last_login = ?, is_online = TRUE WHERE username = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update last login", e);
        }
    }

    public void logout(String username) {
        String sql = "UPDATE users SET is_online = FALSE WHERE username = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Logout failed", e);
        }
    }

    public void setAllUsersOffline() {
        String sql = "UPDATE users SET is_online = FALSE";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set users offline", e);
        }
    }

}