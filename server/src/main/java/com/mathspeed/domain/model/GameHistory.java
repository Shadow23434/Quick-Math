package com.mathspeed.domain.model;


import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;


@Entity
@Table(name = "game_history")
public class GameHistory {

    @EmbeddedId
    private GameHistoryId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("matchId")
    @JoinColumn(name = "match_id", referencedColumnName = "id", nullable = false)
    private GameMatch match;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("playerId")
    @JoinColumn(name = "player_id", referencedColumnName = "id", nullable = false)
    private Player player;

    @Column(name = "final_score")
    private int finalScore;

    @Column(name = "total_time")
    private long totalTime;

    // result: 'win','lose','draw'
    @Column(name = "result", length = 8)
    private String result;

    public GameHistory() { }

    public GameHistory(GameMatch match, Player player) {
        this.match = match;
        this.player = player;
        this.id = new GameHistoryId(match.getId(), player.getId());
    }

    // getters/setters

    public GameHistoryId getId() { return id; }
    public void setId(GameHistoryId id) { this.id = id; }

    public GameMatch getMatch() { return match; }
    public void setMatch(GameMatch match) { this.match = match; }

    public Player getPlayer() { return player; }
    public void setPlayer(Player player) { this.player = player; }

    public int getFinalScore() { return finalScore; }
    public void setFinalScore(int finalScore) { this.finalScore = finalScore; }

    public long getTotalTime() { return totalTime; }
    public void setTotalTime(long totalTime) { this.totalTime = totalTime; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GameHistory that = (GameHistory) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "GameHistory{" +
                "matchId=" + (id != null ? id.getMatchId() : "null") +
                ", playerId=" + (id != null ? id.getPlayerId() : "null") +
                ", finalScore=" + finalScore +
                ", result='" + result + '\'' +
                '}';
    }
}