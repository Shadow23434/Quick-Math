package com.mathspeed.controller;

import com.mathspeed.client.SceneManager;
import com.mathspeed.util.ReloadManager;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LeaderboardController {
    private static final Logger logger = LoggerFactory.getLogger(LeaderboardController.class);

    @FXML private ImageView logoImageView;
    @FXML private Label userGreetingLabel;
    @FXML private Label userEmailLabel;
    @FXML private Button filterButton;
    @FXML private Button reloadButton;
    @FXML private ProgressIndicator loadingIndicator;

    // Time Period Buttons
    @FXML private Button todayBtn;
    @FXML private Button weekBtn;
    @FXML private Button monthBtn;
    @FXML private Button allTimeBtn;

    // Top 3 Podium
    @FXML private Label first_name;
    @FXML private Label first_score;
    @FXML private Label second_name;
    @FXML private Label second_score;
    @FXML private Label third_name;
    @FXML private Label third_score;

    // Leaderboard List
    @FXML private VBox leaderboardContainer;

    // User Rank
    @FXML private ImageView userAvatar;
    @FXML private Label userRankLabel;
    @FXML private Label userNameLabel;
    @FXML private Label userScoreLabel;
    @FXML private Label userTrendLabel;

    private String username;
    private String currentPeriod = "today";

    @FXML
    public void initialize() {
        logger.info("LeaderboardController initialized");
        setupReloadShortcut();
        loadLeaderboardData();
        populateSampleData();
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
        String greeting = "Hi, " + username;
        String email = "@" + username.toLowerCase();

        if (userGreetingLabel != null) {
            userGreetingLabel.setText(greeting);
            Tooltip greetingTooltip = new Tooltip(greeting);
            Tooltip.install(userGreetingLabel, greetingTooltip);
        }
        if (userEmailLabel != null) {
            userEmailLabel.setText(email);
            Tooltip emailTooltip = new Tooltip(email);
            Tooltip.install(userEmailLabel, emailTooltip);
        }
        if (userNameLabel != null) {
            userNameLabel.setText(username);
        }
    }

    private void setActivePeriod(String period) {
        todayBtn.getStyleClass().removeAll("category-button-active");
        weekBtn.getStyleClass().removeAll("category-button-active");
        monthBtn.getStyleClass().removeAll("category-button-active");
        allTimeBtn.getStyleClass().removeAll("category-button-active");

        switch (period) {
            case "today" -> todayBtn.getStyleClass().add("category-button-active");
            case "week" -> weekBtn.getStyleClass().add("category-button-active");
            case "month" -> monthBtn.getStyleClass().add("category-button-active");
            case "alltime" -> allTimeBtn.getStyleClass().add("category-button-active");
        }
        currentPeriod = period;
    }

    private void loadLeaderboardData() {
        logger.info("Loading leaderboard data for period: " + currentPeriod);
    }

    private void populateSampleData() {
        if (leaderboardContainer == null) return;

        leaderboardContainer.getChildren().clear();

        String[][] sampleData = {
            {"4", "Emma Wilson", "1,890", "↑", "2"},
            {"5", "Michael Chen", "1,755", "↓", "1"},
            {"6", "Sophia Lee", "1,620", "↑", "3"},
            {"7", "James Brown", "1,540", "−", "0"},
            {"8", "Olivia Davis", "1,430", "↑", "1"},
            {"9", "William Taylor", "1,320", "↓", "2"},
            {"10", "Isabella Garcia", "1,210", "↑", "4"}
        };

        for (String[] data : sampleData) {
            leaderboardContainer.getChildren().add(createLeaderboardItem(
                data[0], data[1], data[2], data[3], data[4]
            ));
        }
    }

    private HBox createLeaderboardItem(String rank, String name, String score, String trend, String change) {
        HBox item = new HBox(15);
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("leaderboard-item");
        item.setPadding(new Insets(12, 15, 12, 15));

        Label rankLabel = new Label(rank);
        rankLabel.getStyleClass().add("rank-number");
        rankLabel.setMinWidth(30);

        ImageView avatar = new ImageView();
        avatar.setFitWidth(40);
        avatar.setFitHeight(40);
        avatar.setPreserveRatio(true);
        int randomImg = 10 + Integer.parseInt(rank);
        avatar.setImage(new Image("https://i.pravatar.cc/150?img=" + randomImg));
        javafx.scene.shape.Circle clip = new javafx.scene.shape.Circle(20, 20, 20);
        avatar.setClip(clip);

        VBox nameBox = new VBox(2);
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("leaderboard-name");
        Label scoreLabel = new Label(score + " pts");
        scoreLabel.getStyleClass().add("leaderboard-score");
        nameBox.getChildren().addAll(nameLabel, scoreLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        String trendText = trend + " " + change;
        Label trendLabel = new Label(trendText);
        if ("↑".equals(trend)) {
            trendLabel.getStyleClass().add("trend-up");
        } else if ("↓".equals(trend)) {
            trendLabel.getStyleClass().add("trend-down");
        } else {
            trendLabel.getStyleClass().add("trend-neutral");
        }

        item.getChildren().addAll(rankLabel, avatar, nameBox, spacer, trendLabel);

        item.setOnMouseEntered(e -> item.setStyle("-fx-background-color: #f5f5f5; -fx-background-radius: 10;"));
        item.setOnMouseExited(e -> item.setStyle("-fx-background-color: transparent;"));

        return item;
    }

    @FXML
    private void handleToday() {
        setActivePeriod("today");
        loadLeaderboardData();
        populateSampleData();
    }

    @FXML
    private void handleWeek() {
        setActivePeriod("week");
        loadLeaderboardData();
        populateSampleData();
    }

    @FXML
    private void handleMonth() {
        setActivePeriod("month");
        loadLeaderboardData();
        populateSampleData();
    }

    @FXML
    private void handleAllTime() {
        setActivePeriod("alltime");
        loadLeaderboardData();
        populateSampleData();
    }

    @FXML
    private void handleFilter() {
        logger.info("Filter clicked");
    }

    @FXML
    private void handleReload() {
        ReloadManager.reloadCurrentScene();
    }

    @FXML
    private void handleHome() {
        com.mathspeed.client.SceneManager.getInstance().navigate(com.mathspeed.client.SceneManager.Screen.DASHBOARD);
    }

    @FXML
    private void handleLibrary() {
        com.mathspeed.client.SceneManager.getInstance().navigate(com.mathspeed.client.SceneManager.Screen.LIBRARY);
    }

    @FXML
    private void handleFriends() {
        com.mathspeed.client.SceneManager.getInstance().navigate(com.mathspeed.client.SceneManager.Screen.FRIENDS);
    }

    @FXML
    private void handleProfile() {
        com.mathspeed.client.SceneManager.getInstance().navigate(com.mathspeed.client.SceneManager.Screen.PROFILE);
    }

    @FXML
    private void handleLeaderboard() {
        // already on leaderboard
    }
}
