package com.mathspeed.controller;

import com.mathspeed.client.SessionManager;
import com.mathspeed.common.ErrorComponents;
import javafx.animation.FadeTransition;
import com.mathspeed.model.Player;
import com.mathspeed.model.Quiz;
import com.mathspeed.service.LibraryService;
import javafx.application.Platform;
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
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LibraryController {
    private static final Logger logger = LoggerFactory.getLogger(LibraryController.class);
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Button allQuizzesBtn;
    @FXML private Button myQuizzesBtn;
    @FXML private Button favoritesBtn;
    @FXML private FlowPane quizContainer;
    // Inline loading box shown inside quizContainer while loading (faded placeholders)
    private VBox loadingBox;
    private List<FadeTransition> loadingAnimations;
    private String currentCategory = "all";
    private final Player currentPlayer = SessionManager.getInstance().getCurrentPlayer();
    private final LibraryService libraryService = new LibraryService();

    @FXML
    public void initialize() {
        javafx.application.Platform.runLater(this::loadQuizzes);
    }

    private void showLoadingOverlay() {
        try {
            if (quizContainer == null) return;
            if (loadingBox != null) return; // already visible

            loadingBox = new VBox(12);
            loadingBox.setAlignment(Pos.CENTER);
            loadingBox.getStyleClass().add("loading-overlay");
            loadingBox.prefWidthProperty().bind(quizContainer.widthProperty());

            // Preserve the current content height so showing the loading overlay doesn't
            // change scrollbars or shift the page horizontally. We set the loadingBox
            // preferred height to the current container height (with sensible fallback).
            double containerHeight = quizContainer.getHeight();
            if (containerHeight <= 1) {
                // Try prefHeight as a fallback (when layout hasn't been measured yet)
                containerHeight = quizContainer.getPrefHeight();
            }
            if (containerHeight <= 1) {
                // Final fallback - approximate a reasonable height so layout stays stable
                containerHeight = 600; // adjust if your layout needs a different default
            }
            loadingBox.setPrefHeight(containerHeight);

            // create a few placeholder quiz cards to indicate loading state
            loadingAnimations = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                VBox placeholder = new VBox();
                placeholder.getStyleClass().addAll("quiz-card", "quiz-card-faded");
                placeholder.setSpacing(8);
                placeholder.setMinWidth(280);
                placeholder.setPrefWidth(280);
                placeholder.setMaxWidth(280);

                Region iconRegion = new Region();
                iconRegion.setPrefSize(120, 120);
                iconRegion.getStyleClass().add("quiz-card-icon-faded");

                HBox info = new HBox(8);
                info.setAlignment(Pos.CENTER_LEFT);
                Region avatarRegion = new Region();
                avatarRegion.setPrefSize(24, 24);
                avatarRegion.getStyleClass().add("avatar-faded");
                VBox texts = new VBox(6);
                Region line1 = new Region();
                line1.setPrefSize(160, 14);
                line1.getStyleClass().add("line-faded");
                Region line2 = new Region();
                line2.setPrefSize(100, 12);
                line2.getStyleClass().add("line-faded");
                texts.getChildren().addAll(line1, line2);
                info.getChildren().addAll(avatarRegion, texts);

                placeholder.getChildren().addAll(iconRegion, info);
                loadingBox.getChildren().add(placeholder);

                // Create a fade animation for this placeholder with a small stagger
                FadeTransition ft = new FadeTransition(Duration.millis(700), placeholder);
                ft.setFromValue(0.4);
                ft.setToValue(1.0);
                ft.setCycleCount(FadeTransition.INDEFINITE);
                ft.setAutoReverse(true);
                ft.setDelay(Duration.millis(i * 180));
                loadingAnimations.add(ft);
            }

            Platform.runLater(() -> {
                quizContainer.getChildren().clear();
                quizContainer.getChildren().add(loadingBox);
                // start all placeholder animations
                for (FadeTransition ft : loadingAnimations) {
                    ft.play();
                }
            });
        } catch (Exception e) {
            logger.warn("Failed to show inline loading placeholders", e);
        }
    }

    private void hideLoadingOverlay() {
        try {
            if (loadingBox == null) return;
            Platform.runLater(() -> {
                try {
                    if (loadingAnimations != null) {
                        for (FadeTransition ft : loadingAnimations) {
                            try { ft.stop(); } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
                try {
                    quizContainer.getChildren().remove(loadingBox);
                } catch (Exception ignored) {}
                loadingBox = null;
                loadingAnimations = null;
            });
        } catch (Exception e) {
            logger.warn("Failed to hide inline loading placeholders", e);
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
        // Fetch quizzes from API and render them
        try {
            if (loadingIndicator != null) {
                loadingIndicator.setVisible(true);
            }
            showLoadingOverlay();

            libraryService.getAllQuizzes()
                    .exceptionally(t -> {
                        logger.error("Failed to load quizzes: {}", t.toString());
                        Platform.runLater(() -> {
                            if (loadingIndicator != null) loadingIndicator.setVisible(false);
                            hideLoadingOverlay();
                        });
                        return Collections.emptyList();
                    })
                    .thenAccept(quizzes -> Platform.runLater(() -> {
                        if (loadingIndicator != null) loadingIndicator.setVisible(false);
                        hideLoadingOverlay();
                        quizContainer.getChildren().clear();

                        if (quizzes == null || quizzes.isEmpty()) {
                            // Show a richer placeholder when no quizzes are available
                            VBox placeholder = ErrorComponents.createEmptyPlaceholder("/images/absolute_math.png");
                            // keep existing style conventions
                            placeholder.getStyleClass().add("empty-placeholder-container");
                            quizContainer.getChildren().add(placeholder);
                            return;
                        }

                        for (Quiz q : quizzes) {
                            // Choose theme and imagePath based on quiz level: easy->cyan, medium->yellow, hard->purple
                            String level = q.getLevel();
                            String theme;
                            if (level != null) {
                                switch (level.toLowerCase()) {
                                    case "easy" -> theme = "quiz-card-cyan";
                                    case "medium" -> theme = "quiz-card-yellow";
                                    case "hard" -> theme = "quiz-card-purple";
                                    default -> theme = "quiz-card-cyan";
                                }
                            } else {
                                theme = "quiz-card-cyan";
                            }
                            String imagePath;
                            if (level != null) {
                                switch (level.toLowerCase()) {
                                    case "easy" -> imagePath = "/images/image.png";
                                    case "medium" -> imagePath = "/images/map.png";
                                    case "hard" -> imagePath = "/images/ruby.png";
                                    default -> imagePath = "/images/map.png";
                                }
                            } else {
                                imagePath = "/images/map.png";
                            }
                             int questionCount = q.getQuestionNumber();
                             String title = q.getTitle() == null ? "Untitled Quiz" : q.getTitle();
                             String authorName = "Unknown";
                             String authorAvatarUrl = getResourceExternal("/images/logo.png");

                            if (q.getPlayer() != null) {
                                authorName = q.getPlayer().getDisplayName() != null ? q.getPlayer().getDisplayName() : q.getPlayer().getUsername();
                                if (q.getPlayer().getAvatarUrl() != null && !q.getPlayer().getAvatarUrl().isBlank()) {
                                    authorAvatarUrl = q.getPlayer().getAvatarUrl();
                                }
                            }

                            addQuizCard(theme, imagePath, questionCount, title, authorName, authorAvatarUrl, level);
                        }
                    }));
        } catch (Exception e) {
            logger.error("Exception while loading quizzes", e);
            if (loadingIndicator != null) loadingIndicator.setVisible(false);
            hideLoadingOverlay();
        }
    }

    private void loadMyQuizzes() {
        // Add user's created quizzes
        try {
            if (loadingIndicator != null) {
                loadingIndicator.setVisible(true);
            }
            showLoadingOverlay();

            if (currentPlayer == null) {
                // No logged-in user -> show empty placeholder
                Platform.runLater(() -> {
                    if (loadingIndicator != null) loadingIndicator.setVisible(false);
                    hideLoadingOverlay();
                    VBox placeholder = ErrorComponents.createEmptyPlaceholder("/images/absolute_math.png");
                    placeholder.getStyleClass().add("empty-placeholder-container");
                    quizContainer.getChildren().clear();
                    quizContainer.getChildren().add(placeholder);
                });
                return;
            }

            libraryService.getOwnQuizzes(currentPlayer.getId())
                    .exceptionally(t -> {
                        logger.error("Failed to load own quizzes: {}", t.toString());
                        Platform.runLater(() -> {
                            if (loadingIndicator != null) loadingIndicator.setVisible(false);
                            hideLoadingOverlay();
                        });
                        return Collections.emptyList();
                    })
                    .thenAccept(quizzes -> Platform.runLater(() -> {
                        if (loadingIndicator != null) loadingIndicator.setVisible(false);
                        hideLoadingOverlay();
                        quizContainer.getChildren().clear();

                        if (quizzes == null || quizzes.isEmpty()) {
                            VBox placeholder = ErrorComponents.createEmptyPlaceholder("/images/absolute_math.png");
                            placeholder.getStyleClass().add("empty-placeholder-container");
                            quizContainer.getChildren().add(placeholder);
                            return;
                        }

                        for (Quiz q : quizzes) {
                            String level = q.getLevel();
                            String theme;
                            if (level != null) {
                                switch (level.toLowerCase()) {
                                    case "easy" -> theme = "quiz-card-cyan";
                                    case "medium" -> theme = "quiz-card-yellow";
                                    case "hard" -> theme = "quiz-card-purple";
                                    default -> theme = "quiz-card-cyan";
                                }
                            } else {
                                theme = "quiz-card-cyan";
                            }

                            String imagePath;
                            if (level != null) {
                                switch (level.toLowerCase()) {
                                    case "easy" -> imagePath = "/images/image.png";
                                    case "medium" -> imagePath = "/images/map.png";
                                    case "hard" -> imagePath = "/images/ruby.png";
                                    default -> imagePath = "/images/map.png";
                                }
                            } else {
                                imagePath = "/images/map.png";
                            }

                            int questionCount = q.getQuestionNumber();
                            String title = q.getTitle() == null ? "Untitled Quiz" : q.getTitle();
                            String authorName = "Unknown";
                            String authorAvatarUrl = getResourceExternal("/images/logo.png");

                            if (q.getPlayer() != null) {
                                authorName = q.getPlayer().getDisplayName() != null ? q.getPlayer().getDisplayName() : q.getPlayer().getUsername();
                                if (q.getPlayer().getAvatarUrl() != null && !q.getPlayer().getAvatarUrl().isBlank()) {
                                    authorAvatarUrl = q.getPlayer().getAvatarUrl();
                                }
                            } else {
                                // fallback to current player info
                                if (currentPlayer.getDisplayName() != null && !currentPlayer.getDisplayName().isBlank()) {
                                    authorName = currentPlayer.getDisplayName();
                                } else if (currentPlayer.getUsername() != null) {
                                    authorName = currentPlayer.getUsername();
                                }
                                if (currentPlayer.getAvatarUrl() != null && !currentPlayer.getAvatarUrl().isBlank()) {
                                    authorAvatarUrl = currentPlayer.getAvatarUrl();
                                }
                            }

                            addQuizCard(theme, imagePath, questionCount, title, authorName, authorAvatarUrl, level);
                        }
                    }));
        } catch (Exception e) {
            logger.error("Exception while loading own quizzes", e);
            if (loadingIndicator != null) loadingIndicator.setVisible(false);
            hideLoadingOverlay();
        }
    }

    private void loadFavoriteQuizzes() {
        // Add favorited quizzes
        addQuizCard("quiz-card-purple", "/images/ruby.png", 10,
                   "Brain Teaser Challenge for Geniuses",
                   "John Doe", "https://i.pravatar.cc/150?img=15", "hard");

        addQuizCard("quiz-card-yellow", "/images/map.png", 6,
                   "Walk Around the World with Geography Quiz",
                   "Dewayne Jaden", "https://i.pravatar.cc/150?img=13", "medium");
    }

    private void addQuizCard(String themeClass, String imagePath, int questionCount,
                            String title, String authorName, String authorAvatarUrl, String level) {
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
            String iconUrl = getResourceExternal(imagePath);
            ImageView quizIcon;
            if (iconUrl != null) {
                quizIcon = new ImageView(new Image(iconUrl));
            } else {
                quizIcon = new ImageView();
            }
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

            // Level badge (e.g., Easy/Medium/Hard) - display top-left
            if (level != null && !level.isBlank()) {
                String lvl = level.substring(0, 1).toUpperCase() + level.substring(1).toLowerCase();
                Label levelLabel = new Label(lvl);
                levelLabel.getStyleClass().add("level-badge");
                // also add level-specific style (level-easy, level-medium, level-hard)
                switch (level.toLowerCase()) {
                    case "easy" -> levelLabel.getStyleClass().add("level-easy");
                    case "medium" -> levelLabel.getStyleClass().add("level-medium");
                    case "hard" -> levelLabel.getStyleClass().add("level-hard");
                    default -> levelLabel.getStyleClass().add("level-medium");
                }
                cardStack.getChildren().add(levelLabel);
                StackPane.setAlignment(levelLabel, Pos.TOP_LEFT);
                StackPane.setMargin(levelLabel, new Insets(8, 0, 0, 8));
            }

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

            ImageView avatarImg;
            try {
                if (authorAvatarUrl != null) {
                    avatarImg = new ImageView(new Image(authorAvatarUrl));
                } else {
                    avatarImg = new ImageView();
                }
             } catch (Exception ex) {
                 // fallback to bundled logo
                String fallback = getResourceExternal("/images/logo.png");
                avatarImg = fallback != null ? new ImageView(new Image(fallback)) : new ImageView();
             }

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

    private String getResourceExternal(String path) {
        try {
            java.net.URL url = getClass().getResource(path);
            return url != null ? url.toExternalForm() : null;
        } catch (Exception e) {
            logger.warn("Failed to resolve resource {}", path, e);
            return null;
        }
    }
}
