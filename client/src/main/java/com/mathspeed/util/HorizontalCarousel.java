package com.mathspeed.util;

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

public class HorizontalCarousel extends StackPane {
    private final HBox container;
    private double scrollAmount = 200; // Pixels to scroll per action
    private Button prevBtn, nextBtn;
    private FontIcon prevIconRef, nextIconRef;

    public HorizontalCarousel() {
        container = new HBox(15);
        container.setStyle("-fx-padding: 0;");
        container.setMinWidth(0);
        container.setPrefWidth(Region.USE_COMPUTED_SIZE);
        container.setMaxWidth(Double.MAX_VALUE);

        HBox.setHgrow(container, javafx.scene.layout.Priority.NEVER);
        setStyle("-fx-border-color: transparent;");
        this.getChildren().add(container);
        this.setStyle("-fx-padding: 0;");
        this.setMinWidth(0);

        this.layoutBoundsProperty().addListener((obs, oldVal, newVal) -> {
            Rectangle clip = new Rectangle(newVal.getWidth(), newVal.getHeight());
            clip.setMouseTransparent(true);
            container.setClip(clip);
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
            // Scroll 1 card + spacing
            this.scrollAmount = cardWidth + spacing;
            System.out.println("Updated scrollAmount to: " + scrollAmount);
        }
    }

    public void addItem(javafx.scene.Node item) {
        container.getChildren().add(item);
        Platform.runLater(this::updateNavButtonVisibility);
    }

    public void scrollRight() {
        double currentTranslate = Math.abs(container.getTranslateX());
        double maxScroll = getMaxScroll();
        double visibleWidth = this.getWidth();
        int totalCards = container.getChildren().size();

        System.out.println("=== ScrollRight Debug ===");
        System.out.println("Current translateX: " + container.getTranslateX());
        System.out.println("Current translate (abs): " + currentTranslate);
        System.out.println("Total cards: " + totalCards);
        System.out.println("Max scroll: " + maxScroll);
        System.out.println("Visible width: " + visibleWidth);

        double[] cardPositions = new double[totalCards];
        double[] cardWidths = new double[totalCards];
        double cumulativePosition = 0;
        for (int i = 0; i < totalCards; i++) {
            cardPositions[i] = cumulativePosition;
            javafx.scene.Node child = container.getChildren().get(i);
            double childWidth = child.getBoundsInParent().getWidth();
            cardWidths[i] = childWidth;
            cumulativePosition += childWidth + container.getSpacing();
            System.out.println("Card " + i + " actual position: " + cardPositions[i] + " (width: " + childWidth + ")");
        }

        // Viewport hiện tại
        double viewportStart = currentTranslate;
        double viewportEnd = currentTranslate + visibleWidth;

        System.out.println("Viewport: " + viewportStart + " to " + viewportEnd);

        int nextCardIndex = -1;
        for (int i = 0; i < totalCards; i++) {
            double cardStart = cardPositions[i];
            double cardEnd = cardStart + cardWidths[i];

            if (cardStart < viewportStart - 1) {
                System.out.println("Card " + i + ": cardStart=" + cardStart + " < viewportStart=" + viewportStart + ", skipping (card already passed)");
                continue;
            }

            double visibleStart = Math.max(cardStart, viewportStart);
            double visibleEnd = Math.min(cardEnd, viewportEnd);
            double visibleAmount = Math.max(0, visibleEnd - visibleStart);
            double visiblePercentage = visibleAmount / cardWidths[i];

            System.out.println("Card " + i + ": cardStart=" + cardStart + ", cardEnd=" + cardEnd +
                             ", visible=" + visibleAmount + "px (" + (visiblePercentage * 100) + "%)");


            if (visiblePercentage < 0.8) {
                nextCardIndex = i;
                System.out.println("Found next card to scroll to: " + i + " (only " + (visiblePercentage * 100) + "% visible)");
                break;
            }
        }

        // Nếu không tìm thấy card nào cần scroll, tức là đã ở cuối
        if (nextCardIndex == -1) {
            System.out.println("All cards in/after viewport are fully visible, already at the end");
            return;
        }

        // Scroll để card này hiển thị đầy đủ
        double targetPosition = cardPositions[nextCardIndex];

        System.out.println("Next card index: " + nextCardIndex);
        System.out.println("Calculated target position: " + targetPosition);

        // Clamp target position trong phạm vi hợp lệ [0, maxScroll]
        if (targetPosition > maxScroll) {
            System.out.println("Target exceeds maxScroll, using maxScroll instead");
            targetPosition = maxScroll;
        }

        System.out.println("Final target position: " + targetPosition);

        scrollToPosition(-targetPosition);
    }

    public void scrollLeft() {
        double currentTranslate = Math.abs(container.getTranslateX());
        double visibleWidth = this.getWidth();
        int totalCards = container.getChildren().size();

        System.out.println("=== ScrollLeft Debug ===");
        System.out.println("Current translateX: " + container.getTranslateX());

        // Tính vị trí thực tế của từng card
        double[] cardPositions = new double[totalCards];
        double cumulativePosition = 0;
        for (int i = 0; i < totalCards; i++) {
            cardPositions[i] = cumulativePosition;
            javafx.scene.Node child = container.getChildren().get(i);
            double childWidth = child.getBoundsInParent().getWidth();
            cumulativePosition += childWidth + container.getSpacing();
        }

        // Tìm card trước đó chưa hiển thị đầy đủ
        int prevCardIndex = -1;
        for (int i = totalCards - 1; i >= 0; i--) {
            javafx.scene.Node child = container.getChildren().get(i);
            double cardStart = cardPositions[i];

            // Vị trí card trong viewport hiện tại
            double cardStartInViewport = cardStart - currentTranslate;

            // Nếu card này bị cắt bên trái viewport hoặc nằm ngoài viewport bên trái
            if (cardStartInViewport < 1) {
                prevCardIndex = Math.max(0, i - 1);
                System.out.println("Found prev card to scroll to: " + prevCardIndex);
                break;
            }
        }

        // Nếu không tìm thấy, scroll về đầu
        if (prevCardIndex == -1) {
            System.out.println("Already at the start, scrolling to 0");
            scrollToPosition(0);
            return;
        }

        double targetPosition = cardPositions[prevCardIndex];
        System.out.println("Target position: " + targetPosition);

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
        System.out.println("=== scrollHorizontal() ENTERED ===");
        System.out.println("Amount: " + amount);
        System.out.println("Container: " + container);
        System.out.println("Current translateX: " + container.getTranslateX());

        TranslateTransition transition = new TranslateTransition(
                Duration.millis(300),
                container
        );
        double currentTranslate = container.getTranslateX();
        double newTranslate = currentTranslate + amount;

        System.out.println("New translate (before clamp): " + newTranslate);

        newTranslate = Math.max(-getMaxScroll(), Math.min(0, newTranslate));

        System.out.println("New translate (after clamp): " + newTranslate);

        transition.setToX(newTranslate);
        transition.setOnFinished(e -> {
            System.out.println("Transition finished");
            updateNavButtonVisibility();
        });
        transition.play();
        System.out.println("=== scrollHorizontal() COMPLETED ===");
    }

    private double getMaxScroll() {
        double visibleWidth = this.getWidth();

        // Tính tổng width thực tế của children
        double contentWidth = 0;
        System.out.println("=== getMaxScroll Debug ===");
        System.out.println("Visible width: " + visibleWidth);
        System.out.println("Children count: " + container.getChildren().size());

        for (int i = 0; i < container.getChildren().size(); i++) {
            javafx.scene.Node child = container.getChildren().get(i);
            double childWidth = child.getBoundsInParent().getWidth();
            contentWidth += childWidth;
            System.out.println("Child " + i + " width: " + childWidth);
        }

        // Thêm spacing
        if (container.getChildren().size() > 1) {
            double totalSpacing = container.getSpacing() * (container.getChildren().size() - 1);
            contentWidth += totalSpacing;
            System.out.println("Total spacing: " + totalSpacing);
        }

        double maxScroll = Math.max(0, contentWidth - visibleWidth);
        System.out.println("Content width: " + contentWidth);
        System.out.println("Max scroll: " + maxScroll);

        return maxScroll;
    }

    public void setSpacing(double spacing) {
        container.setSpacing(spacing);
    }

    public void setScrollAmount(double amount) {
        this.scrollAmount = amount;
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
            System.out.println("=== NextBtn CLICKED ===");
            System.out.println("onNext is null? " + (onNext == null));
            if (onNext != null) {
                System.out.println("Calling onNext.run()");
                onNext.run();
            }
        });
        StackPane.setAlignment(nextBtn, Pos.CENTER_LEFT);
        StackPane.setMargin(nextBtn, new Insets(0, 0, 0, 300));
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

                System.out.println("UpdateNav - Card " + i + " WIDTH DEBUG:");
                System.out.println("  boundsInParent: " + cardWidth);
                System.out.println("  boundsInLocal: " + child.getBoundsInLocal().getWidth());
                System.out.println("  layoutBounds: " + child.getLayoutBounds().getWidth());
                System.out.println("  cardX (minX): " + cardX);
                System.out.println("UpdateNav - Card " + i + ": start=" + cardStart + ", end=" + cardEnd +
                                 ", visible=" + visibleAmount + "px (" + (visiblePercentage * 100) + "%)");
                System.out.println("  viewportStart=" + viewportStart + ", viewportEnd=" + viewportEnd);

                if (cardEnd > viewportStart + 1) {
                    if (visiblePercentage < 0.8) {
                        hasUnviewedCards = true;
                        System.out.println("UpdateNav - Found unviewed card " + i + " (" + (visiblePercentage * 100) + "% visible)");
                        break;
                    }
                }
            }
        }

        boolean canScrollRight = hasUnviewedCards;

        System.out.println("=== Nav Button Visibility Debug ===");
        System.out.println("currentTranslate: " + (-currentTranslate));
        System.out.println("maxScroll: " + maxScroll);
        System.out.println("Viewport: " + currentTranslate + " to " + (currentTranslate + visibleWidth));
        System.out.println("Has unviewed cards: " + hasUnviewedCards);
        System.out.println("Can scroll left: " + canScrollLeft);
        System.out.println("Can scroll right: " + canScrollRight);

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
