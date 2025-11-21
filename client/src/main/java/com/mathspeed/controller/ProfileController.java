package com.mathspeed.controller;

import com.mathspeed.client.SceneManager;
import com.mathspeed.client.SessionManager;
import com.mathspeed.model.Player;
import com.mathspeed.service.AuthService;
import com.mathspeed.util.ReloadManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

public class ProfileController {
    private static final Logger logger = LoggerFactory.getLogger(ProfileController.class);
    @FXML private Button reloadButton;

    private Player currentPlayer;

    // Profile Info
    @FXML private ImageView profileImageView;
    @FXML private Label profileDisplayNameLabel;
    @FXML private Label profileUserNameLabel;
    @FXML private Label profileJoinedLabel;
    // New: country flag and gender
    @FXML private ImageView countryFlagView;
    @FXML private Label genderLabel;
    @FXML private org.kordamp.ikonli.javafx.FontIcon genderIcon;
    @FXML private Label countryNameLabel;

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

    @FXML
    public void initialize() {
        setupReloadShortcut();
        javafx.application.Platform.runLater(() -> setActiveScreen("profile"));
        currentPlayer = SessionManager.getInstance().getCurrentPlayer();
        if (currentPlayer != null) {
            setCurrentPlayerInfo();
        } else {
            logger.warn("No current player found in session");
        }
    }

    private void setCurrentPlayerInfo() {
        // load avatar image from URL into an Image then set on ImageView
        try {
            javafx.scene.image.Image avatarImg = new javafx.scene.image.Image(currentPlayer.getAvatarUrl(), true);
            profileImageView.setImage(avatarImg);
        } catch (Exception ex) {
            // default image if URL invalid
            java.io.InputStream is = getClass().getResourceAsStream("/images/t1.png");
            if (is != null) profileImageView.setImage(new javafx.scene.image.Image(is));
        }
        profileDisplayNameLabel.setText(currentPlayer.getDisplayName());
        profileUserNameLabel.setText("@" + currentPlayer.getUsername());
        // Format createdAt to a friendly date string (e.g. "Joined: 17 Mar 2025")
        java.time.LocalDateTime createdAt = currentPlayer.getCreatedAt();
        if (createdAt != null) {
            DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault());
            profileJoinedLabel.setText("Joined: " + createdAt.format(fmt));
        } else {
            profileJoinedLabel.setText("Joined: -");
        }

        // Gender
        String gender = currentPlayer.getGender();
        if (gender != null && !gender.isBlank()) {
            String g = gender.trim().toLowerCase();
            genderIcon.setVisible(true);
            if (g.equals("m") || g.equals("male")) {
                genderLabel.setText("Male");
                genderIcon.setIconLiteral("fas-mars");
                genderIcon.getStyleClass().removeAll("gender-icon-female", "gender-icon-neutral");
                genderIcon.getStyleClass().add("gender-icon-male");
            } else if (g.equals("f") || g.equals("female")) {
                genderLabel.setText("Female");
                genderIcon.setIconLiteral("fas-venus");
                genderIcon.getStyleClass().removeAll("gender-icon-male", "gender-icon-neutral");
                genderIcon.getStyleClass().add("gender-icon-female");
            } else {
                genderLabel.setText(Character.toUpperCase(g.charAt(0)) + g.substring(1));
                genderIcon.setIconLiteral("fas-user");
                genderIcon.getStyleClass().removeAll("gender-icon-male", "gender-icon-female");
                genderIcon.getStyleClass().add("gender-icon-neutral");
            }
        } else {
            genderLabel.setText("");
            if (genderIcon != null) genderIcon.setVisible(false);
        }

        // Country flag
        String country = currentPlayer.getCountryCode();
        if (country != null && country.length() == 2) {
            String codeLower = country.toLowerCase();
            String codeUpper = country.toUpperCase();
            String flagUrl = "https://flagcdn.com/56x42/" + codeLower + ".png";
            try {
                javafx.scene.image.Image flagImg = new javafx.scene.image.Image(flagUrl, true);
                if (!flagImg.isError()) {
                    countryFlagView.setImage(flagImg);
                } else {
                    java.io.InputStream is = getClass().getResourceAsStream("/images/t1.png");
                    if (is != null) countryFlagView.setImage(new javafx.scene.image.Image(is));
                }
            } catch (Exception ex) {
                java.io.InputStream is = getClass().getResourceAsStream("/images/t1.png");
                if (is != null) countryFlagView.setImage(new javafx.scene.image.Image(is));
            }
            // set country display name using Locale
            try {
                String countryName = new java.util.Locale.Builder().setRegion(codeUpper).build().getDisplayCountry(java.util.Locale.getDefault());
                if (countryName.isBlank()) countryName = codeUpper;
                countryNameLabel.setText("(" + countryName + ")");
                countryNameLabel.setVisible(true);
            } catch (Exception ex) {
                countryNameLabel.setText(codeUpper);
                countryNameLabel.setVisible(true);
            }
        } else {
            // no country: use fallback
            java.io.InputStream is = getClass().getResourceAsStream("/images/t1.png");
            if (is != null) countryFlagView.setImage(new javafx.scene.image.Image(is));
            if (countryNameLabel != null) {
                countryNameLabel.setText("");
                countryNameLabel.setVisible(false);
            }
        }
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
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Logout");
        confirm.setHeaderText("Are you sure you want to logout?");
        confirm.setContentText("You will be redirected to the login page.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // Call backend logout, but proceed to clear session/UI regardless.
                AuthService authService = new AuthService();
                authService.logout(currentPlayer.getUsername()).thenAccept(resp -> {
                    Platform.runLater(() -> {
                        // Always clear local session
                        com.mathspeed.client.SessionManager.getInstance().endSession();
                        if (resp != null && resp.isSuccess()) {
                            SceneManager.getInstance().switchToLogin();
                        } else {
                            // Backend reported failure - show message but still navigate to login
                            String serverMsg = (resp != null) ? resp.getMessage() : "Unknown error";
                            Alert err = new Alert(Alert.AlertType.WARNING);
                            err.setTitle("Logout");
                            err.setHeaderText("Server logout failed");
                            err.setContentText(serverMsg);
                            err.showAndWait();
                            SceneManager.getInstance().switchToLogin();
                        }
                    });
                }).exceptionally(ex -> {
                    Platform.runLater(() -> {
                        com.mathspeed.client.SessionManager.getInstance().endSession();
                        Alert err = new Alert(Alert.AlertType.WARNING);
                        err.setTitle("Logout");
                        err.setHeaderText("Network error during logout");
                        err.setContentText(ex.getMessage());
                        err.showAndWait();
                        SceneManager.getInstance().switchToLogin();
                    });
                    return null;
                });
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
