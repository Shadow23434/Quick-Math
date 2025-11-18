package com.mathspeed.controller;

import com.mathspeed.client.SceneManager;
import com.mathspeed.util.ReloadManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProfileController {
    private static final Logger logger = LoggerFactory.getLogger(ProfileController.class);

    @FXML private Button settingsButton;
    @FXML private Button reloadButton;
    @FXML private ProgressIndicator loadingIndicator;

    // Profile Info
    @FXML private ImageView profileImageView;
    @FXML private Label profileNameLabel;
    @FXML private Label profileEmailLabel;
    @FXML private Label profileJoinedLabel;

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
    @FXML private Button joinQuizButton;

    // Recent Activity
    @FXML private VBox recentActivityContainer;

    private String username;

    @FXML
    public void initialize() {
        logger.info("ProfileController initialized");
        setupReloadShortcut();
        // Wait for scene to be ready before setting active state
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
        // TODO: Load profile data from backend
        logger.info("Loading profile data for: " + username);
        // This will be implemented when backend is ready
    }

    @FXML
    private void handleEditProfile() {
        logger.info("Edit profile clicked");
        // TODO: Implement edit profile dialog
    }

    @FXML
    private void handleSettings() {
        logger.info("Settings clicked");
        // TODO: Implement settings page
    }

    @FXML
    private void handleChangePassword() {
        logger.info("Change password clicked");
        // TODO: Implement change password dialog
    }

    @FXML
    private void handlePrivacySettings() {
        logger.info("Privacy settings clicked");
        // TODO: Implement privacy settings dialog
    }

    @FXML
    private void handleLogout() {
        logger.info("Logout clicked");
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

    // Existing method (legacy name) kept for compatibility
    @FXML
    private void handleDashboard() {
        com.mathspeed.client.SceneManager.getInstance().navigate(com.mathspeed.client.SceneManager.Screen.DASHBOARD);
        setActiveScreen("home");
    }

    // Missing handler referenced in FXML: maps to home navigation
    @FXML
    private void handleHome() {
        logger.info("Home clicked from Profile screen");
        SceneManager.getInstance().navigate(SceneManager.Screen.DASHBOARD);
        setActiveScreen("home");
    }

    @FXML
    private void handleLibrary() {
        com.mathspeed.client.SceneManager.getInstance().navigate(com.mathspeed.client.SceneManager.Screen.LIBRARY);
        setActiveScreen("library");
    }

    @FXML
    private void handleFriends() {
        com.mathspeed.client.SceneManager.getInstance().navigate(com.mathspeed.client.SceneManager.Screen.FRIENDS);
        setActiveScreen("friends");
    }

    @FXML
    private void handleLeaderboard() {
        com.mathspeed.client.SceneManager.getInstance().navigate(com.mathspeed.client.SceneManager.Screen.LEADERBOARD);
        // could highlight leaderboard if added to profile nav
    }

    @FXML
    private void handleProfile() {
        // Already on profile page
        logger.info("Already on Profile page");
    }
}
