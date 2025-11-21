package com.mathspeed.infrastructure.persistence;

import com.mathspeed.domain.port.GameRepository;

import java.sql.*;
import java.util.*;

public class GameDAOImpl extends BaseDAO implements GameRepository {

    public GameDAOImpl() {
        super();
    }

    @Override
    public void insertGame(String gameId, int totalRounds) throws Exception {
        String sql = "INSERT INTO games (id, total_rounds, status, created_at) VALUES (?, ?, 'pending', NOW()) " +
                "ON DUPLICATE KEY UPDATE id = id";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, gameId);
            ps.setInt(2, totalRounds);
            ps.executeUpdate();
        }
    }

    @Override
    public void insertGamePlayersByIds(String gameId, List<String> userIds) throws Exception {
        if (userIds == null || userIds.isEmpty()) return;

        String sql = "INSERT INTO game_players (game_id, player_id, joined_at) VALUES (?, ?, NOW()) " +
                "ON DUPLICATE KEY UPDATE player_id = player_id";

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
                try { conn.setAutoCommit(true); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Ensure a games row exists. If it doesn't, insert a minimal row.
     */
    private void ensureGameExists(Connection conn, String gameId, Integer totalRounds, Long startedAtMs) throws SQLException {
        // Insert or no-op if already present
        String sql = "INSERT INTO games (id, total_rounds, status, created_at, started_at) " +
                "VALUES (?, ?, 'pending', NOW(), ?) " +
                "ON DUPLICATE KEY UPDATE id = id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, gameId);
            if (totalRounds != null) ps.setInt(2, totalRounds); else ps.setInt(2, 0);
            if (startedAtMs != null) ps.setTimestamp(3, new Timestamp(startedAtMs)); else ps.setTimestamp(3, null);
            ps.executeUpdate();
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

        // Prepare SQL statements
        String ensurePlayerSql = "INSERT INTO game_players (game_id, player_id, joined_at) VALUES (?, ?, NOW()) " +
                "ON DUPLICATE KEY UPDATE player_id = player_id";
        String updateGameSql = "UPDATE games SET status = 'finished', ended_at = NOW(), total_rounds = ? WHERE id = ?";
        String updatePlayerSql = "UPDATE game_players SET final_score = ?, total_time = ?, result = ? WHERE game_id = ? AND player_id = ?";
        String insertRoundSql = "INSERT INTO game_rounds (game_id, round_index, player_id, correct, round_play_time_ms, timestamp_ms) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ensurePlayerStmt = conn.prepareStatement(ensurePlayerSql);
             PreparedStatement updGame = conn.prepareStatement(updateGameSql);
             PreparedStatement updPlayer = conn.prepareStatement(updatePlayerSql);
             PreparedStatement insRound = conn.prepareStatement(insertRoundSql)) {

            conn.setAutoCommit(false);
            try {
                ensureGameExists(conn, gameId, scores != null ? scores.size() : null, null);

                Set<String> participants = new HashSet<>();
                if (scores != null) participants.addAll(scores.keySet());
                if (roundHistory != null) participants.addAll(roundHistory.keySet());
                for (String pid : participants) {
                    ensurePlayerStmt.setString(1, gameId);
                    ensurePlayerStmt.setString(2, pid);
                    ensurePlayerStmt.addBatch();
                }
                if (!participants.isEmpty()) ensurePlayerStmt.executeBatch();

                // 2) Update games to finished and set total_rounds to size of roundHistory or existing totalRounds
                int totalRounds = 0;
                // Try infer totalRounds from roundHistory sizes (max round_index+1)
                if (roundHistory != null && !roundHistory.isEmpty()) {
                    int maxIdx = -1;
                    for (List<Map<String, Object>> rh : roundHistory.values()) {
                        if (rh == null) continue;
                        for (Map<String, Object> e : rh) {
                            Number idx = (Number) e.get("round_index");
                            if (idx != null) maxIdx = Math.max(maxIdx, idx.intValue());
                        }
                    }
                    if (maxIdx >= 0) totalRounds = maxIdx + 1;
                }
                // fallback: set to 0 if undetermined
                updGame.setInt(1, totalRounds);
                updGame.setString(2, gameId);
                updGame.executeUpdate();

                // 3) Update each player's final_score, total_time, result
                if (scores != null) {
                    for (Map.Entry<String, Integer> e : scores.entrySet()) {
                        String userId = e.getKey();
                        int finalScore = e.getValue();
                        long totalTime = totalPlayTimeMs != null ? totalPlayTimeMs.getOrDefault(userId, 0L) : 0L;

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
                }

                // 4) Insert per-round history rows
                if (roundHistory != null && !roundHistory.isEmpty()) {
                    for (Map.Entry<String, List<Map<String, Object>>> ph : roundHistory.entrySet()) {
                        String userId = ph.getKey();
                        List<Map<String, Object>> rounds = ph.getValue();
                        if (rounds == null || rounds.isEmpty()) continue;
                        for (Map<String, Object> r : rounds) {
                            Number idxN = (Number) r.get("round_index");
                            Object corrObj = r.get("correct");
                            Boolean correctB = null;
                            if (corrObj instanceof Boolean) correctB = (Boolean) corrObj;
                            else if (corrObj instanceof Number) correctB = ((Number) corrObj).intValue() != 0;
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
                try { conn.setAutoCommit(true); } catch (Exception ignored) {}
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
                // Ensure game exists and players exist
                ensureGameExists(conn, gameId, null, null);
                for (Map<String, Object> p : playersSummary) {
                    String playerId = String.valueOf(p.get("id"));
                    Boolean correctB = (Boolean) p.get("correct");
                    Number playTimeN = (Number) p.get("round_play_time_ms");
                    Number tsN = (Number) p.get("timestamp");

                    boolean correct = correctB != null && correctB;
                    long playTime = playTimeN != null ? playTimeN.longValue() : 0L;
                    long ts = tsN != null ? tsN.longValue() : System.currentTimeMillis();

                    // ensure player row exists in game_players
                    try (PreparedStatement ensurePlayer = conn.prepareStatement(
                            "INSERT INTO game_players (game_id, player_id, joined_at) VALUES (?, ?, NOW()) " +
                                    "ON DUPLICATE KEY UPDATE player_id = player_id")) {
                        ensurePlayer.setString(1, gameId);
                        ensurePlayer.setString(2, playerId);
                        ensurePlayer.executeUpdate();
                    }

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
                try { conn.setAutoCommit(true); } catch (Exception ignored) {}
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