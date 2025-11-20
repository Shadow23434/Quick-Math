package com.mathspeed.controller;

import com.mathspeed.client.SceneManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HeaderController {
    private static final Logger logger = LoggerFactory.getLogger(HeaderController.class);

    @FXML private ImageView avatarImageView;
    @FXML private Label userGreetingLabel;
    @FXML private Label userEmailLabel;
    @FXML private Button searchButton;
    @FXML private Button notificationButton;
    @FXML private Button reloadButton;

    private String username;
    private String email;

    @FXML
    public void initialize() {
        logger.info("DashboardHeaderController initialized");

        // Ensure CSS is applied to all components after scene is set
        Platform.runLater(() -> {
            applyCSSToComponents();
        });
    }

    /**
     * Apply CSS to all header components
     */
    private void applyCSSToComponents() {
        if (avatarImageView != null) {
            javafx.scene.Parent root = avatarImageView.getParent();
            while (root != null && root.getParent() != null) {
                root = root.getParent();
                if (root.getStyleClass().contains("header-section")) {
                    root.applyCss();
                    root.layout();
                    logger.debug("Applied CSS to header section");
                    break;
                }
            }
        }

        // Apply CSS to individual components
        if (userGreetingLabel != null) userGreetingLabel.applyCss();
        if (userEmailLabel != null) userEmailLabel.applyCss();
        if (searchButton != null) searchButton.applyCss();
        if (notificationButton != null) notificationButton.applyCss();
        if (reloadButton != null) reloadButton.applyCss();

        logger.debug("Applied CSS to all header components");
    }

    /**
     * Set user information
     */
    public void setUserInfo(String username, String email) {
        this.username = username;
        this.email = email;
        updateUserDisplay();
    }

    public void setUsername(String username) {
        this.username = username;
        updateUserDisplay();
    }

    public void setAvatarUrl(String avatarUrl) {
        if (avatarImageView != null && avatarUrl != null && !avatarUrl.isEmpty()) {
            try {
                Image image = new Image(avatarUrl, true);
                avatarImageView.setImage(image);
                logger.debug("Set avatar image from URL: {}", avatarUrl);
            } catch (Exception e) {
                logger.warn("Failed to load avatar from URL: {}", avatarUrl, e);
            }
        }
    }

    private void updateUserDisplay() {
        Platform.runLater(() -> {
            if (username != null && !username.isEmpty()) {
                if (userGreetingLabel != null) {
                    userGreetingLabel.setText("Hi, " + username);
                }
            }
            if (email != null && !email.isEmpty()) {
                if (userEmailLabel != null) {
                    userEmailLabel.setText(email);
                }
            }
        });
    }

    @FXML
    private void handleProfile() {
        logger.info("Profile clicked");
        SceneManager.getInstance().navigate(SceneManager.Screen.PROFILE);
    }

    @FXML
    private void handleNotifications() {
        logger.info("Notification clicked: navigate to Friends and show Requests tab");
        SceneManager sceneManager = SceneManager.getInstance();
        // Ensure username is set before navigating
        if ((username == null || username.isBlank()) && sceneManager.getCurrentUsername() != null) {
            username = sceneManager.getCurrentUsername();
        }
        if (username == null || username.isBlank()) {
            logger.warn("Cannot navigate to Friends - username not available");
            return;
        }

        sceneManager.navigate(SceneManager.Screen.FRIENDS);
        Platform.runLater(() -> {
            Object controller = sceneManager.getController(SceneManager.Screen.FRIENDS);
            if (controller instanceof com.mathspeed.controller.FriendsController fc) {
                logger.info("FriendsController found - invoking showRequestsImmediately()");
                fc.showRequestsImmediately();
            } else {
                logger.warn("FriendsController not available immediately after navigation");
            }
        });
    }

    @FXML
    private void handleReload() {
        logger.info("Reload button clicked");
        // Reload current screen
        SceneManager sceneManager = SceneManager.getInstance();
        SceneManager.Screen currentScreen = sceneManager.getCurrentScreen();
        if (currentScreen != null) {
            sceneManager.navigate(currentScreen);
        }
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }
}
