package com.mathspeed.controller;

import com.mathspeed.client.SceneManager;
import com.mathspeed.util.HorizontalCarousel;
import com.mathspeed.util.ReloadManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DashboardController {
    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);
    public Button findFriendsButton;
    public ImageView logoImageView;
    public Button joinQuizButton;
    public Hyperlink seeAllFriendsLink;
    public Hyperlink seeAllQuizLink;

    @FXML
    private Label welcomeLabel;
    @FXML private Label userGreetingLabel;
    @FXML private Label userEmailLabel;
    @FXML
    private Label avatarLabel;
    @FXML private Button reloadButton;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private org.kordamp.ikonli.javafx.FontIcon homeIcon;
    @FXML private Label homeLabel;
    @FXML private org.kordamp.ikonli.javafx.FontIcon libraryIcon;
    @FXML private Label libraryLabel;
    @FXML private org.kordamp.ikonli.javafx.FontIcon friendsIcon;
    @FXML private Label friendsLabel;
    @FXML private org.kordamp.ikonli.javafx.FontIcon profileIcon;
    @FXML private Label profileLabel;
    @FXML private StackPane quizCarouselPane;
    @FXML private StackPane friendsCarouselPane;

    private String username;
    private String currentScreen = "home";
    private HorizontalCarousel quizCarousel;
    private HorizontalCarousel friendsCarousel;

    @FXML
    public void initialize() {
        logger.info("DashboardController initialized");
        setupReloadShortcut();
        setActiveScreen(currentScreen);

        // Quiz Carousel
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

        final HorizontalCarousel carousel = quizCarousel;
        quizCarousel.addNavigationButtons(
                quizPrevIcon,
                quizNextIcon,
                carousel::scrollLeft,
                carousel::scrollRight
        );

        // Friends Carousel
        friendsCarousel = new HorizontalCarousel();
        friendsCarousel.setSpacing(15);
        friendsCarousel.addFriendsCard("Alice", "https://i.pravatar.cc/150?img=24");
        friendsCarousel.addFriendsCard("Bob", "https://i.pravatar.cc/150?img=33");
        friendsCarousel.addFriendsCard("Charlie", "https://i.pravatar.cc/150?img=29");
        friendsCarousel.addFriendsCard("David", "https://i.pravatar.cc/150?img=20");
        friendsCarouselPane.getChildren().add(friendsCarousel);

        FontIcon friendsPrevIcon = new FontIcon("fas-angle-left");
        friendsPrevIcon.setIconSize(18);
        FontIcon friendsNextIcon = new FontIcon("fas-angle-right");
        friendsNextIcon.setIconSize(18);

        friendsCarousel.addNavigationButtons(
                friendsPrevIcon,
                friendsNextIcon,
                carousel::scrollLeft,
                carousel::scrollRight
        );
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
        if (welcomeLabel != null) {
            welcomeLabel.setText("Welcome back, " + username + "!");
        }
    }

    public void setActiveScreen(String screen) {
        // Check if all components are initialized
        if (homeIcon == null || homeLabel == null ||
            libraryIcon == null || libraryLabel == null ||
            friendsIcon == null || friendsLabel == null ||
            profileIcon == null || profileLabel == null) {
            logger.warn("Bottom appbar components not yet initialized");
            return;
        }

        // Remove active style from all
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
            case "profile":
                profileIcon.getStyleClass().add("bottom-appbar-icon-active");
                profileLabel.getStyleClass().add("bottom-appbar-label-active");
                break;
        }
        currentScreen = screen;
    }

    @FXML
    public void handleHome() {
        setActiveScreen("home");
    }

    @FXML
    public void handleLibrary() {
        setActiveScreen("library");
    }

    @FXML
    public void handleFriends() {
        setActiveScreen("friends");
    }

    @FXML
    public void handleProfile() {
        setActiveScreen("profile");
    }

    @FXML
    private void handleSearch() {
        logger.info("Search clicked");
        // TODO: Navigate to search/browse scene
    }

    @FXML
    private void handleNotifications() {
        logger.info("Notifications clicked");
        // TODO: Show notifications panel or navigate to notifications
    }

    @FXML
    private void handleReload() {
        ReloadManager.reloadCurrentScene();
    }
}
