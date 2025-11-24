package com.mathspeed.domain.model;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import org.apache.logging.log4j.core.config.plugins.convert.TypeConverters;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

@Entity
@Table(name = "quizzes", indexes = {
        @Index(name = "ix_quiz_player", columnList = "player_id")
})
public class Quiz {
    @Id
    @Column(name = "id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "question_number", nullable = false)
    private int questionNumber;

    @Column(name = "player_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String playerId;

    @Convert(converter = TypeConverters.LevelConverter.class)
    @Column(name = "level", nullable = false, columnDefinition = "ENUM('easy','medium','hard') DEFAULT 'easy'")
    private String level = "easy";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Quiz() {}

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getQuestionNumber() {
        return questionNumber;
    }

    public void setQuestionNumber(int questionNumber) {
        this.questionNumber = questionNumber;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
