package com.mathspeed.dao;

import java.util.List;

/**
 * DAO interface for QuickMath persistence operations required by GameSession.
 *
 * This interface is tailored to the minimal schema:
 * - games (id)
 * - game_players (game_id, user_id, final_score, total_time, result)
 *
 * Implementations should perform transactional updates where appropriate.
 */
public interface GameDAO {

    /**
     * Insert a new game header row.
     *
     * @param gameId      UUID string for game.id
     * @param totalRounds number of rounds in the match
     * @throws Exception on DB error
     */
    void insertGame(String gameId, int totalRounds) throws Exception;

    /**
     * Insert game_players rows for the given user ids.
     * Implementation should insert one row per user with default final_score=0,total_time=0,result=NULL.
     *
     * @param gameId game id
     * @param userIds list of user id strings (UUID)
     * @throws Exception on DB error
     */
    void insertGamePlayersByIds(String gameId, List<String> userIds) throws Exception;

    /**
     * Persist final per-player summary for a finished game.
     * Implementation MUST run these updates in a single transaction:
     *  - update game_players set final_score=?, total_time=?, result=? for each player
     *  - update games set status='finished', ended_at = NOW()
     *
     * The method receives PlayerSummary objects containing the user id, final score, total time (ms) and result.
     *
     * @param gameId   game id
     * @param players  list of PlayerSummary for the two players
     * @throws Exception on DB error
     */
    void persistGameFinal(String gameId, List<PlayerSummary> players) throws Exception;

    /**
     * Player summary DTO used to persist final results.
     */
    class PlayerSummary {
        public final String userId;
        public final int finalScore;
        public final long totalTimeMs;
        public final String result; // "win"|"lose"|"draw"

        public PlayerSummary(String userId, int finalScore, long totalTimeMs, String result) {
            this.userId = userId;
            this.finalScore = finalScore;
            this.totalTimeMs = totalTimeMs;
            this.result = result;
        }
    }
}