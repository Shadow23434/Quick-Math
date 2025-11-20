package com.mathspeed.infrastructure.persistence;

import com.mathspeed.domain.port.GameRepository;

import java.sql.*;
import java.util.List;
import java.util.Map;

public class GameDAOImpl extends BaseDAO implements GameRepository {

    public GameDAOImpl() {
        super();
    }

    @Override
    public void insertGame(String gameId, int totalRounds) throws Exception {
        String sql = "INSERT INTO games (id, total_rounds, status, created_at) VALUES (?, ?, 'pending', NOW())";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, gameId);
            ps.setInt(2, totalRounds);
            ps.executeUpdate();
        }
    }

    @Override
    public void insertGamePlayersByIds(String gameId, List<String> userIds) throws Exception {
        String sql = "INSERT INTO game_players (game_id, player_id, player_order) VALUES (?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < userIds.size(); i++) {
                ps.setString(1, gameId);
                ps.setString(2, userIds.get(i));
                ps.setInt(3, i + 1);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public void persistGameFinal(String gameId, Map<String, Integer> scores, Map<String, Long> totalPlayTimeMs, Map<String, List<Map<String, Object>>> roundHistory, String winnerUserId) throws Exception {
        String sqlGame = "UPDATE games SET status = 'finished', finished_at = NOW(), winner_id = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlGame)) {
            ps.setString(1, winnerUserId);
            ps.setString(2, gameId);
            ps.executeUpdate();
        }

        String sqlPlayers = "UPDATE game_players SET final_score = ?, total_time_ms = ?, result = ? WHERE game_id = ? AND player_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlPlayers)) {
            for (Map.Entry<String, Integer> entry : scores.entrySet()) {
                String playerId = entry.getKey();
                int score = entry.getValue();
                long timeMs = totalPlayTimeMs.getOrDefault(playerId, 0L);
                String result = playerId.equals(winnerUserId) ? "win" : "lose";

                ps.setInt(1, score);
                ps.setLong(2, timeMs);
                ps.setString(3, result);
                ps.setString(4, gameId);
                ps.setString(5, playerId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public void persistRound(String gameId, int roundIndex, List<Map<String, Object>> playersSummary) throws Exception {
        // Optional implementation for per-round persistence
    }
}

