package com.mathspeed.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LobbyController {
    private static final Logger logger = LoggerFactory.getLogger(LobbyController.class);

    @FXML private Label welcomeLabel;

    private String username;

    public void setUsername(String username) {
        this.username = username;
        if (welcomeLabel != null) {
            welcomeLabel.setText("Xin chào, " + username + "!");
        }
        logger.info("Lobby loaded for user: {}", username);
    }

    @FXML
    public void initialize() {
        logger.info("LobbyController initialized");
    }
}