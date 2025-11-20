package com.mathspeed.controller;

import com.mathspeed.client.SceneManager;
import com.mathspeed.util.ReloadManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProfileController {
    private static final Logger logger = LoggerFactory.getLogger(ProfileController.class);
    @FXML private Button reloadButton;
    // Profile Info
    @FXML private Label profileNameLabel;
    @FXML private Label profileEmailLabel;

    // Stats
    @FXML private Label totalQuizzesLabel;
    @FXML private Label gamesPlayedLabel;
    @FXML private Label winsLabel;
    @FXML private Label friendsCountLabel;

    // Bottom AppBar
    @FXML private FontIcon homeIcon;
    @FXML private Label homeLabel;
    @FXML private FontIcon libraryIcon;
    @FXML private Label libraryLabel;
    @FXML private FontIcon friendsIcon;
    @FXML private Label friendsLabel;
    @FXML private FontIcon profileIcon;
    @FXML private Label profileLabel;
    private String username;

    @FXML
    public void initialize() {
        setupReloadShortcut();
        javafx.application.Platform.runLater(() -> setActiveScreen("profile"));
        loadProfileData();
    }

    private void setupReloadShortcut() {
        if (reloadButton != null && reloadButton.getScene() != null) {
            reloadButton.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.F5) {
                    handleReload();
                    event.consume();
                }
            });
        }
    }

    public void setUsername(String username) {
        this.username = username;

        if (profileNameLabel != null) {
            profileNameLabel.setText(username);
        }
        if (profileEmailLabel != null) {
            profileEmailLabel.setText("@" + username.toLowerCase());
        }
    }

    private void setActiveScreen(String screen) {
        if (homeIcon == null || libraryIcon == null || friendsIcon == null || profileIcon == null) {
            return;
        }

        // Remove active styles
        homeIcon.getStyleClass().removeAll("bottom-appbar-icon-active");
        homeLabel.getStyleClass().removeAll("bottom-appbar-label-active");
        libraryIcon.getStyleClass().removeAll("bottom-appbar-icon-active");
        libraryLabel.getStyleClass().removeAll("bottom-appbar-label-active");
        friendsIcon.getStyleClass().removeAll("bottom-appbar-icon-active");
        friendsLabel.getStyleClass().removeAll("bottom-appbar-label-active");
        profileIcon.getStyleClass().removeAll("bottom-appbar-icon-active");
        profileLabel.getStyleClass().removeAll("bottom-appbar-label-active");

        // Add active style to selected
        switch (screen) {
            case "profile":
                profileIcon.getStyleClass().add("bottom-appbar-icon-active");
                profileLabel.getStyleClass().add("bottom-appbar-label-active");
                break;
            case "home":
                homeIcon.getStyleClass().add("bottom-appbar-icon-active");
                homeLabel.getStyleClass().add("bottom-appbar-label-active");
                break;
            case "library":
                libraryIcon.getStyleClass().add("bottom-appbar-icon-active");
                libraryLabel.getStyleClass().add("bottom-appbar-label-active");
                break;
            case "friends":
                friendsIcon.getStyleClass().add("bottom-appbar-icon-active");
                friendsLabel.getStyleClass().add("bottom-appbar-label-active");
                break;
        }
    }

    private void loadProfileData() {
        logger.info("Loading profile data for: " + username);
    }

    @FXML
    private void handleChangePassword() {
        logger.info("Change password clicked");
    }

    @FXML
    private void handlePrivacySettings() {
        logger.info("Privacy settings clicked");
    }

    @FXML
    private void handleLogout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Logout");
        alert.setHeaderText("Are you sure you want to logout?");
        alert.setContentText("You will be redirected to the login page.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                SceneManager.getInstance().switchToLogin();
            }
        });
    }

    @FXML
    private void handleReload() {
        ReloadManager.reloadCurrentScene();
    }

    @FXML
    private void handleNotifications() {
        com.mathspeed.client.SceneManager sceneManager = com.mathspeed.client.SceneManager.getInstance();
        sceneManager.navigate(com.mathspeed.client.SceneManager.Screen.FRIENDS);
        javafx.application.Platform.runLater(() -> {
            Object controller = sceneManager.getController(com.mathspeed.client.SceneManager.Screen.FRIENDS);
            if (controller instanceof FriendsController fc) {
                fc.showRequestsImmediately();
            } else {
                logger.warn("FriendsController not available immediately after navigation");
            }
        });
    }
}
