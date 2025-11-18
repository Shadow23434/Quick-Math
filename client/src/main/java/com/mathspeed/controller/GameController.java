package com.mathspeed.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class GameController {

    // Root includes
    @FXML private StackPane countdownOverlay; // injected from CountDownOverlay.fxml via fx:include id
    @FXML private Label countdownLabel;

    // Main game fields
    @FXML private Label roundLabel;
    @FXML private Label targetNumberLabel;
    @FXML private TextField resultTextField;
    @FXML private Button clearButton;
    @FXML private Button submitButton;
    @FXML private Label attemptsLabel;

    @FXML private ToggleButton plusOp;
    @FXML private ToggleButton minusOp;
    @FXML private ToggleButton multiplyOp;
    @FXML private ToggleButton divideOp;

    @FXML private HBox serverNumbersRow;

    // Result banner fields (included)
    @FXML private VBox roundResultBanner; // wrapper from the include
    @FXML private Label youScoreLabel;
    @FXML private Label opponentScoreLabel;
    @FXML private Label yourOutcomeLabel;
    @FXML private Label opponentOutcomeLabel;

    private final List<ToggleButton> numberButtons = new ArrayList<>();
    private int attempts = 0;
    private Timeline countdownTimeline;

    @FXML
    public void initialize() {
        // Initially hide the included components if present
        if (countdownOverlay != null) {
            countdownOverlay.setVisible(false);
            countdownOverlay.setManaged(false);
        }

        if (roundResultBanner != null) {
            roundResultBanner.setVisible(false);
            roundResultBanner.setManaged(false);
        }

        clearButton.setOnAction(e -> handleClear());
        submitButton.setOnAction(e -> handleSubmit());

        setInputsDisabled(true);

        // If serverNumbersRow already has placeholder buttons, collect them
        serverNumbersRow.getChildren().forEach(n -> {
            if (n instanceof ToggleButton) numberButtons.add((ToggleButton) n);
        });
    }

    // Start countdown (seconds) and run onFinished when completes
    public void startCountdown(int seconds, Runnable onFinished) {
        Platform.runLater(() -> {
            if (countdownOverlay != null) {
                countdownOverlay.setVisible(true);
                countdownOverlay.setManaged(true);
            }
            countdownLabel.setText(String.valueOf(seconds));
            setInputsDisabled(true);

            if (countdownTimeline != null) countdownTimeline.stop();
            countdownTimeline = new Timeline();
            for (int i = 0; i <= seconds; i++) {
                final int t = seconds - i;
                countdownTimeline.getKeyFrames().add(new KeyFrame(Duration.seconds(i), ev -> countdownLabel.setText(String.valueOf(t))));
            }
            countdownTimeline.getKeyFrames().add(new KeyFrame(Duration.seconds(seconds + 0.15), ev -> {
                if (countdownOverlay != null) {
                    countdownOverlay.setVisible(false);
                    countdownOverlay.setManaged(false);
                }
                setInputsDisabled(false);
                if (onFinished != null) onFinished.run();
            }));
            countdownTimeline.play();
        });
    }

    /**
     * Replace current number buttons with provided server numbers.
     * selectionChangeListener receives currently selected numbers when selections change.
     */
    public void populateServerNumbers(List<Integer> numbers, Consumer<List<Integer>> selectionChangeListener) {
        Platform.runLater(() -> {
            serverNumbersRow.getChildren().clear();
            numberButtons.clear();
            for (Integer v : numbers) {
                ToggleButton btn = new ToggleButton(String.valueOf(v));
                btn.getStyleClass().add("number-button");
                btn.setOnAction(e -> {
                    if (selectionChangeListener != null) {
                        List<Integer> selected = new ArrayList<>();
                        for (ToggleButton tb : numberButtons) {
                            if (tb.isSelected()) selected.add(Integer.parseInt(tb.getText()));
                        }
                        selectionChangeListener.accept(selected);
                    }
                });
                numberButtons.add(btn);
                serverNumbersRow.getChildren().add(btn);
            }
        });
    }

    @FXML
    public void handleClear() {
        resultTextField.clear();
        // Reset attempts count if you want (keeps UX option)
        // attempts = 0; updateAttemptsLabel();
        plusOp.setSelected(false);
        minusOp.setSelected(false);
        multiplyOp.setSelected(false);
        divideOp.setSelected(false);
        numberButtons.forEach(b -> b.setSelected(false));
    }

    @FXML
    public void handleSubmit() {
        attempts++;
        updateAttemptsLabel();

        String freeText = resultTextField.getText();
        List<Integer> selectedNumbers = new ArrayList<>();
        for (ToggleButton tb : numberButtons) if (tb.isSelected()) selectedNumbers.add(Integer.parseInt(tb.getText()));

        List<String> ops = new ArrayList<>();
        if (plusOp.isSelected()) ops.add("+");
        if (minusOp.isSelected()) ops.add("-");
        if (multiplyOp.isSelected()) ops.add("*");
        if (divideOp.isSelected()) ops.add("/");

        // TODO: Send submission to server or evaluate locally:
        // payload should include: freeText, selectedNumbers, ops, attempts, round id...
        System.out.println("Submit -> text=" + freeText + " nums=" + selectedNumbers + " ops=" + ops);
    }

    private void updateAttemptsLabel() {
        attemptsLabel.setText("Attempts: " + attempts);
    }

    private void setInputsDisabled(boolean disabled) {
        resultTextField.setDisable(disabled);
        clearButton.setDisable(disabled);
        submitButton.setDisable(disabled);
        plusOp.setDisable(disabled);
        minusOp.setDisable(disabled);
        multiplyOp.setDisable(disabled);
        divideOp.setDisable(disabled);
        numberButtons.forEach(b -> b.setDisable(disabled));
    }

    /**
     * Show the round result banner.
     * yourOutcome/opponentOutcome expected to be integers like +1 or -1
     */
    public void showRoundResult(int yourOutcome, int opponentOutcome, int yourScore, int opponentScore, int visibleMs) {
        Platform.runLater(() -> {
            if (yourOutcomeLabel != null) {
                yourOutcomeLabel.setText((yourOutcome >= 0 ? "+" : "") + yourOutcome);
                yourOutcomeLabel.setStyle(yourOutcome > 0 ? "-fx-text-fill: green; -fx-font-weight:bold;" : "-fx-text-fill: red; -fx-font-weight:bold;");
            }
            if (opponentOutcomeLabel != null) {
                opponentOutcomeLabel.setText((opponentOutcome >= 0 ? "+" : "") + opponentOutcome);
                opponentOutcomeLabel.setStyle(opponentOutcome > 0 ? "-fx-text-fill: green; -fx-font-weight:bold;" : "-fx-text-fill: red; -fx-font-weight:bold;");
            }
            if (youScoreLabel != null) youScoreLabel.setText(String.valueOf(yourScore));
            if (opponentScoreLabel != null) opponentScoreLabel.setText(String.valueOf(opponentScore));

            if (roundResultBanner != null) {
                roundResultBanner.setVisible(true);
                roundResultBanner.setManaged(true);

                if (visibleMs > 0) {
                    Timeline t = new Timeline(new KeyFrame(Duration.millis(visibleMs), ev -> {
                        roundResultBanner.setVisible(false);
                        roundResultBanner.setManaged(false);
                    }));
                    t.play();
                }
            }
        });
    }

    // Convenience method used when a new round starts (example)
    public void onRoundStart(int roundNumber, int targetNumber, List<Integer> serverNumbers) {
        Platform.runLater(() -> {
            roundLabel.setText("Round " + roundNumber);
            targetNumberLabel.setText(String.valueOf(targetNumber));
            handleClear();
            populateServerNumbers(serverNumbers, null);
            startCountdown(5, null);
        });
    }
}
