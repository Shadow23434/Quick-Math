package com.mathspeed.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_history")
public class GameHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "player1_id", nullable = false)
    private Integer player1Id;

    @Column(name = "player2_id", nullable = false)
    private Integer player2Id;

    @Column(name = "player1_score")
    private Integer player1Score = 0;

    @Column(name = "player2_score")
    private Integer player2Score = 0;

    @Column(name = "player1_time")
    private Integer player1Time = 0;

    @Column(name = "player2_time")
    private Integer player2Time = 0;

    @Column(name = "winner_id")
    private Integer winnerId;

    @Column(name = "question_count")
    private Integer questionCount = 0;

    @Column(name = "played_at")
    private LocalDateTime playedAt;

    @PrePersist
    protected void onCreate() {
        playedAt = LocalDateTime.now();
    }

    // Constructors
    public GameHistory() {}

    public GameHistory(Integer player1Id, Integer player2Id) {
        this.player1Id = player1Id;
        this.player2Id = player2Id;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getPlayer1Id() { return player1Id; }
    public void setPlayer1Id(Integer player1Id) { this.player1Id = player1Id; }

    public Integer getPlayer2Id() { return player2Id; }
    public void setPlayer2Id(Integer player2Id) { this.player2Id = player2Id; }

    public Integer getPlayer1Score() { return player1Score; }
    public void setPlayer1Score(Integer player1Score) { this.player1Score = player1Score; }

    public Integer getPlayer2Score() { return player2Score; }
    public void setPlayer2Score(Integer player2Score) { this.player2Score = player2Score; }

    public Integer getPlayer1Time() { return player1Time; }
    public void setPlayer1Time(Integer player1Time) { this.player1Time = player1Time; }

    public Integer getPlayer2Time() { return player2Time; }
    public void setPlayer2Time(Integer player2Time) { this.player2Time = player2Time; }

    public Integer getWinnerId() { return winnerId; }
    public void setWinnerId(Integer winnerId) { this.winnerId = winnerId; }

    public Integer getQuestionCount() { return questionCount; }
    public void setQuestionCount(Integer questionCount) { this.questionCount = questionCount; }

    public LocalDateTime getPlayedAt() { return playedAt; }
    public void setPlayedAt(LocalDateTime playedAt) { this.playedAt = playedAt; }
}
