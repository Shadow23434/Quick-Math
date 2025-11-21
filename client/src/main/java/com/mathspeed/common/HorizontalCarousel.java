package com.mathspeed.common;

import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HorizontalCarousel extends StackPane {
    private static final Logger logger = LoggerFactory.getLogger(HorizontalCarousel.class);
    private final HBox container;
    private double scrollAmount = 200;
    private Button prevBtn, nextBtn;
    private FontIcon prevIconRef, nextIconRef;

    public HorizontalCarousel() {
        container = new HBox(15);
        container.setStyle("-fx-padding: 0 10;");
        container.setMinWidth(0);
        container.setPrefWidth(Region.USE_COMPUTED_SIZE);
        container.setMaxWidth(Double.MAX_VALUE);

        HBox.setHgrow(container, javafx.scene.layout.Priority.NEVER);
        setStyle("-fx-border-color: transparent;");
        this.getChildren().add(container);
        this.setStyle("-fx-padding: 0;");
        this.setMinWidth(0);

        this.layoutBoundsProperty().addListener((obs, oldVal, newVal) -> {
            double width = newVal.getWidth();
            double height = newVal.getHeight();
            if (width > 0 && height > 0) {
                Rectangle clip = new Rectangle(0, 0, width, height);
                clip.setMouseTransparent(true);
                this.setClip(clip);  // Clip the carousel itself, not just container
                logger.debug("Carousel clipped to: {}x{}", width, height);
            }
            updateNavButtonVisibility();
            updateScrollAmount();
        });

        this.setOnScroll(event -> {
            double delta = event.getDeltaX() != 0 ? event.getDeltaX() : event.getDeltaY();
            if (delta != 0) {
                scrollHorizontal(-delta * 0.5);
                updateNavButtonVisibility();
            }
        });
    }

    private void updateScrollAmount() {
        if (container.getChildren().size() > 0) {
            javafx.scene.Node firstChild = container.getChildren().get(0);
            double cardWidth = firstChild.getBoundsInParent().getWidth();
            double spacing = container.getSpacing();
            this.scrollAmount = cardWidth + spacing;
        }
    }

    public void scrollRight() {
        double currentTranslate = Math.abs(container.getTranslateX());
        double maxScroll = getMaxScroll();
        double visibleWidth = this.getWidth();
        int totalCards = container.getChildren().size();

        double[] cardPositions = new double[totalCards];
        double[] cardWidths = new double[totalCards];
        double cumulativePosition = 0;
        for (int i = 0; i < totalCards; i++) {
            cardPositions[i] = cumulativePosition;
            javafx.scene.Node child = container.getChildren().get(i);
            double childWidth = child.getBoundsInParent().getWidth();
            cardWidths[i] = childWidth;
            cumulativePosition += childWidth + container.getSpacing();
        }

        double viewportStart = currentTranslate;
        double viewportEnd = currentTranslate + visibleWidth;

        int nextCardIndex = -1;
        for (int i = 0; i < totalCards; i++) {
            double cardStart = cardPositions[i];
            double cardEnd = cardStart + cardWidths[i];

            if (cardStart < viewportStart - 1) {
                continue;
            }

            double visibleStart = Math.max(cardStart, viewportStart);
            double visibleEnd = Math.min(cardEnd, viewportEnd);
            double visibleAmount = Math.max(0, visibleEnd - visibleStart);
            double visiblePercentage = visibleAmount / cardWidths[i];

            if (visiblePercentage < 0.8) {
                nextCardIndex = i;
                break;
            }
        }

        if (nextCardIndex == -1) {
            return;
        }

        double targetPosition = cardPositions[nextCardIndex];
        if (targetPosition > maxScroll) {
            targetPosition = maxScroll;
        }

        scrollToPosition(-targetPosition);
    }

    public void scrollLeft() {
        double currentTranslate = Math.abs(container.getTranslateX());
        double visibleWidth = this.getWidth();
        int totalCards = container.getChildren().size();

        double[] cardPositions = new double[totalCards];
        double cumulativePosition = 0;
        for (int i = 0; i < totalCards; i++) {
            cardPositions[i] = cumulativePosition;
            javafx.scene.Node child = container.getChildren().get(i);
            double childWidth = child.getBoundsInParent().getWidth();
            cumulativePosition += childWidth + container.getSpacing();
        }

        int prevCardIndex = -1;
        for (int i = totalCards - 1; i >= 0; i--) {
            javafx.scene.Node child = container.getChildren().get(i);
            double cardStart = cardPositions[i];

            double cardStartInViewport = cardStart - currentTranslate;

            if (cardStartInViewport < 1) {
                prevCardIndex = Math.max(0, i - 1);
                break;
            }
        }

        if (prevCardIndex == -1) {
            scrollToPosition(0);
            return;
        }

        double targetPosition = cardPositions[prevCardIndex];
        scrollToPosition(-targetPosition);
    }

    private void scrollToPosition(double position) {
        double maxScroll = getMaxScroll();
        position = Math.max(-maxScroll, Math.min(0, position));

        TranslateTransition transition = new TranslateTransition(
                Duration.millis(300),
                container
        );
        transition.setToX(position);
        transition.setOnFinished(e -> updateNavButtonVisibility());
        transition.play();
    }

    private void scrollHorizontal(double amount) {
        TranslateTransition transition = new TranslateTransition(
                Duration.millis(300),
                container
        );
        double currentTranslate = container.getTranslateX();
        double newTranslate = currentTranslate + amount;

        newTranslate = Math.max(-getMaxScroll(), Math.min(0, newTranslate));

        transition.setToX(newTranslate);
        transition.setOnFinished(e -> {
            updateNavButtonVisibility();
        });
        transition.play();
    }

    private double getMaxScroll() {
        double visibleWidth = this.getWidth();

        // Tính tổng width thực tế của children
        double contentWidth = 0;

        for (int i = 0; i < container.getChildren().size(); i++) {
            javafx.scene.Node child = container.getChildren().get(i);
            double childWidth = child.getBoundsInParent().getWidth();
            contentWidth += childWidth;
        }

        // Thêm spacing
        if (container.getChildren().size() > 1) {
            double totalSpacing = container.getSpacing() * (container.getChildren().size() - 1);
            contentWidth += totalSpacing;
        }

        double maxScroll = Math.max(0, contentWidth - visibleWidth);

        return maxScroll;
    }

    public void setSpacing(double spacing) {
        container.setSpacing(spacing);
    }

    public HBox getContainer() {
        return container;
    }

    public void addQuizCard(String themeClass, String imagePath, int questionCount, String title, String authorName, String authorAvatarUrl) {
        VBox quizCard = new VBox();
        quizCard.getStyleClass().addAll("quiz-card", themeClass);
        quizCard.setSpacing(0);
        quizCard.setMinWidth(280);
        quizCard.setPrefWidth(280);
        quizCard.setMaxWidth(280);

        // Card Background with Icon
        VBox iconBox = new VBox();
        iconBox.setAlignment(Pos.CENTER);
        ImageView quizIcon = new ImageView(new Image(imagePath));
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
        container.getChildren().add(quizCard);
        Platform.runLater(() -> {
            updateScrollAmount();
            updateNavButtonVisibility();
        });
    }

    public void addFriendsCard(String name, String avatarUrl) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("friend-card");

        // Avatar
        StackPane avatarPane = new StackPane();
        avatarPane.getStyleClass().add("friend-avatar");
        ImageView avatarImg = new ImageView(new javafx.scene.image.Image(avatarUrl));
        avatarImg.setFitWidth(56);
        avatarImg.setFitHeight(56);
        avatarImg.getStyleClass().add("avatar-image");
        javafx.scene.shape.Circle avatarClip = new javafx.scene.shape.Circle(28, 28, 28);
        avatarImg.setClip(avatarClip);
        avatarPane.getChildren().add(avatarImg);

        // Name
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("friend-name");

        // Challenge button
        Button challengeBtn = new Button("Challenge");
        challengeBtn.getStyleClass().add("primary-button");
        challengeBtn.setMaxWidth(90);

        card.getChildren().addAll(avatarPane, nameLabel, challengeBtn);
        container.getChildren().add(card);
        Platform.runLater(this::updateNavButtonVisibility);
    }

    public void addNavigationButtons(FontIcon prevIcon, FontIcon nextIcon, Runnable onPrev, Runnable onNext) {
        prevBtn = new Button();
        prevIconRef = prevIcon;
        if (prevIcon != null) prevIcon.getStyleClass().add("carousel-nav-icon");
        if (prevIcon != null) prevBtn.setGraphic(prevIcon);
        prevBtn.getStyleClass().add("carousel-nav-btn");
        prevBtn.setFocusTraversable(false);
        prevBtn.setOnAction(e -> { if (onPrev != null) onPrev.run(); });
        StackPane.setAlignment(prevBtn, Pos.CENTER_LEFT);
        prevBtn.setVisible(false);
        prevBtn.setManaged(false);
        if (prevIcon != null) {
            prevBtn.setOnMouseEntered(e -> prevIcon.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("custom-hover"), true));
            prevBtn.setOnMouseExited(e -> prevIcon.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("custom-hover"), false));
        }

        nextBtn = new Button();
        nextIconRef = nextIcon;
        if (nextIcon != null) nextIcon.getStyleClass().add("carousel-nav-icon");
        if (nextIcon != null) nextBtn.setGraphic(nextIcon);
        nextBtn.getStyleClass().add("carousel-nav-btn");
        nextBtn.setFocusTraversable(false);
        nextBtn.setOnAction(e -> {
            if (onNext != null) {
                onNext.run();
            }
        });
        StackPane.setAlignment(nextBtn, Pos.CENTER_RIGHT);
        nextBtn.setVisible(false);
        nextBtn.setManaged(false);
        if (nextIcon != null) {
            nextBtn.setOnMouseEntered(e -> nextIcon.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("custom-hover"), true));
            nextBtn.setOnMouseExited(e -> nextIcon.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("custom-hover"), false));
        }
        this.getChildren().addAll(prevBtn, nextBtn);
        Platform.runLater(this::updateNavButtonVisibility);
    }

    private void updateNavButtonVisibility() {
        this.layout();
        double currentTranslate = Math.abs(container.getTranslateX());
        double maxScroll = getMaxScroll();
        double visibleWidth = this.getWidth();
        int totalCards = container.getChildren().size();

        boolean canScrollLeft = container.getTranslateX() < -1e-2;

        boolean hasUnviewedCards = false;
        if (totalCards > 0) {
            for (int i = 0; i < totalCards; i++) {
                javafx.scene.Node child = container.getChildren().get(i);

                double cardX = child.getBoundsInParent().getMinX();
                double cardWidth = child.getBoundsInParent().getWidth();

                double cardStart = cardX;
                double cardEnd = cardX + cardWidth;

                double viewportStart = currentTranslate;
                double viewportEnd = currentTranslate + visibleWidth;

                double visibleStart = Math.max(cardStart, viewportStart);
                double visibleEnd = Math.min(cardEnd, viewportEnd);
                double visibleAmount = Math.max(0, visibleEnd - visibleStart);
                double visiblePercentage = (cardWidth > 0) ? (visibleAmount / cardWidth) : 0;

                if (cardEnd > viewportStart + 1) {
                    if (visiblePercentage < 0.8) {
                        hasUnviewedCards = true;
                        break;
                    }
                }
            }
        }

        boolean canScrollRight = hasUnviewedCards;
        if (prevBtn != null) {
            prevBtn.setVisible(canScrollLeft);
            prevBtn.setManaged(canScrollLeft);
        }
        if (nextBtn != null) {
            nextBtn.setVisible(canScrollRight);
            nextBtn.setManaged(canScrollRight);
            nextBtn.setOpacity(1.0);
            nextBtn.toFront();
        }
    }
}
