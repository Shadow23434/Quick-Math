package com.mathspeed.controller;

import com.mathspeed.client.SceneManager;
import com.mathspeed.client.SessionManager;
import com.mathspeed.model.Player;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HeaderController {
    private static final Logger logger = LoggerFactory.getLogger(HeaderController.class);
    @FXML private Button notificationButton;
    @FXML private ImageView avatarImageView;
    @FXML private Label disPlayNameLabel;
    @FXML private Label userNameLabel;
    private Player currentPlayer;

    @FXML
    public void initialize() {
        // refresh current player from session
        currentPlayer = SessionManager.getInstance().getCurrentPlayer();

        Platform.runLater(() -> {
            applyCSSToComponents();
            if (currentPlayer != null) {
                updateUserDisplay(
                        currentPlayer.getAvatarUrl(),
                        currentPlayer.getDisplayName(),
                        currentPlayer.getUsername()
                );
            }
        });
    }

    private void applyCSSToComponents() {
        if (avatarImageView != null) {
            Parent root = avatarImageView.getParent();
            while (root != null && root.getParent() != null) {
                root = root.getParent();
                if (root.getStyleClass().contains("header-section")) {
                    root.applyCss();
                    root.layout();
                    break;
                }
            }
        }

        // Apply CSS to individual UI components (not model)
        if (disPlayNameLabel != null) disPlayNameLabel.applyCss();
        if (userNameLabel != null) userNameLabel.applyCss();
        if (notificationButton != null) notificationButton.applyCss();
    }

    private void updateUserDisplay(String avatarUrl, String displayName, String username) {
        Platform.runLater(() -> {
            if (avatarImageView != null && avatarUrl != null && !avatarUrl.isEmpty()) {
                try {
                    Image image = new Image(avatarUrl, true);
                    avatarImageView.setImage(image);
                } catch (Exception e) {
                    logger.warn("Failed to load avatar from URL: {}", avatarUrl, e);
                }
            }
            if (username != null && !username.isEmpty() && userNameLabel != null) {
                userNameLabel.setText("@" + username);
            }
            if (displayName != null && !displayName.isEmpty() && disPlayNameLabel != null) {
                disPlayNameLabel.setText("Hi, " + displayName);
            }
        });
    }

    @FXML
    private void handleProfile() {
        SceneManager.getInstance().navigate(SceneManager.Screen.PROFILE);
    }

    @FXML
    private void handleNotifications() {
        SceneManager sceneManager = SceneManager.getInstance();
        Player player = SessionManager.getInstance().getCurrentPlayer();
        String username = (player == null) ? null : player.getUsername();

        if (username == null || username.trim().isEmpty()) {
            logger.warn("Cannot navigate to Friends - username not available");
            return;
        }

        sceneManager.navigate(SceneManager.Screen.FRIENDS);

        Platform.runLater(() -> {
            Object controller = sceneManager.getController(SceneManager.Screen.FRIENDS);
            if (controller instanceof com.mathspeed.controller.FriendsController fc) {
                fc.showRequestsImmediately();
            } else {
                logger.warn("FriendsController not available immediately after navigation");
            }
        });
    }
}
