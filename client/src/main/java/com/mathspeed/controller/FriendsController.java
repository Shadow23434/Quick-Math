package com.mathspeed.controller;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mathspeed.model.Player;
import com.mathspeed.service.FriendService;
import com.mathspeed.client.SessionManager;

public class FriendsController {
    private static final Logger logger = LoggerFactory.getLogger(FriendsController.class);
    private String pendingStartFilter = null;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Button allFriendsBtn;
    @FXML private Button onlineBtn;
    @FXML private Button requestsBtn;
    @FXML private TextField searchField;
    @FXML private FlowPane friendsContainer;
    private String currentFilter = "all";
    private final FriendService friendService = new FriendService();
    private Player currentPlayer;

    @FXML
    public void initialize() {
        currentPlayer = SessionManager.getInstance().getCurrentPlayer();
        String startFilter = (pendingStartFilter != null) ? pendingStartFilter : currentFilter;
        javafx.application.Platform.runLater(() -> {
            setActiveFilter(startFilter);
            loadFriends();
            pendingStartFilter = null;
        });
    }

    public void showRequestsImmediately() {
        if (requestsBtn == null) {
            // Not initialized yet; set flag
            pendingStartFilter = "requests";
            currentFilter = "requests";
            return;
        }
        setActiveFilter("requests");
        loadFriends();
    }

    public void showAllImmediately() {
        if (onlineBtn == null) {
            // Not initialized yet; set flag
            pendingStartFilter = "all";
            currentFilter = "all";
            return;
        }
        setActiveFilter("all");
        loadFriends();
    }

    private void setActiveFilter(String filter) {
        if (allFriendsBtn == null || onlineBtn == null || requestsBtn == null) {
            logger.warn("Category buttons not initialized yet, filter: {}", filter);
            currentFilter = filter;
            return;
        }

        allFriendsBtn.getStyleClass().removeAll("category-button-active");
        onlineBtn.getStyleClass().removeAll("category-button-active");
        requestsBtn.getStyleClass().removeAll("category-button-active");

        // Add active class to selected button
        switch (filter) {
            case "all" -> {
                allFriendsBtn.getStyleClass().add("category-button-active");
            }
            case "online" -> {
                onlineBtn.getStyleClass().add("category-button-active");
            }
            case "requests" -> {
                requestsBtn.getStyleClass().add("category-button-active");
            }
        }
        currentFilter = filter;
    }

    private void loadFriends() {
        if (friendsContainer == null) {
            logger.warn("Friends container is null");
            return;
        }
        friendsContainer.getChildren().clear();
        switch (currentFilter) {
            case "all" -> loadAllFriends();
            case "online" -> loadOnlineFriends();
            case "requests" -> loadFriendRequests();
        }
    }

    private void loadAllFriends() {
        if (friendsContainer == null) return;
        friendsContainer.getChildren().clear();
        if (loadingIndicator != null) loadingIndicator.setVisible(true);

        friendService.getAllFriends(currentPlayer.getId()).thenAccept(list -> {
            javafx.application.Platform.runLater(() -> {
                friendsContainer.getChildren().clear();
                if (list == null || list.isEmpty()) {
                    Label empty = new Label("No friends yet");
                    empty.getStyleClass().add("muted-label");
                    friendsContainer.getChildren().add(empty);
                } else {
                    for (Player f : list) {
                        addFriendCard(f);
                    }
                }
                if (loadingIndicator != null) loadingIndicator.setVisible(false);
            });
        }).exceptionally(ex -> {
            logger.error("Failed to load all friends", ex);
            javafx.application.Platform.runLater(() -> {
                if (loadingIndicator != null) loadingIndicator.setVisible(false);
                showAlert("Failed to load friends", ex.getMessage());
            });
            return null;
        });
    }

    private void loadOnlineFriends() {
        if (friendsContainer == null) return;
        friendsContainer.getChildren().clear();
        if (loadingIndicator != null) loadingIndicator.setVisible(true);

        friendService.getOnlineFriends(currentPlayer.getId()).thenAccept(list -> {
            javafx.application.Platform.runLater(() -> {
                friendsContainer.getChildren().clear();
                if (list == null || list.isEmpty()) {
                    Label empty = new Label("No online friends");
                    empty.getStyleClass().add("muted-label");
                    friendsContainer.getChildren().add(empty);
                } else {
                    for (Player f : list) {
                        addFriendCard(f);
                    }
                }
                if (loadingIndicator != null) loadingIndicator.setVisible(false);
            });
        }).exceptionally(ex -> {
            logger.error("Failed to load online friends", ex);
            javafx.application.Platform.runLater(() -> {
                if (loadingIndicator != null) loadingIndicator.setVisible(false);
                showAlert("Failed to load online friends", ex.getMessage());
            });
            return null;
        });
    }

    private void loadFriendRequests() {
        // Add pending friend requests
        addFriendRequestCard("Isabella", "https://i.pravatar.cc/150?img=27");
        addFriendRequestCard("Jack", "https://i.pravatar.cc/150?img=32");
        addFriendRequestCard("Kate", "https://i.pravatar.cc/150?img=28");
    }

    private void addFriendCard(Player player) {
        String name = player.getDisplayName() != null && !player.getDisplayName().isBlank() ? player.getDisplayName() : player.getUsername();
        String avatarUrl = player.getAvatarUrl() != null ? player.getAvatarUrl() : "";
        String rawStatus = player.getStatus() != null ? player.getStatus() : "offline";
        String statusText;
        String statusColor;
        boolean isOnline = false;

        if ("online".equalsIgnoreCase(rawStatus)) {
            statusText = "Online";
            statusColor = "#4caf50";
            isOnline = true;
        } else if ("in_game".equalsIgnoreCase(rawStatus) || "in-game".equalsIgnoreCase(rawStatus) || "busy".equalsIgnoreCase(rawStatus)) {
            statusText = "In game";
            statusColor = "#ff0033";
        } else {
            statusText = "Offline";
            statusColor = "#9e9e9e";
        }

        try {
            VBox card = new VBox(6);
            card.setAlignment(Pos.CENTER);
            card.getStyleClass().add("friend-card");
            card.setMinWidth(100);
            card.setPrefWidth(100);
            card.setMaxWidth(100);

            // Avatar with online indicator
            StackPane avatarPane = new StackPane();
            avatarPane.getStyleClass().add("friend-avatar");

            ImageView avatarImg;
            if (avatarUrl == null || avatarUrl.isBlank()) {
                avatarImg = new ImageView(new Image(getClass().getResourceAsStream("/images/logo.png")));
            } else {
                avatarImg = new ImageView(new Image(avatarUrl, true));
            }
            avatarImg.setFitWidth(56);
            avatarImg.setFitHeight(56);
            avatarImg.getStyleClass().add("avatar-image");
            Circle avatarClip = new Circle(28, 28, 28);
            avatarImg.setClip(avatarClip);
            avatarPane.getChildren().add(avatarImg);

            // Status indicator (colored circle bottom-right)
            Circle statusIndicator = new Circle(6);
            statusIndicator.setStyle(String.format("-fx-fill: %s; -fx-stroke: white; -fx-stroke-width: 2;", statusColor));
            StackPane.setAlignment(statusIndicator, Pos.BOTTOM_RIGHT);
            avatarPane.getChildren().add(statusIndicator);

            // Name
            Label nameLabel = new Label(name);
            nameLabel.getStyleClass().add("friend-name");

            // Status text under name
            Label statusLabel = new Label(statusText);
            statusLabel.getStyleClass().add("friend-status");
            statusLabel.setStyle(String.format("-fx-font-size: 11px; -fx-text-fill: %s; -fx-font-weight:600", statusColor));

            // Challenge button
            Button challengeBtn = new Button("Challenge");
            challengeBtn.getStyleClass().add("primary-button");
            challengeBtn.setMaxWidth(90);
            challengeBtn.setOnAction(e -> handleChallengeFriend(name));

            // Disable challenge button if friend is not online
            if (!isOnline) {
                challengeBtn.setDisable(true);
                challengeBtn.setOpacity(0.6);
                Tooltip offlineTip = new Tooltip(statusText.equals("In game") ? "Friend is in a game" : "Friend is offline");
                Tooltip.install(challengeBtn, offlineTip);
            }

            card.getChildren().addAll(avatarPane, nameLabel, statusLabel, challengeBtn);

            friendsContainer.getChildren().add(card);
        } catch (Exception e) {
            logger.error("Failed to create friend card: {}", name, e);
        }
    }

    private void addFriendRequestCard(String name, String avatarUrl) {
        try {
            VBox card = new VBox(8);
            card.setAlignment(Pos.CENTER);
            card.getStyleClass().add("friend-card");
            card.setMinWidth(100);
            card.setPrefWidth(100);
            card.setMaxWidth(100);

            // Avatar
            StackPane avatarPane = new StackPane();
            avatarPane.getStyleClass().add("friend-avatar");
            ImageView avatarImg = new ImageView(new Image(avatarUrl));
            avatarImg.setFitWidth(56);
            avatarImg.setFitHeight(56);
            avatarImg.getStyleClass().add("avatar-image");
            Circle avatarClip = new Circle(28, 28, 28);
            avatarImg.setClip(avatarClip);
            avatarPane.getChildren().add(avatarImg);

            // Name
            Label nameLabel = new Label(name);
            nameLabel.getStyleClass().add("friend-name");

            // Accept/Decline buttons
            HBox buttonBox = new HBox(5);
            buttonBox.setAlignment(Pos.CENTER);

            Button acceptBtn = new Button("✓");
            acceptBtn.getStyleClass().add("primary-button");
            acceptBtn.setStyle("-fx-background-color: #4caf50; -fx-min-width: 40px; -fx-max-width: 40px;");
            acceptBtn.setOnAction(e -> handleAcceptFriend(name));

            Button declineBtn = new Button("✗");
            declineBtn.getStyleClass().add("primary-button");
            declineBtn.setStyle("-fx-background-color: #f44336; -fx-min-width: 40px; -fx-max-width: 40px;");
            declineBtn.setOnAction(e -> handleDeclineFriend(name));

            buttonBox.getChildren().addAll(acceptBtn, declineBtn);

            card.getChildren().addAll(avatarPane, nameLabel, buttonBox);
            friendsContainer.getChildren().add(card);
        } catch (Exception e) {
            logger.error("Failed to create friend request card: " + name, e);
        }
    }

    private void handleChallengeFriend(String friendName) {
        logger.info("Challenge friend: {}", friendName);
    }

    private void handleAcceptFriend(String friendName) {
        logger.info("Accept friend request: {}", friendName);
        // TODO: Accept friend request
        loadFriends(); // Reload to show updated list
    }

    private void handleDeclineFriend(String friendName) {
        logger.info("Decline friend request: {}", friendName);
        // TODO: Decline friend request
        loadFriends(); // Reload to show updated list
    }

    @FXML
    private void handleAllFriends() {
        setActiveFilter("all");
        loadFriends();
    }

    @FXML
    private void handleOnline() {
        setActiveFilter("online");
        loadFriends();
    }

    @FXML
    private void handleRequests() {
        setActiveFilter("requests");
        loadFriends();
    }

    @FXML
    private void handleSearchFriend() {
        FriendService friendService = new FriendService();
        friendService.searchFriends( searchField.getText().trim(), currentPlayer.getId()).thenAccept(list -> {
            javafx.application.Platform.runLater(() -> {
                friendsContainer.getChildren().clear();
                if (list == null || list.isEmpty()) {
                    Label empty = new Label("No friends yet");
                    empty.getStyleClass().add("muted-label");
                    friendsContainer.getChildren().add(empty);
                } else {
                    for (Player f : list) {
                        addFriendCard(f);
                    }
                }
                if (loadingIndicator != null) loadingIndicator.setVisible(false);
            });
        }).exceptionally(ex -> {
            logger.error("Failed to load friends", ex);
            javafx.application.Platform.runLater(() -> {
                if (loadingIndicator != null) loadingIndicator.setVisible(false);
                showAlert("Failed to load friends", ex.getMessage());
            });
            return null;
        });

    }

    private void showAlert(String title, String message) {
        try {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setTitle(title);
            a.setHeaderText(null);
            a.setContentText(message != null ? message : "Unknown error");
            a.showAndWait();
        } catch (Exception e) {
            logger.error("Failed to show alert: {} - {}", title, message, e);
        }
    }
}
