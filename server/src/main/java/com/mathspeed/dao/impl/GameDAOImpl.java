package com.mathspeed.dao.impl;

import com.mathspeed.dao.BaseDAO;
import com.mathspeed.dao.GameDAO;

import java.sql.*;
import java.util.List;
import java.util.Map;

/**
 * JDBC implementation of GameDAO for the provided schema.
 *
 * This implementation:
 * - inserts games and game_players,
 * - marks game finished and updates game_players final_score/total_time/result,
 * - persists per-round history into game_rounds table (created on demand).
 *
 * It matches the GameDAO interface that accepts primitive-friendly types (userId strings,
 * per-player maps and per-round maps).
 */
public class GameDAOImpl extends BaseDAO implements GameDAO {

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
        String sql = "INSERT INTO game_players (game_id, player_id, joined_at) VALUES (?, ?, NOW())";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);
            try {
                for (String userId : userIds) {
                    ps.setString(1, gameId);
                    ps.setString(2, userId);
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    @Override
    public void persistGameFinal(String gameId,
                                 Map<String, Integer> scores,
                                 Map<String, Long> totalPlayTimeMs,
                                 Map<String, List<Map<String, Object>>> roundHistory,
                                 String winnerUserId) throws Exception {
        // Ensure the auxiliary table exists
        ensureGameRoundsTable();

        String updateGameSql = "UPDATE games SET status = 'finished', ended_at = NOW() WHERE id = ?";
        String updatePlayerSql = "UPDATE game_players SET final_score = ?, total_time = ?, result = ? WHERE game_id = ? AND player_id = ?";
        String insertRoundSql = "INSERT INTO game_rounds (game_id, round_index, player_id, correct, round_play_time_ms, timestamp_ms) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement updGame = conn.prepareStatement(updateGameSql);
             PreparedStatement updPlayer = conn.prepareStatement(updatePlayerSql);
             PreparedStatement insRound = conn.prepareStatement(insertRoundSql)) {

            conn.setAutoCommit(false);
            try {
                // mark game finished
                updGame.setString(1, gameId);
                updGame.executeUpdate();

                // update each player's final row in game_players
                for (Map.Entry<String, Integer> e : scores.entrySet()) {
                    String userId = e.getKey();
                    int finalScore = e.getValue();
                    long totalTime = totalPlayTimeMs.getOrDefault(userId, 0L);

                    String result;
                    if (winnerUserId != null) {
                        if (winnerUserId.equals(userId)) result = "win";
                        else result = "lose";
                    } else {
                        // draw when no winner provided
                        result = "draw";
                    }

                    updPlayer.setInt(1, finalScore);
                    updPlayer.setLong(2, totalTime);
                    updPlayer.setString(3, result);
                    updPlayer.setString(4, gameId);
                    updPlayer.setString(5, userId);
                    updPlayer.addBatch();
                }
                updPlayer.executeBatch();

                // insert per-round history rows
                if (roundHistory != null && !roundHistory.isEmpty()) {
                    for (Map.Entry<String, List<Map<String, Object>>> ph : roundHistory.entrySet()) {
                        String userId = ph.getKey();
                        List<Map<String, Object>> rounds = ph.getValue();
                        if (rounds == null || rounds.isEmpty()) continue;
                        for (Map<String, Object> r : rounds) {
                            Number idxN = (Number) r.get("round_index");
                            Boolean correctB = (Boolean) r.get("correct");
                            Number playTimeN = (Number) r.get("round_play_time_ms");
                            Number tsN = (Number) r.get("timestamp");

                            int roundIndex = idxN != null ? idxN.intValue() : -1;
                            boolean correct = correctB != null && correctB;
                            long playTime = playTimeN != null ? playTimeN.longValue() : 0L;
                            long ts = tsN != null ? tsN.longValue() : System.currentTimeMillis();

                            insRound.setString(1, gameId);
                            insRound.setInt(2, roundIndex);
                            insRound.setString(3, userId);
                            insRound.setBoolean(4, correct);
                            insRound.setLong(5, playTime);
                            insRound.setLong(6, ts);
                            insRound.addBatch();
                        }
                    }
                    insRound.executeBatch();
                }

                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    @Override
    public void persistRound(String gameId, int roundIndex, List<Map<String, Object>> playersSummary) throws Exception {
        ensureGameRoundsTable();
        String insertRoundSql = "INSERT INTO game_rounds (game_id, round_index, player_id, correct, round_play_time_ms, timestamp_ms) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(insertRoundSql)) {
            conn.setAutoCommit(false);
            try {
                for (Map<String, Object> p : playersSummary) {
                    String playerId = String.valueOf(p.get("id"));
                    Boolean correctB = (Boolean) p.get("correct");
                    Number playTimeN = (Number) p.get("round_play_time_ms");
                    Number tsN = (Number) p.get("timestamp");

                    boolean correct = correctB != null && correctB;
                    long playTime = playTimeN != null ? playTimeN.longValue() : 0L;
                    long ts = tsN != null ? tsN.longValue() : System.currentTimeMillis();

                    ps.setString(1, gameId);
                    ps.setInt(2, roundIndex);
                    ps.setString(3, playerId);
                    ps.setBoolean(4, correct);
                    ps.setLong(5, playTime);
                    ps.setLong(6, ts);
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private void ensureGameRoundsTable() throws SQLException {
        String ddl = "CREATE TABLE IF NOT EXISTS game_rounds (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "game_id CHAR(36) NOT NULL," +
                "round_index INT NOT NULL," +
                "player_id CHAR(36) NOT NULL," +
                "correct TINYINT(1) NOT NULL DEFAULT 0," +
                "round_play_time_ms BIGINT NOT NULL DEFAULT 0," +
                "timestamp_ms BIGINT NOT NULL," +
                "INDEX ix_gr_game (game_id)," +
                "INDEX ix_gr_player (player_id)," +
                "CONSTRAINT fk_gr_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE ON UPDATE CASCADE," +
                "CONSTRAINT fk_gr_player FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE RESTRICT ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        try (Connection conn = getConnection();
             Statement st = conn.createStatement()) {
            st.execute(ddl);
        }
    }
}