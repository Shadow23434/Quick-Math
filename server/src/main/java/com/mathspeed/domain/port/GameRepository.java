package com.mathspeed.domain.port;
import com.mathspeed.domain.model.GameHistory;
import com.mathspeed.domain.model.GameMatch;

import java.util.List;
import java.util.Map;
public interface GameRepository {
    void insertGame(String gameId, int totalRounds) throws Exception;
    void insertGamePlayersByIds(String gameId, List<String> userIds) throws Exception;
    void persistGameFinal(GameMatch match,
                          List<GameHistory> histories,
                          Map<String, List<Map<String, Object>>> roundHistory) throws Exception;
    default void persistRound(String gameId, int roundIndex, List<Map<String, Object>> playersSummary) throws Exception {}
}
