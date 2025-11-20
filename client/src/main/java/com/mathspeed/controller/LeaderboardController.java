package com.mathspeed.controller;

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

    @FXML private Label userGreetingLabel;
    @FXML private Label userEmailLabel;
    @FXML private Button reloadButton;

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

    private String username;
    private String currentPeriod = "today";

    // Keep an in-memory list of entries so we can filter by country
    private java.util.List<LeaderboardEntry> allEntries = new java.util.ArrayList<>();

    @FXML
    public void initialize() {
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

    private void loadLeaderboardData() {
        logger.info("Loading leaderboard data for period: " + currentPeriod);
    }

    private void populateSampleData() {
        if (leaderboardContainer == null) return;

        leaderboardContainer.getChildren().clear();
        allEntries.clear();

        // sample data: rank, name, score, countryCode
        String[][] sampleData = {
            {"4", "Emma Wilson", "1,890", "US"},
            {"5", "Michael Chen", "1,755", "CN"},
            {"6", "Sophia Lee", "1,620", "GB"},
            {"7", "James Brown", "1,540", "US"},
            {"8", "Olivia Davis", "1,430", "CA"},
            {"9", "William Taylor", "1,320", "AU"},
            {"10", "Isabella Garcia", "1,210", "BR"}
        };

        for (String[] data : sampleData) {
            LeaderboardEntry e = new LeaderboardEntry(data[0], data[1], data[2], data[3]);
            allEntries.add(e);
        }

        renderEntries(allEntries);
    }

    private void renderEntries(java.util.List<LeaderboardEntry> entries) {
        leaderboardContainer.getChildren().clear();
        for (LeaderboardEntry e : entries) {
            leaderboardContainer.getChildren().add(createLeaderboardItem(e));
        }
    }

    private HBox createLeaderboardItem(LeaderboardEntry entry) {
        HBox item = new HBox(10);
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("leaderboard-item");
        item.setPadding(new Insets(12, 15, 12, 15));

        Label rankLabel = new Label(entry.getRank());
        rankLabel.getStyleClass().add("rank-number");
        rankLabel.setMinWidth(30);

        ImageView avatar = new ImageView();
        avatar.setFitWidth(40);
        avatar.setFitHeight(40);
        avatar.setPreserveRatio(true);
        int randomImg = 10 + Integer.parseInt(entry.getRank());
        avatar.setImage(new Image("https://i.pravatar.cc/150?img=" + randomImg));
        javafx.scene.shape.Circle clip = new javafx.scene.shape.Circle(20, 20, 20);
        avatar.setClip(clip);

        // Flag ImageView
        ImageView flagView = new ImageView();
        flagView.setFitWidth(24);
        flagView.setFitHeight(16);
        flagView.setPreserveRatio(true);
        String countryCode = entry.getCountryCode();
        if (countryCode != null && countryCode.length() == 2) {
            String codeLower = countryCode.toLowerCase();
            String flagUrl = "https://flagcdn.com/w40/" + codeLower + ".png"; // 40px width
            try {
                Image flagImg = new Image(flagUrl, true);
                if (!flagImg.isError()) {
                    flagView.setImage(flagImg);
                } else {
                    // fallback to bundled default
                    java.io.InputStream is = getClass().getResourceAsStream("/images/t1.png");
                    if (is != null) flagView.setImage(new Image(is));
                }
            } catch (Exception ex) {
                java.io.InputStream is = getClass().getResourceAsStream("/images/t1.png");
                if (is != null) flagView.setImage(new Image(is));
            }
        } else {
            java.io.InputStream is = getClass().getResourceAsStream("/images/t1.png");
            if (is != null) flagView.setImage(new Image(is));
        }

        VBox nameBox = new VBox(2);
        Label nameLabel = new Label(entry.getName());
        nameLabel.getStyleClass().add("leaderboard-name");
        Label scoreLabel = new Label(entry.getScore() + " pts");
        scoreLabel.getStyleClass().add("leaderboard-score");
        Label countryLabel = new Label(entry.getCountryCode());
        countryLabel.getStyleClass().add("leaderboard-country");
        nameBox.getChildren().addAll(nameLabel, scoreLabel, countryLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        item.getChildren().addAll(rankLabel, avatar, flagView, nameBox, spacer);

        item.setOnMouseEntered(e -> item.setStyle("-fx-background-color: #f5f5f5; -fx-background-radius: 10;"));
        item.setOnMouseExited(e -> item.setStyle("-fx-background-color: transparent;"));

        return item;
    }

    @FXML
    private void handleReload() {
        ReloadManager.reloadCurrentScene();
    }

    // Simple entry model for the leaderboard
    private static class LeaderboardEntry {
        private final String rank;
        private final String name;
        private final String score;
        private final String countryCode;

        public LeaderboardEntry(String rank, String name, String score, String countryCode) {
            this.rank = rank;
            this.name = name;
            this.score = score;
            this.countryCode = countryCode;
        }

        public String getRank() { return rank; }
        public String getName() { return name; }
        public String getScore() { return score; }
        public String getCountryCode() { return countryCode; }
    }
}
