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

public class FriendsController {
    private static final Logger logger = LoggerFactory.getLogger(FriendsController.class);

    private String pendingStartFilter = null; // requests/online if scheduled before init

    // Header component (injected from fx:include)
    @FXML private DashboardHeaderController dashboardHeaderController;

    @FXML private ProgressIndicator loadingIndicator;

    // Category Buttons
    @FXML private Button allFriendsBtn;
    @FXML private Button onlineBtn;
    @FXML private Button requestsBtn;

    // Search
    @FXML private TextField searchField;

    // Friends Container (changed to FlowPane)
    @FXML private FlowPane friendsContainer;

    private String username;
    private String currentFilter = "all";

    @FXML
    public void initialize() {
        logger.info("FriendsController initialized");
        String startFilter = (pendingStartFilter != null) ? pendingStartFilter : currentFilter;
        // Initialize after layout
        javafx.application.Platform.runLater(() -> {
            setActiveFilter(startFilter);
            loadFriends();
            pendingStartFilter = null; // clear
        });
    }

    /**
     * Public API to force switch to Requests tab immediately
     */
    public void showRequestsImmediately() {
        if (requestsBtn == null) {
            // Not initialized yet; set flag
            pendingStartFilter = "requests";
            currentFilter = "requests";
            logger.info("Requests tab scheduled for display after initialization");
            return;
        }
        setActiveFilter("requests");
        loadFriends();
        logger.info("Requests tab displayed immediately");
    }

    /**
     * Public API to force switch to Online tab immediately
     */
    public void showOnlineImmediately() {
        if (onlineBtn == null) {
            // Not initialized yet; set flag
            pendingStartFilter = "online";
            currentFilter = "online";
            logger.info("Online tab scheduled for display after initialization");
            return;
        }
        setActiveFilter("online");
        loadFriends();
        logger.info("Online tab displayed immediately");
    }

    public void setUsername(String username) {
        this.username = username;
        logger.info("FriendsController - Setting username: {}", username);

        // Delegate user info to header controller
        if (dashboardHeaderController != null) {
            String email = username + "@mathspeed.com";
            dashboardHeaderController.setUserInfo(username, email);
            logger.debug("User info delegated to header controller");
        } else {
            logger.warn("Header controller not initialized yet, username will be set later");
        }
    }

    private void setActiveFilter(String filter) {
        logger.info("setActiveFilter called with filter: {}", filter);

        // Check if buttons are initialized
        if (allFriendsBtn == null || onlineBtn == null || requestsBtn == null) {
            logger.warn("Category buttons not initialized yet, filter: {}", filter);
            currentFilter = filter;
            return;
        }

        // Remove active class from all buttons
        allFriendsBtn.getStyleClass().removeAll("category-button-active");
        onlineBtn.getStyleClass().removeAll("category-button-active");
        requestsBtn.getStyleClass().removeAll("category-button-active");

        // Add active class to selected button
        switch (filter) {
            case "all" -> {
                allFriendsBtn.getStyleClass().add("category-button-active");
                logger.debug("Set All Friends button as active");
            }
            case "online" -> {
                onlineBtn.getStyleClass().add("category-button-active");
                logger.debug("Set Online button as active");
            }
            case "requests" -> {
                requestsBtn.getStyleClass().add("category-button-active");
                logger.debug("Set Requests button as active");
            }
        }
        currentFilter = filter;
        logger.info("Filter successfully changed to: {}", filter);
    }

    private void loadFriends() {
        logger.info("Loading friends with filter: " + currentFilter);

        if (friendsContainer == null) {
            logger.warn("Friends container is null");
            return;
        }

        // Clear existing friends
        friendsContainer.getChildren().clear();

        // Add friend cards based on filter
        switch (currentFilter) {
            case "all" -> loadAllFriends();
            case "online" -> loadOnlineFriends();
            case "requests" -> loadFriendRequests();
        }
    }

    private void loadAllFriends() {
        // Add all friends
        addFriendCard("Alice", "https://i.pravatar.cc/150?img=24", true);
        addFriendCard("Bob", "https://i.pravatar.cc/150?img=33", true);
        addFriendCard("Charlie", "https://i.pravatar.cc/150?img=29", false);
        addFriendCard("David", "https://i.pravatar.cc/150?img=20", true);
        addFriendCard("Eve", "https://i.pravatar.cc/150?img=25", false);
        addFriendCard("Frank", "https://i.pravatar.cc/150?img=30", true);
        addFriendCard("Grace", "https://i.pravatar.cc/150?img=26", false);
        addFriendCard("Henry", "https://i.pravatar.cc/150?img=31", true);
    }

    private void loadOnlineFriends() {
        // Add only online friends
        addFriendCard("Alice", "https://i.pravatar.cc/150?img=24", true);
        addFriendCard("Bob", "https://i.pravatar.cc/150?img=33", true);
        addFriendCard("David", "https://i.pravatar.cc/150?img=20", true);
        addFriendCard("Frank", "https://i.pravatar.cc/150?img=30", true);
        addFriendCard("Henry", "https://i.pravatar.cc/150?img=31", true);
    }

    private void loadFriendRequests() {
        // Add pending friend requests
        addFriendRequestCard("Isabella", "https://i.pravatar.cc/150?img=27");
        addFriendRequestCard("Jack", "https://i.pravatar.cc/150?img=32");
        addFriendRequestCard("Kate", "https://i.pravatar.cc/150?img=28");
    }

    private void addFriendCard(String name, String avatarUrl, boolean isOnline) {
        try {
            VBox card = new VBox(8);
            card.setAlignment(Pos.CENTER);
            card.getStyleClass().add("friend-card");
            card.setMinWidth(100);
            card.setPrefWidth(100);
            card.setMaxWidth(100);

            // Avatar with online indicator
            StackPane avatarPane = new StackPane();
            avatarPane.getStyleClass().add("friend-avatar");

            ImageView avatarImg = new ImageView(new Image(avatarUrl));
            avatarImg.setFitWidth(56);
            avatarImg.setFitHeight(56);
            avatarImg.getStyleClass().add("avatar-image");
            Circle avatarClip = new Circle(28, 28, 28);
            avatarImg.setClip(avatarClip);
            avatarPane.getChildren().add(avatarImg);

            // Online status indicator
            if (isOnline) {
                Circle statusIndicator = new Circle(6);
                statusIndicator.setStyle("-fx-fill: #4caf50; -fx-stroke: white; -fx-stroke-width: 2;");
                StackPane.setAlignment(statusIndicator, Pos.BOTTOM_RIGHT);
                avatarPane.getChildren().add(statusIndicator);
            }

            // Name
            Label nameLabel = new Label(name);
            nameLabel.getStyleClass().add("friend-name");

            // Challenge button
            Button challengeBtn = new Button("Challenge");
            challengeBtn.getStyleClass().add("primary-button");
            challengeBtn.setMaxWidth(90);
            challengeBtn.setOnAction(e -> handleChallengeFriend(name));
            // Disable challenge button if friend is offline
            if (!isOnline) {
                challengeBtn.setDisable(true);
                Tooltip offlineTip = new Tooltip("Friend is offline");
                Tooltip.install(challengeBtn, offlineTip);
            }

            card.getChildren().addAll(avatarPane, nameLabel, challengeBtn);

            // Add click handler to card only if friend is online
            if (isOnline) {
                card.setOnMouseClicked(e -> {
                    if (e.getTarget() != challengeBtn) {
                        handleFriendCardClick(name);
                    }
                });
                card.setStyle(card.getStyle() + "; -fx-cursor: hand;");
            } else {
                // Offline card - no click handler, change opacity
                card.setOpacity(0.6);
                card.setStyle(card.getStyle() + "; -fx-cursor: default;");
            }

            friendsContainer.getChildren().add(card);
            logger.debug("Added friend card: {} (online: {})", name, isOnline);

        } catch (Exception e) {
            logger.error("Failed to create friend card: " + name, e);
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
            logger.debug("Added friend request card: {}", name);

        } catch (Exception e) {
            logger.error("Failed to create friend request card: " + name, e);
        }
    }

    private void handleFriendCardClick(String friendName) {
        logger.info("Friend card clicked: {}", friendName);
        // TODO: Show friend profile or details
    }

    private void handleChallengeFriend(String friendName) {
        logger.info("Challenge friend: {}", friendName);
        // TODO: Start quiz challenge with friend
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
        String query = searchField.getText();
        logger.info("Searching for friend: " + query);
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
        // already on friends
    }

    @FXML
    public void handleLeaderboard() {
        com.mathspeed.client.SceneManager.getInstance().navigate(com.mathspeed.client.SceneManager.Screen.LEADERBOARD);
    }
}
