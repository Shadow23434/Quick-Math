package com.mathspeed.domain.model;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entity mapping for table `matchs` (named `GameMatch` in code).
 * Note: table name in DB is `matchs` as provided.
 */
@Entity
@Table(name = "matches")
public class GameMatch {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "created_at", columnDefinition = "DATETIME")
    private LocalDateTime createdAt;

    @Column(name = "started_at", columnDefinition = "DATETIME")
    private LocalDateTime startedAt;

    @Column(name = "ended_at", columnDefinition = "DATETIME")
    private LocalDateTime endedAt;

    @Column(name = "total_rounds")
    private int totalRounds;

    // status values: 'pending','running','finished','cancelled'
    @Column(name = "status", length = 16)
    private String status = "pending";

    public GameMatch() {
        this.createdAt = LocalDateTime.now();
    }

    public GameMatch(String id, int totalRounds) {
        this();
        this.id = id;
        this.totalRounds = totalRounds;
    }

    // getters/setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }

    public int getTotalRounds() { return totalRounds; }
    public void setTotalRounds(int totalRounds) { this.totalRounds = totalRounds; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GameMatch gameMatch = (GameMatch) o;
        return Objects.equals(id, gameMatch.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "GameMatch{" +
                "id='" + id + '\'' +
                ", status='" + status + '\'' +
                ", totalRounds=" + totalRounds +
                '}';
    }
}
