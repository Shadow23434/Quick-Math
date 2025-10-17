package com.mathspeed.controller;

import com.mathspeed.util.ReloadManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LobbyController {
    private static final Logger logger = LoggerFactory.getLogger(LobbyController.class);

    @FXML private Label welcomeLabel;
    @FXML private Button reloadButton;

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
        setupReloadShortcut();
    }

    private void setupReloadShortcut() {
        // Setup F5 key for reload
        if (reloadButton != null && reloadButton.getScene() != null) {
            reloadButton.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.F5) {
                    handleReload();
                    event.consume();
                }
            });
        }
    }

    @FXML
    private void handleReload() {
        logger.info("Reload button clicked - reloading scene");
        ReloadManager.reloadCurrentScene();
    }
}