package com.mathspeed.controller;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LibraryController {
    private static final Logger logger = LoggerFactory.getLogger(LibraryController.class);
    @FXML private HeaderController headerController;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Button allQuizzesBtn;
    @FXML private Button myQuizzesBtn;
    @FXML private Button favoritesBtn;
    @FXML private FlowPane quizContainer;
    private String username;
    private String currentCategory = "all";

    @FXML
    public void initialize() {
        javafx.application.Platform.runLater(this::loadQuizzes);
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

    private void setActiveCategory(String category) {
        allQuizzesBtn.getStyleClass().removeAll("category-button-active");
        myQuizzesBtn.getStyleClass().removeAll("category-button-active");
        favoritesBtn.getStyleClass().removeAll("category-button-active");

        switch (category) {
            case "all" -> allQuizzesBtn.getStyleClass().add("category-button-active");
            case "my" -> myQuizzesBtn.getStyleClass().add("category-button-active");
            case "favorites" -> favoritesBtn.getStyleClass().add("category-button-active");
        }
        currentCategory = category;
    }

    private void loadQuizzes() {
        if (quizContainer == null) {
            logger.warn("Quiz container is null");
            return;
        }
        quizContainer.getChildren().clear();
        switch (currentCategory) {
            case "all" -> loadAllQuizzes();
            case "my" -> loadMyQuizzes();
            case "favorites" -> loadFavoriteQuizzes();
        }
    }

    private void loadAllQuizzes() {
        // Add sample quiz cards (same style as dashboard)
        addQuizCard("quiz-card-yellow", "/images/map.png", 6,
                   "Walk Around the World with Geography Quiz",
                   "Dewayne Jaden", "https://i.pravatar.cc/150?img=13");

        addQuizCard("quiz-card-cyan", "/images/image.png", 8,
                   "How smart are you? Prove your Knowledge",
                   "Katie Madeline", "https://i.pravatar.cc/150?img=16");

        addQuizCard("quiz-card-purple", "/images/ruby.png", 10,
                   "Brain Teaser Challenge for Geniuses",
                   "John Doe", "https://i.pravatar.cc/150?img=15");

        addQuizCard("quiz-card-yellow", "/images/math.png", 12,
                   "Master Mathematics Quiz",
                   "Sarah Connor", "https://i.pravatar.cc/150?img=25");

        addQuizCard("quiz-card-cyan", "/images/map.png", 5,
                   "World Capitals Challenge",
                   "Mike Johnson", "https://i.pravatar.cc/150?img=30");

        addQuizCard("quiz-card-purple", "/images/image.png", 7,
                   "Science Facts You Should Know",
                   "Emma Watson", "https://i.pravatar.cc/150?img=10");
    }

    private void loadMyQuizzes() {
        // Add user's created quizzes
        addQuizCard("quiz-card-cyan", "/images/map.png", 5,
                   "My Custom Geography Quiz",
                   username, "https://i.pravatar.cc/150?img=12");

        addQuizCard("quiz-card-yellow", "/images/ruby.png", 8,
                   "My Brain Teasers Collection",
                   username, "https://i.pravatar.cc/150?img=12");
    }

    private void loadFavoriteQuizzes() {
        // Add favorited quizzes
        addQuizCard("quiz-card-purple", "/images/ruby.png", 10,
                   "Brain Teaser Challenge for Geniuses",
                   "John Doe", "https://i.pravatar.cc/150?img=15");

        addQuizCard("quiz-card-yellow", "/images/map.png", 6,
                   "Walk Around the World with Geography Quiz",
                   "Dewayne Jaden", "https://i.pravatar.cc/150?img=13");
    }

    private void addQuizCard(String themeClass, String imagePath, int questionCount,
                            String title, String authorName, String authorAvatarUrl) {
        try {
            VBox quizCard = new VBox();
            quizCard.getStyleClass().addAll("quiz-card", themeClass);
            quizCard.setSpacing(0);
            quizCard.setMinWidth(280);
            quizCard.setPrefWidth(280);
            quizCard.setMaxWidth(280);

            // Card Background with Icon
            VBox iconBox = new VBox();
            iconBox.setAlignment(Pos.CENTER);
            ImageView quizIcon = new ImageView(new Image(getClass().getResource(imagePath).toExternalForm()));
            quizIcon.setFitWidth(120);
            quizIcon.setFitHeight(120);
            quizIcon.getStyleClass().add("quiz-card-icon");
            iconBox.getChildren().add(quizIcon);
            StackPane.setAlignment(iconBox, Pos.CENTER);

            // Question Count Badge
            HBox badge = new HBox(5);
            badge.setAlignment(Pos.CENTER);
            badge.getStyleClass().add("question-badge");
            FontIcon badgeIcon = new FontIcon("fas-file-alt");
            badgeIcon.getStyleClass().add("badge-icon");
            Label badgeText = new Label(String.valueOf(questionCount));
            badgeText.getStyleClass().add("badge-text");
            badge.getChildren().addAll(badgeIcon, badgeText);
            StackPane.setAlignment(badge, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(badge, new Insets(0, 10, 10, 0));

            // Card StackPane
            StackPane cardStack = new StackPane();
            cardStack.getChildren().addAll(iconBox, badge);

            // Quiz Info
            VBox quizInfo = new VBox(8);
            quizInfo.getStyleClass().add("quiz-info");
            Label quizTitle = new Label(title);
            quizTitle.getStyleClass().add("quiz-title");
            quizTitle.setWrapText(true);

            HBox authorBox = new HBox(8);
            authorBox.setAlignment(Pos.CENTER_LEFT);
            StackPane avatarPane = new StackPane();
            avatarPane.getStyleClass().add("author-avatar");
            ImageView avatarImg = new ImageView(new Image(authorAvatarUrl));
            avatarImg.setFitWidth(24);
            avatarImg.setFitHeight(24);
            avatarImg.getStyleClass().add("avatar-image");
            Circle avatarClip = new Circle(12, 12, 12);
            avatarImg.setClip(avatarClip);
            avatarPane.getChildren().add(avatarImg);
            Label authorLabel = new Label(authorName);
            authorLabel.getStyleClass().add("author-name");
            authorBox.getChildren().addAll(avatarPane, authorLabel);
            quizInfo.getChildren().addAll(quizTitle, authorBox);

            quizCard.getChildren().addAll(cardStack, quizInfo);

            // Add click handler
            quizCard.setOnMouseClicked(e -> handleQuizCardClick(title));
            quizCard.setStyle(quizCard.getStyle() + "; -fx-cursor: hand;");

            quizContainer.getChildren().add(quizCard);
        } catch (Exception e) {
            logger.error("Failed to create quiz card: " + title, e);
        }
    }

    private void handleQuizCardClick(String quizTitle) {
        logger.info("Quiz card clicked: {}", quizTitle);
        // TODO: Navigate to quiz detail or start quiz
    }

    @FXML
    private void handleAllQuizzes() {
        setActiveCategory("all");
        loadQuizzes();
    }

    @FXML
    private void handleMyQuizzes() {
        setActiveCategory("my");
        loadQuizzes();
    }

    @FXML
    private void handleFavorites() {
        setActiveCategory("favorites");
        loadQuizzes();
    }

    public void showAllImmediately() {
        try {
            setActiveCategory("all");
            loadQuizzes();
        } catch (Exception e) {
            logger.warn("Failed to immediately show all quizzes", e);
        }
    }
}
