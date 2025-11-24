package com.mathspeed.domain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import com.mathspeed.util.UuidUtil;

@Entity
@Table(name = "players")
public class Player {
    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "username", unique = true, nullable = false, length = 64)
    private String username;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "gender", columnDefinition = "ENUM('male','female','other') DEFAULT 'male'")
    private String gender = "male";

    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;

    @Column(name = "country_code", length = 255)
    private String countryCode;

    @Column(name = "status", columnDefinition = "ENUM('online','offline','in_game') DEFAULT 'offline'")
    private String status = "offline";

    @Column(name = "last_active_at", nullable = false)
    private LocalDateTime lastActiveAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Player() { }

    public Player(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UuidUtil.randomUuid();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.gender == null) {
            this.gender = "male";
        }
        if (this.avatarUrl == null) {
            this.avatarUrl = "https://tse1.mm.bing.net/th/id/OIP.pLa0MvBoBWBLYBwKtdbLhQAAAA?rs=1&pid=ImgDetMain&o=7&rm=3";
        }
        if (this.status == null) {
            this.status = "offline";
        }
        if (this.countryCode == null) {
            this.countryCode = "vn";
        }
    }

    // ---------------------
    // Getters & Setters
    // ---------------------

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(LocalDateTime lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
