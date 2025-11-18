package com.mathspeed.controller;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Controller cho mathresult.fxml (overlay kết quả trận đấu).
 * Riêng biệt, dễ test và reuse.
 */
public class MatchResultController {

    @FXML private StackPane matchResultRoot;
    @FXML private ImageView matchResultBackground;
    @FXML private ImageView winnerStarImage;
    @FXML private ImageView loserStarImage;
    @FXML private Label winnerNameLabel;
    @FXML private Label loserNameLabel;
    @FXML private Label winnerScoreLabel;
    @FXML private Label loserScoreLabel;
    @FXML private Label winnerTotalTimeLabel;
    @FXML private Label loserTotalTimeLabel;
//    @FXML private Button playAgainButton;
    @FXML private Button closeResultButton;

//    private Runnable onPlayAgain;
    private Runnable onClose;

    @FXML
    public void initialize() {
        // debug log to confirm controller initialization
        System.out.println("MatchResultController initialized, root=" + matchResultRoot);

        // bind background image size to root so it always covers the overlay
        if (matchResultBackground != null && matchResultRoot != null) {
            matchResultBackground.fitWidthProperty().bind(matchResultRoot.widthProperty());
            matchResultBackground.fitHeightProperty().bind(matchResultRoot.heightProperty());
        }

        // ensure hidden initially
        if (matchResultRoot != null) {
            matchResultRoot.setVisible(false);
            matchResultRoot.setManaged(false);
        }
    }

    /**
     * Show overlay và populate dữ liệu.
     */
    public void show(String winnerName, int winnerScore, double winnerTotalTime,
                     String loserName, int loserScore, double loserTotalTime) {
        Platform.runLater(() -> {
            if (winnerNameLabel != null) winnerNameLabel.setText(winnerName);
            if (loserNameLabel != null) loserNameLabel.setText(loserName);
            if (winnerScoreLabel != null) winnerScoreLabel.setText(String.valueOf(winnerScore));
            if (loserScoreLabel != null) loserScoreLabel.setText(String.valueOf(loserScore));
            if (winnerTotalTimeLabel != null) winnerTotalTimeLabel.setText(String.format("%.1fs", winnerTotalTime));
            if (loserTotalTimeLabel != null) loserTotalTimeLabel.setText(String.format("%.1fs", loserTotalTime));

            // small winner star pop
            if (winnerStarImage != null) {
                ScaleTransition st = new ScaleTransition(Duration.millis(420), winnerStarImage);
                st.setFromX(0.75); st.setFromY(0.75);
                st.setToX(1.15); st.setToY(1.15);
                st.setAutoReverse(true);
                st.setCycleCount(2);
                st.play();
            }

            if (matchResultRoot != null) {
                matchResultRoot.setOpacity(0);
                matchResultRoot.setVisible(true);
                matchResultRoot.setManaged(true);
                FadeTransition ft = new FadeTransition(Duration.millis(220), matchResultRoot);
                ft.setFromValue(0.0); ft.setToValue(1.0);
                ft.play();
            }

//            if (playAgainButton != null) playAgainButton.setDisable(false);
            if (closeResultButton != null) closeResultButton.setDisable(false);
        });
    }

    /**
     * Ẩn overlay
     */
    public void hide() {
        Platform.runLater(() -> {
            if (matchResultRoot != null && matchResultRoot.isVisible()) {
                FadeTransition ft = new FadeTransition(Duration.millis(160), matchResultRoot);
                ft.setFromValue(1.0); ft.setToValue(0.0);
                ft.setOnFinished(e -> {
                    matchResultRoot.setVisible(false);
                    matchResultRoot.setManaged(false);
                });
                ft.play();
            }
        });
    }

//    @FXML
//    protected void handlePlayAgain(ActionEvent event) {
//        if (onPlayAgain != null) onPlayAgain.run();
//    }

    @FXML
    protected void handleCloseResult(ActionEvent event) {
        if (onClose != null) onClose.run();
        else hide();
    }

//    public void setOnPlayAgain(Runnable r) { this.onPlayAgain = r; }
    public void setOnClose(Runnable r) { this.onClose = r; }
}