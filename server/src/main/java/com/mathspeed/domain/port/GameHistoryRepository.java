package com.mathspeed.domain.port;

public interface GameHistoryRepository {
    int getTotalWins(String playerId);
    int getTotalGames(String playerId);
}
