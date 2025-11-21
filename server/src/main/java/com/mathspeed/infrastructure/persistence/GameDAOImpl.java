package com.mathspeed.infrastructure.persistence;

import com.mathspeed.domain.model.GameHistory;
import com.mathspeed.domain.model.GameMatch;
import com.mathspeed.domain.port.GameRepository;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class GameDAOImpl extends BaseDAO implements GameRepository {

    public GameDAOImpl() {
        super();
    }

    @Override
    public void insertGame(String matchId, int totalRounds) throws Exception {
        String sql = "INSERT INTO matches (id, total_rounds, status, created_at) VALUES (?, ?, 'pending', NOW()) " +
                "ON DUPLICATE KEY UPDATE id = id";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, matchId);
            ps.setInt(2, totalRounds);
            ps.executeUpdate();
        }
    }

    @Override
    public void insertGamePlayersByIds(String matchId, List<String> userIds) throws Exception {
        if (userIds == null || userIds.isEmpty()) return;

        String sql = "INSERT INTO game_history (match_id, player_id, joined_at) VALUES (?, ?, NOW()) " +
                "ON DUPLICATE KEY UPDATE player_id = player_id";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);
            try {
                for (String userId : userIds) {
                    ps.setString(1, matchId);
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

    public void ensureMatchExists(String matchId, Integer totalRounds, Timestamp startedAt) throws SQLException {
        try (Connection conn = getConnection()) {
            ensureMatchExists(conn, matchId, totalRounds, startedAt);
        }
    }

    private void ensureMatchExists(Connection conn, String matchId, Integer totalRounds, Timestamp startedAt) throws SQLException {
        String sql = "INSERT INTO matches (id, total_rounds, status, created_at, started_at) " +
                "VALUES (?, ?, 'pending', NOW(), ?) " +
                "ON DUPLICATE KEY UPDATE id = id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, matchId);
            ps.setInt(2, totalRounds != null ? totalRounds : 0);
            if (startedAt != null) ps.setTimestamp(3, startedAt); else ps.setTimestamp(3, null);
            ps.executeUpdate();
        }
    }

    @Override
    public void persistGameFinal(GameMatch match,
                                 List<GameHistory> histories,
                                 Map<String, List<Map<String, Object>>> roundHistory) throws Exception {
        if (match == null) throw new IllegalArgumentException("match is required");
        ensureGameRoundsTable();

        boolean previousAuto = true;
        try (Connection conn = getConnection()) {
            previousAuto = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // Ensure matches row exists (use started_at if provided)
                ensureMatchExists(conn, match.getId(), match.getTotalRounds() > 0 ? match.getTotalRounds() : null,
                        match.getStartedAt() != null ? Timestamp.valueOf(match.getStartedAt()) : null);

                // Ensure game_history rows exist (insert joined_at if provided on entities)
                String ensureHistorySql = "INSERT INTO game_history (match_id, player_id) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE player_id = player_id";
                try (PreparedStatement ensureHist = conn.prepareStatement(ensureHistorySql)) {
                    if (histories != null) {
                        for (GameHistory gh : histories) {
                            String pid = gh.getPlayer() != null ? gh.getPlayer().getId() : null;
                            if (pid == null) continue;
                            ensureHist.setString(1, match.getId());
                            ensureHist.setString(2, pid);
                            ensureHist.addBatch();
                        }
                        ensureHist.executeBatch();
                    }
                }

                // Update matches -> set status finished, started_at = COALESCE(started_at, ?), ended_at = ?, total_rounds = ?
                String updMatchSql = "UPDATE matches SET status = 'finished', started_at = COALESCE(started_at, ?), ended_at = ?, total_rounds = ? WHERE id = ?";
                try (PreparedStatement updMatch = conn.prepareStatement(updMatchSql)) {
                    Timestamp startedAt = match.getStartedAt() != null ? Timestamp.valueOf(match.getStartedAt()) : null;
                    Timestamp endedAt = match.getEndedAt() != null ? Timestamp.valueOf(match.getEndedAt()) : new Timestamp(System.currentTimeMillis());
                    // If startedAt is null, COALESCE will preserve existing started_at in DB
                    updMatch.setTimestamp(1, startedAt);
                    updMatch.setTimestamp(2, endedAt);
                    updMatch.setInt(3, match.getTotalRounds());
                    updMatch.setString(4, match.getId());
                    updMatch.executeUpdate();
                }

                // Update game_history rows with final_score, total_time, result, left_at
                String updHistSql = "UPDATE game_history SET final_score = ?, total_time = ?, result = ? WHERE match_id = ? AND player_id = ?";
                try (PreparedStatement updHist = conn.prepareStatement(updHistSql)) {
                    if (histories != null) {
                        for (GameHistory gh : histories) {
                            String pid = gh.getPlayer() != null ? gh.getPlayer().getId() : null;
                            if (pid == null) continue;
                            updHist.setInt(1, gh.getFinalScore());
                            updHist.setLong(2, gh.getTotalTime());
                            updHist.setString(3, gh.getResult());
                            updHist.setString(4, match.getId());
                            updHist.setString(5, pid);
                            updHist.addBatch();
                        }
                        updHist.executeBatch();
                    }
                }

                // Insert per-round rows into game_rounds (batch)
                if (roundHistory != null && !roundHistory.isEmpty()) {
                    String insRoundSql = "INSERT INTO game_rounds (match_id, round_index, player_id, correct, round_play_time_ms, timestamp_ms) VALUES (?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement insRound = conn.prepareStatement(insRoundSql)) {
                        for (Map.Entry<String, List<Map<String, Object>>> ph : roundHistory.entrySet()) {
                            String playerId = ph.getKey();
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

                                insRound.setString(1, match.getId());
                                insRound.setInt(2, roundIndex);
                                insRound.setString(3, playerId);
                                insRound.setBoolean(4, correct);
                                insRound.setLong(5, playTime);
                                insRound.setLong(6, ts);
                                insRound.addBatch();
                            }
                        }
                        insRound.executeBatch();
                    }
                }

                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                try { conn.setAutoCommit(previousAuto); } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void persistRound(String matchId, int roundIndex, List<Map<String, Object>> playersSummary) throws Exception {
        ensureGameRoundsTable();
        String insertRoundSql = "INSERT INTO game_rounds (match_id, round_index, player_id, correct, round_play_time_ms, timestamp_ms) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(insertRoundSql)) {
            conn.setAutoCommit(false);
            try {
                // Ensure match exists
                ensureMatchExists(conn, matchId, null, null);

                for (Map<String, Object> p : playersSummary) {
                    String playerId = String.valueOf(p.get("id"));
                    Boolean correctB = (Boolean) p.get("correct");
                    Number playTimeN = (Number) p.get("round_play_time_ms");
                    Number tsN = (Number) p.get("timestamp");

                    boolean correct = correctB != null && correctB;
                    long playTime = playTimeN != null ? playTimeN.longValue() : 0L;
                    long ts = tsN != null ? tsN.longValue() : System.currentTimeMillis();

                    // ensure history row exists for this player in match
                    try (PreparedStatement ensureHist = conn.prepareStatement(
                            "INSERT INTO game_history (match_id, player_id, joined_at) VALUES (?, ?, NOW()) " +
                                    "ON DUPLICATE KEY UPDATE player_id = player_id")) {
                        ensureHist.setString(1, matchId);
                        ensureHist.setString(2, playerId);
                        ensureHist.executeUpdate();
                    }

                    ps.setString(1, matchId);
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
                "match_id CHAR(36) NOT NULL," +
                "round_index INT NOT NULL," +
                "player_id CHAR(36) NOT NULL," +
                "correct TINYINT(1) NOT NULL DEFAULT 0," +
                "round_play_time_ms BIGINT NOT NULL DEFAULT 0," +
                "timestamp_ms BIGINT NOT NULL," +
                "INDEX ix_gr_match (match_id)," +
                "INDEX ix_gr_player (player_id)," +
                "CONSTRAINT fk_gr_match FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE ON UPDATE CASCADE," +
                "CONSTRAINT fk_gr_player FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE RESTRICT ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        try (Connection conn = getConnection();
             Statement st = conn.createStatement()) {
            st.execute(ddl);
        }
    }

    private int inferTotalRounds(Map<String, List<Map<String, Object>>> roundHistory) {
        int maxIdx = -1;
        if (roundHistory == null) return 0;
        for (List<Map<String, Object>> rh : roundHistory.values()) {
            if (rh == null) continue;
            for (Map<String, Object> e : rh) {
                Number idx = (Number) e.get("round_index");
                if (idx != null) maxIdx = Math.max(maxIdx, idx.intValue());
            }
        }
        return maxIdx >= 0 ? maxIdx + 1 : 0;
    }

    private String determineResultForPlayer(String playerId, String winnerId) {
        if (winnerId == null) return "draw";
        return winnerId.equals(playerId) ? "win" : "lose";
    }
}