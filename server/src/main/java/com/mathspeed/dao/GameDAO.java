package com.mathspeed.dao;

import java.util.List;
import java.util.Map;

/**
 * DAO interface for QuickMath persistence operations required by GameSession.
 *
 * Notes on types:
 * - user IDs are application-level identifiers (String) — avoid passing network-layer objects (ClientHandler) into DAO APIs.
 * - scores: Map<userId, finalScore>
 * - totalPlayTimeMs: Map<userId, totalPlayTimeInMilliseconds>
 * - roundHistory: Map<userId, List<Map<String,Object>>> where each inner map represents a round:
 *     {
 *       "round_index": Integer,
 *       "correct": Boolean,
 *       "round_play_time_ms": Long,
 *       "timestamp": Long
 *     }
 *
 * Implementations should perform transactional updates where appropriate.
 */
public interface GameDAO {

    /**
     * Insert a new game record (minimal info).
     *
     * @param gameId      unique id for the game/session
     * @param totalRounds number of rounds in the game
     * @throws Exception on persistence errors
     */
    void insertGame(String gameId, int totalRounds) throws Exception;

    /**
     * Insert player->game relationships using user ids.
     *
     * @param gameId  the game/session id
     * @param userIds ordered list of user ids participating in the game
     * @throws Exception on persistence errors
     */
    void insertGamePlayersByIds(String gameId, List<String> userIds) throws Exception;

    /**
     * Persist the final game result including per-player final scores, total play times and per-round history.
     *
     * @param gameId           game/session id
     * @param scores           map of userId -> final score
     * @param totalPlayTimeMs  map of userId -> total play time in milliseconds
     * @param roundHistory     map of userId -> list of per-round maps (round_index, correct, round_play_time_ms, timestamp)
     * @param winnerUserId     userId of the winner (nullable for draw)
     * @throws Exception on persistence errors
     */
    void persistGameFinal(String gameId,
                          Map<String, Integer> scores,
                          Map<String, Long> totalPlayTimeMs,
                          Map<String, List<Map<String, Object>>> roundHistory,
                          String winnerUserId) throws Exception;

    /**
     * Optional: persist a single round summary incrementally.
     * Implementations can choose to persist per-round data as rounds complete.
     *
     * @param gameId         game/session id
     * @param roundIndex     zero-based round index
     * @param playersSummary list of per-player summary maps (id, username, correct, round_play_time_ms, total_score, total_play_time_ms)
     * @throws Exception on persistence errors
     */
    default void persistRound(String gameId, int roundIndex, List<Map<String, Object>> playersSummary) throws Exception {
        // default no-op; implementations may override to support incremental persistence
    }
}