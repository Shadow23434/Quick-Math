package com.mathspeed.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

public class LeaderboardController {

    @FXML private Label first_name;
    @FXML private Label first_score;
    @FXML private Label second_name;
    @FXML private Label second_score;
    @FXML private Label third_name;
    @FXML private Label third_score;

    @FXML private ImageView firstAvatar;
    @FXML private ImageView secondAvatar;
    @FXML private ImageView thirdAvatar;

    @FXML private VBox leaderboardContainer;

    @FXML private ImageView userAvatar;
    @FXML private Label userRankLabel;
    @FXML private Label userNameLabel;
    @FXML private Label userScoreLabel;

    private String currentUserId; // lưu ID người dùng đang login

    private final String API_URL = "http://localhost:8080/api/leaderboard?limit=100";
    private final ObjectMapper mapper = new ObjectMapper();

    @FXML
    private void initialize() {
        loadLeaderboardData();
    }
    public void setCurrentUser(String userId) {
        this.currentUserId = userId;
    }


    private void loadLeaderboardData() {
        new Thread(() -> {
            try {
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");

                InputStreamReader reader = new InputStreamReader(conn.getInputStream());
                Map<String, Object> response = mapper.readValue(reader, Map.class);
                reader.close();

                if (Boolean.TRUE.equals(response.get("ok"))) {
                    List<Map<String, Object>> leaderboard = (List<Map<String, Object>>) response.get("leaderboard");
                    Platform.runLater(() -> renderLeaderboard(leaderboard));
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void renderLeaderboard(List<Map<String, Object>> leaderboard) {
        leaderboardContainer.getChildren().clear();

        for (int i = 0; i < leaderboard.size(); i++) {
            Map<String, Object> p = leaderboard.get(i);

            String userId = (String) p.get("id");
            String displayName = (String) p.get("displayName");
            String avatarUrl = (String) p.get("avatarUrl");
            int wins = ((Number) p.get("wins")).intValue();

            // Top 3 podium
            if (i == 0) {
                first_name.setText(displayName);
                first_score.setText(wins + " pts");
                if (avatarUrl != null) firstAvatar.setImage(new Image(avatarUrl, true));
            } else if (i == 1) {
                second_name.setText(displayName);
                second_score.setText(wins + " pts");
                if (avatarUrl != null) secondAvatar.setImage(new Image(avatarUrl, true));
            } else if (i == 2) {
                third_name.setText(displayName);
                third_score.setText(wins + " pts");
                if (avatarUrl != null) thirdAvatar.setImage(new Image(avatarUrl, true));
            } else {
                // Những người còn lại đánh số rank
                leaderboardContainer.getChildren().add(createLeaderboardItem(i + 1, displayName, avatarUrl, wins));
            }

            // Hiển thị your rank
            if (currentUserId != null && currentUserId.equals(userId)) {
                userNameLabel.setText(displayName);
                userScoreLabel.setText(wins + " pts");
                userRankLabel.setText("#" + (i + 1));
                if (avatarUrl != null) userAvatar.setImage(new Image(avatarUrl, true));
            }
        }
    }




    private HBox createLeaderboardItem(int rank, String name, String avatarUrl, int score) {
        HBox item = new HBox(10);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(10));
        item.getStyleClass().add("leaderboard-item");

        Label rankLabel = new Label("#" + rank);
        rankLabel.setMinWidth(30);

        ImageView avatar = new ImageView();
        avatar.setFitWidth(40);
        avatar.setFitHeight(40);
        avatar.setPreserveRatio(true);
        if (avatarUrl != null) avatar.setImage(new Image(avatarUrl, true));

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("leaderboard-name");

        Label scoreLabel = new Label(score + " pts");
        scoreLabel.getStyleClass().add("leaderboard-score");

        VBox infoBox = new VBox(2, nameLabel, scoreLabel);

        item.getChildren().addAll(rankLabel, avatar, infoBox, spacer);
        return item;
    }
}
