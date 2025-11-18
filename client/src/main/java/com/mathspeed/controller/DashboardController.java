package com.mathspeed.controller;

import com.mathspeed.util.HorizontalCarousel;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DashboardController {
    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    // Header component (injected from fx:include)
    @FXML private DashboardHeaderController dashboardHeaderController;

    // Main content fields
    public Button findFriendsButton;
    public Button joinQuizButton;
    public Hyperlink seeAllFriendsLink;
    public Hyperlink seeAllQuizLink;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private StackPane quizCarouselPane;
    @FXML private StackPane friendsCarouselPane;

    private String username;
    private HorizontalCarousel quizCarousel;
    private HorizontalCarousel friendsCarousel;

    @FXML
    public void initialize() {
        logger.info("DashboardController initialized");
        setupReloadShortcut();

        // Initialize carousels after layout is ready
        javafx.application.Platform.runLater(() -> {
            initializeQuizCarousel();
            initializeFriendsCarousel();
        });
    }

    private void initializeQuizCarousel() {
        // Quiz Carousel
        quizCarousel = new HorizontalCarousel();
        quizCarousel.setSpacing(15);

        // Set carousel viewport to match pane width
        quizCarousel.setPrefWidth(320);
        quizCarousel.setMaxWidth(320);
        quizCarousel.setMinWidth(320);

        logger.debug("Quiz carousel pane dimensions: {}x{}", quizCarouselPane.getPrefWidth(), quizCarouselPane.getPrefHeight());

        quizCarousel.addQuizCard(
            "quiz-card-yellow",
            getClass().getResource("/images/map.png").toExternalForm(),
            6,
            "Walk Around the World with Geography Quiz",
            "Dewayne Jaden",
            "https://i.pravatar.cc/150?img=13"
        );
        quizCarousel.addQuizCard(
            "quiz-card-cyan",
            getClass().getResource("/images/image.png").toExternalForm(),
            8,
            "How smart are you? Prove your Knowledge",
            "Katie Madeline",
            "https://i.pravatar.cc/150?img=16"
        );
        quizCarousel.addQuizCard(
            "quiz-card-purple",
            getClass().getResource("/images/ruby.png").toExternalForm(),
            10,
            "Brain Teaser Challenge for Geniuses",
            "John Doe",
            "https://i.pravatar.cc/150?img=15"
        );

        quizCarouselPane.getChildren().add(quizCarousel);

        FontIcon quizPrevIcon = new FontIcon("fas-angle-left");
        quizPrevIcon.setIconSize(18);
        FontIcon quizNextIcon = new FontIcon("fas-angle-right");
        quizNextIcon.setIconSize(18);

        quizCarousel.addNavigationButtons(
                quizPrevIcon,
                quizNextIcon,
                quizCarousel::scrollLeft,
                quizCarousel::scrollRight
        );

        // Force layout to calculate sizes
        javafx.application.Platform.runLater(() -> {
            quizCarousel.applyCss();
            quizCarousel.layout();

            double containerWidth = quizCarousel.getContainer().getWidth();
            double viewportWidth = quizCarousel.getWidth();
            int cardCount = quizCarousel.getContainer().getChildren().size();

            logger.info("Quiz carousel initialized: {} cards, viewport: {}px, content: {}px",
                        cardCount, viewportWidth, containerWidth);
        });
    }

    private void initializeFriendsCarousel() {
        // Friends Carousel
        friendsCarousel = new HorizontalCarousel();
        friendsCarousel.setSpacing(15);

        // Set carousel viewport to match pane width
        friendsCarousel.setPrefWidth(320);
        friendsCarousel.setMaxWidth(320);
        friendsCarousel.setMinWidth(320);

        logger.debug("Friends carousel pane dimensions: {}x{}", friendsCarouselPane.getPrefWidth(), friendsCarouselPane.getPrefHeight());

        friendsCarousel.addFriendsCard("Alice", "https://i.pravatar.cc/150?img=24");
        friendsCarousel.addFriendsCard("Bob", "https://i.pravatar.cc/150?img=33");
        friendsCarousel.addFriendsCard("Charlie", "https://i.pravatar.cc/150?img=29");
        friendsCarousel.addFriendsCard("David", "https://i.pravatar.cc/150?img=20");
        friendsCarousel.addFriendsCard("Eve", "https://i.pravatar.cc/150?img=25");
        friendsCarousel.addFriendsCard("Frank", "https://i.pravatar.cc/150?img=30");

        friendsCarouselPane.getChildren().add(friendsCarousel);

        FontIcon friendsPrevIcon = new FontIcon("fas-angle-left");
        friendsPrevIcon.setIconSize(18);
        FontIcon friendsNextIcon = new FontIcon("fas-angle-right");
        friendsNextIcon.setIconSize(18);

        friendsCarousel.addNavigationButtons(
                friendsPrevIcon,
                friendsNextIcon,
                friendsCarousel::scrollLeft,
                friendsCarousel::scrollRight
        );

        // Force layout to calculate sizes
        javafx.application.Platform.runLater(() -> {
            friendsCarousel.applyCss();
            friendsCarousel.layout();

            double containerWidth = friendsCarousel.getContainer().getWidth();
            double viewportWidth = friendsCarousel.getWidth();
            int cardCount = friendsCarousel.getContainer().getChildren().size();

            logger.info("Friends carousel initialized: {} cards, viewport: {}px, content: {}px",
                        cardCount, viewportWidth, containerWidth);
        });
    }

    private void setupReloadShortcut() {
        // Reload shortcut is now handled by the header controller
        logger.debug("Reload shortcut setup delegated to header controller");
    }

    public void setUsername(String username) {
        this.username = username;
        logger.info("DashboardController - Setting username: {}", username);

        // Delegate user info to header controller
        if (dashboardHeaderController != null) {
            String email = username + "@mathspeed.com";
            dashboardHeaderController.setUserInfo(username, email);
            logger.debug("User info delegated to header controller");
        } else {
            logger.warn("Header controller not initialized yet, username will be set later");
        }
    }

    @FXML
    public void handleFriends() {
        com.mathspeed.client.SceneManager sceneManager = com.mathspeed.client.SceneManager.getInstance();
        sceneManager.navigate(com.mathspeed.client.SceneManager.Screen.FRIENDS);
        // Switch to Online tab when triggered from "See All"
        javafx.application.Platform.runLater(() -> {
            Object controller = sceneManager.getController(com.mathspeed.client.SceneManager.Screen.FRIENDS);
            if (controller instanceof FriendsController fc) {
                fc.showOnlineImmediately();
            }
        });
    }

    @FXML
    public void handleLibrary() {
        com.mathspeed.client.SceneManager.getInstance().navigate(com.mathspeed.client.SceneManager.Screen.LIBRARY);
    }
}
