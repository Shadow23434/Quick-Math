package com.mathspeed.dao.impl;

import com.mathspeed.dao.BaseDAO;
import com.mathspeed.dao.GameDAO;

import javax.sql.DataSource;
import java.sql.*;
import java.util.List;

/**
 * JDBC implementation of GameDAO for the minimal schema.
 *
 * Assumes tables:
 *  - games (id CHAR(36), total_rounds INT, status..., started_at, ended_at)
 *  - game_players (game_id, user_id, final_score, total_time, result, joined_at, left_at)
 *
 * Uses transactional updates in persistGameFinal.
 */
public class GameDAOImpl extends BaseDAO implements GameDAO {

    public GameDAOImpl(){
        super();
    }

    @Override
    public void insertGame(String gameId, int totalRounds) throws Exception {
        final String sql = "INSERT INTO games (id, total_rounds, status, created_at) VALUES (?, ?, 'running', CURRENT_TIMESTAMP)";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, gameId);
            ps.setInt(2, totalRounds);
            ps.executeUpdate();
        }
    }

    @Override
    public void insertGamePlayersByIds(String gameId, List<String> userIds) throws Exception {
        if (userIds == null || userIds.isEmpty()) return;
        final String sql = "INSERT INTO game_players (game_id, user_id, joined_at, final_score, total_time, result) " +
                "VALUES (?, ?, CURRENT_TIMESTAMP, 0, 0, NULL)";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (String uid : userIds) {
                ps.setString(1, gameId);
                ps.setString(2, uid);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public void persistGameFinal(String gameId, List<GameDAO.PlayerSummary> players) throws Exception {
        if (players == null || players.isEmpty()) return;

        final String updatePlayerSql = "UPDATE game_players SET final_score = ?, total_time = ?, result = ?, left_at = COALESCE(left_at, CURRENT_TIMESTAMP) " +
                "WHERE game_id = ? AND user_id = ?";
        final String updateGameSql = "UPDATE games SET status = 'finished', ended_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement psPlayer = c.prepareStatement(updatePlayerSql);
                 PreparedStatement psGame = c.prepareStatement(updateGameSql)) {

                for (GameDAO.PlayerSummary p : players) {
                    psPlayer.setInt(1, p.finalScore);
                    psPlayer.setLong(2, p.totalTimeMs);
                    psPlayer.setString(3, p.result);
                    psPlayer.setString(4, gameId);
                    psPlayer.setString(5, p.userId);
                    psPlayer.addBatch();
                }
                psPlayer.executeBatch();

                psGame.setString(1, gameId);
                psGame.executeUpdate();

                c.commit();
            } catch (SQLException e) {
                try { c.rollback(); } catch (SQLException ignore) {}
                throw e;
            } finally {
                try { c.setAutoCommit(true); } catch (SQLException ignore) {}
            }
        }
    }
}