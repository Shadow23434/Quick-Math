package com.mathspeed.controller;

import com.mathspeed.client.SessionManager;
import com.mathspeed.common.HorizontalCarousel;
import com.mathspeed.model.Player;
import com.mathspeed.service.FriendService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DashboardController {
    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    public Button findFriendsButton;
    public Hyperlink seeAllFriendsLink;
    public Hyperlink seeAllQuizLink;
    @FXML private StackPane quizCarouselPane;
    @FXML private StackPane friendsCarouselPane;
    private HorizontalCarousel quizCarousel;
    private HorizontalCarousel friendsCarousel;
    private Player currentPlayer;

    @FXML
    public void initialize() {
        currentPlayer = SessionManager.getInstance().getCurrentPlayer();
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

        FriendService friendService = new FriendService();
        friendService.getAllFriends(currentPlayer.getId()).thenAccept(list -> {
            javafx.application.Platform.runLater(() -> {
                if (list == null || list.isEmpty()) {
                    Label empty = new Label("No online friends");
                    empty.getStyleClass().add("muted-label");
                } else {
                    for (Player f : list) {
                        friendsCarousel.addFriendsCard(f);
                    }
                }
            });
        }).exceptionally(ex -> {
            logger.error("Failed to load online friends", ex);
            javafx.application.Platform.runLater(() -> {
            });
            return null;
        });

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

    @FXML
    public void handleSeeAllFriends() {
        com.mathspeed.client.SceneManager sceneManager = com.mathspeed.client.SceneManager.getInstance();
        sceneManager.navigate(com.mathspeed.client.SceneManager.Screen.FRIENDS);
        javafx.application.Platform.runLater(() -> {
            Object controller = sceneManager.getController(com.mathspeed.client.SceneManager.Screen.FRIENDS);
            if (controller instanceof FriendsController fc) {
                fc.showAllImmediately();
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
