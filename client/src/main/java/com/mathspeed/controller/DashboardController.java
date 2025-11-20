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

    @FXML private HeaderController headerController;
    public Button findFriendsButton;
    public Button joinQuizButton;
    public Hyperlink seeAllFriendsLink;
    public Hyperlink seeAllQuizLink;
    @FXML private StackPane quizCarouselPane;
    @FXML private StackPane friendsCarouselPane;
    private String username;
    private HorizontalCarousel quizCarousel;
    private HorizontalCarousel friendsCarousel;

    @FXML
    public void initialize() {
        javafx.application.Platform.runLater(() -> {
            initializeQuizCarousel();
            initializeFriendsCarousel();
        });
    }

    private void initializeQuizCarousel() {
        quizCarousel = new HorizontalCarousel();
        quizCarousel.setSpacing(15);

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

        javafx.application.Platform.runLater(() -> {
            quizCarousel.applyCss();
            quizCarousel.layout();
        });
    }

    private void initializeFriendsCarousel() {
        friendsCarousel = new HorizontalCarousel();
        friendsCarousel.setSpacing(15);

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

        javafx.application.Platform.runLater(() -> {
            friendsCarousel.applyCss();
            friendsCarousel.layout();
        });
    }

    public void setUsername(String username) {
        this.username = username;
        if (headerController != null) {
            String email = username + "@mathspeed.com";
            headerController.setUserInfo(username, email);
        } else {
            logger.warn("Header controller not initialized yet, username will be set later");
        }
    }

    @FXML
    public void handleFriends() {
        com.mathspeed.client.SceneManager sceneManager = com.mathspeed.client.SceneManager.getInstance();
        sceneManager.navigate(com.mathspeed.client.SceneManager.Screen.FRIENDS);
        javafx.application.Platform.runLater(() -> {
            Object controller = sceneManager.getController(com.mathspeed.client.SceneManager.Screen.FRIENDS);
            if (controller instanceof FriendsController fc) {
                fc.showOnlineImmediately();
            }
        });
    }

    @FXML
    public void handleSeeAllQuizzes() {
        com.mathspeed.client.SceneManager sceneManager = com.mathspeed.client.SceneManager.getInstance();
        sceneManager.navigate(com.mathspeed.client.SceneManager.Screen.LIBRARY);
        javafx.application.Platform.runLater(() -> {
            Object controller = sceneManager.getController(com.mathspeed.client.SceneManager.Screen.LIBRARY);
            if (controller instanceof LibraryController lc) {
                lc.showAllImmediately();
            }
        });
    }
}
