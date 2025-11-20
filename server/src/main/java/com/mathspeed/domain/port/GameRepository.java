package com.mathspeed.domain.port;
import java.util.List;
import java.util.Map;
public interface GameRepository {
    void insertGame(String gameId, int totalRounds) throws Exception;
    void insertGamePlayersByIds(String gameId, List<String> userIds) throws Exception;
    void persistGameFinal(String gameId, Map<String, Integer> scores, Map<String, Long> totalPlayTimeMs, Map<String, List<Map<String, Object>>> roundHistory, String winnerUserId) throws Exception;
    default void persistRound(String gameId, int roundIndex, List<Map<String, Object>> playersSummary) throws Exception {}
}
