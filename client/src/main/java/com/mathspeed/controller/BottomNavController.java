package com.mathspeed.controller;

import com.mathspeed.client.SceneManager;
import com.mathspeed.client.SessionManager;
import com.mathspeed.model.Player;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import lombok.Setter;
import org.kordamp.ikonli.javafx.FontIcon;

public class BottomNavController {
    @FXML private FontIcon homeIcon;
    @FXML private Label homeLabel;
    @FXML private FontIcon libraryIcon;
    @FXML private Label libraryLabel;
    @FXML private FontIcon friendsIcon;
    @FXML private Label friendsLabel;
    @FXML private FontIcon leaderboardIcon;
    @FXML private Label leaderboardLabel;
    @FXML private Button joinQuizButton;
    private Player currentPlayer;

    public enum Screen {
        HOME, LIBRARY, FRIENDS, LEADERBOARD
    }

    public void setActiveScreen(Screen screen) {
        if (homeIcon == null || homeLabel == null ||
            libraryIcon == null || libraryLabel == null ||
            friendsIcon == null || friendsLabel == null ||
            leaderboardIcon == null || leaderboardLabel == null) {
            return;
        }

        homeIcon.getStyleClass().removeAll("bottom-appbar-icon-active");
        homeLabel.getStyleClass().removeAll("bottom-appbar-label-active");
        libraryIcon.getStyleClass().removeAll("bottom-appbar-icon-active");
        libraryLabel.getStyleClass().removeAll("bottom-appbar-label-active");
        friendsIcon.getStyleClass().removeAll("bottom-appbar-icon-active");
        friendsLabel.getStyleClass().removeAll("bottom-appbar-label-active");
        leaderboardIcon.getStyleClass().removeAll("bottom-appbar-icon-active");
        leaderboardLabel.getStyleClass().removeAll("bottom-appbar-label-active");

        switch (screen) {
            case HOME -> {
                homeIcon.getStyleClass().add("bottom-appbar-icon-active");
                homeLabel.getStyleClass().add("bottom-appbar-label-active");
            }
            case LIBRARY -> {
                libraryIcon.getStyleClass().add("bottom-appbar-icon-active");
                libraryLabel.getStyleClass().add("bottom-appbar-label-active");
            }
            case FRIENDS -> {
                friendsIcon.getStyleClass().add("bottom-appbar-icon-active");
                friendsLabel.getStyleClass().add("bottom-appbar-label-active");
            }
            case LEADERBOARD -> {
                leaderboardIcon.getStyleClass().add("bottom-appbar-icon-active");
                leaderboardLabel.getStyleClass().add("bottom-appbar-label-active");
            }
        }
    }

    @FXML
    private void initialize() {
        currentPlayer = SessionManager.getInstance().getCurrentPlayer();
        // Sync initial active screen if SceneManager already knows current screen
        SceneManager sm = SceneManager.getInstance();
        SceneManager.Screen cs = sm.getCurrentScreen();
        if (cs == null) {
            // Default to HOME until SceneManager sets a current screen
            setActiveScreen(Screen.HOME);
        } else {
            switch (cs) {
                case DASHBOARD -> setActiveScreen(Screen.HOME);
                case LIBRARY -> setActiveScreen(Screen.LIBRARY);
                case FRIENDS -> setActiveScreen(Screen.FRIENDS);
                case LEADERBOARD -> setActiveScreen(Screen.LEADERBOARD);
                default -> { /* ignore */ }
            }
        }
    }

    @FXML
    private void onHomeClicked() {
        if (currentPlayer == null) return;
        setActiveScreen(Screen.HOME);
        SceneManager.getInstance().navigate(SceneManager.Screen.DASHBOARD);
    }

    @FXML
    private void onLibraryClicked() {
        if (currentPlayer == null) return;
        setActiveScreen(Screen.LIBRARY);
        SceneManager.getInstance().navigate(SceneManager.Screen.LIBRARY);
    }

    @FXML
    private void onFriendsClicked() {
        if (currentPlayer == null) return;
        setActiveScreen(Screen.FRIENDS);
        SceneManager.getInstance().navigate(SceneManager.Screen.FRIENDS);
    }

    @FXML
    private void onLeaderboardClicked() {
        if (currentPlayer == null) return;
        setActiveScreen(Screen.LEADERBOARD);
        SceneManager.getInstance().navigate(SceneManager.Screen.LEADERBOARD);
    }
}
