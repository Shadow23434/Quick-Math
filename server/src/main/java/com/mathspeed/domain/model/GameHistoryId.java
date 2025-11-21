package com.mathspeed.domain.model;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class GameHistoryId implements Serializable {

    private String matchId;
    private String playerId;

    public GameHistoryId() {}

    public GameHistoryId(String matchId, String playerId) {
        this.matchId = matchId;
        this.playerId = playerId;
    }

    public String getMatchId() { return matchId; }
    public void setMatchId(String matchId) { this.matchId = matchId; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GameHistoryId)) return false;
        GameHistoryId that = (GameHistoryId) o;
        return Objects.equals(matchId, that.matchId) &&
                Objects.equals(playerId, that.playerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matchId, playerId);
    }
}