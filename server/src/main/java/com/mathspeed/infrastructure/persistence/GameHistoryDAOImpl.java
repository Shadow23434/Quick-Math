package com.mathspeed.infrastructure.persistence;

import com.mathspeed.domain.port.GameHistoryRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class GameHistoryDAOImpl extends BaseDAO implements GameHistoryRepository {
    @Override
    public int getTotalWins(String playerId) {
        String sql = "SELECT COUNT(*) AS total FROM game_history WHERE player_id = ? AND result = 'win'";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("total");
                return 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    @Override
    public int getTotalGames(String playerId) {
        String sql = "SELECT COUNT(*) AS total FROM game_history WHERE player_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("total");
                return 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }
}
