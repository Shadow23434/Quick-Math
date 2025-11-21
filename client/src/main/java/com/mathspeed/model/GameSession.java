package com.mathspeed.model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Lightweight session model to hold current username and score; JavaFX properties so controllers can bind.
 */
public class GameSession {
    private final StringProperty username = new SimpleStringProperty("");
    private final IntegerProperty score = new SimpleIntegerProperty(0);

    public StringProperty usernameProperty() { return username; }
    public String getUsername() { return username.get(); }
    public void setUsername(String u) { this.username.set(u); }

    public IntegerProperty scoreProperty() { return score; }
    public int getScore() { return score.get(); }
    public void setScore(int s) { this.score.set(s); }
}

