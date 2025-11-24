package com.mathspeed.model;

public class Stats {
    private Integer totalQuizzes;
    private Integer gamesPlayed;
    private Integer wins;
    private Integer friends;

    public Integer getTotalQuizzes() {
        return totalQuizzes;
    }

    public void setTotalQuizzes(Integer totalQuizzes) {
        this.totalQuizzes = totalQuizzes;
    }

    public Integer getGamesPlayed() {
        return gamesPlayed;
    }

    public void setGamesPlayed(Integer gamesPlayed) {
        this.gamesPlayed = gamesPlayed;
    }

    public Integer getWins() {
        return wins;
    }

    public void setWins(Integer wins) {
        this.wins = wins;
    }

    public Integer getFriends() {
        return friends;
    }

    public void setFriends(Integer friends) {
        this.friends = friends;
    }

    @Override
    public String toString() {
        return "Stats{" +
                "totalQuizzes=" + totalQuizzes +
                ", gamesPlayed=" + gamesPlayed +
                ", wins=" + wins +
                ", friends=" + friends +
                '}';
    }
}

