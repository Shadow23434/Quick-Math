package com.mathspeed.controller;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.util.Duration;

import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * GamePlayController - controller cho gameplay.fxml
 *
 * Lưu ý:
 * - Logic màn hình kết quả (match result) đã tách ra thành MatchResultController riêng.
 * - GamePlayController chỉ giữ UI chính: palette, input grid, per-round timer, overlay countdown, token logic, evaluator.
 */
public class GamePlayController {

    // Root / common UI
    @FXML private StackPane rootPane;
    @FXML private ImageView backgroundImageView;

    // Palette
    @FXML private FlowPane numberRow;
    @FXML private FlowPane operatorRow;
    @FXML private FlowPane palettePane; // optional

    // Input grid
    @FXML private ScrollPane inputScroll;
    @FXML private TilePane inputGrid;       // TilePane grid (15 x 5)
    @FXML private Label slotCounterLabel;   // shows "count / capacity"

    // Player labels
    @FXML private Label player1ScoreLabel;
    @FXML private Label player2ScoreLabel;
    @FXML private Label player1TotalTimeLabel;
    @FXML private Label player2TotalTimeLabel;
    @FXML private Label targetNumberLabel;

    // Per-round timer UI
    @FXML private ImageView perRoundPlaque;       // plaque background ImageView
    @FXML private ImageView perRoundClockIcon;    // tiny clock ImageView
    @FXML private Label perRoundTimeLabel;        // label that displays remaining time

    @FXML private Label questionLabel;

    // Countdown overlay (start-of-round)
    @FXML private StackPane countdownOverlay;
    @FXML private Label countdownLabel;

    // Control buttons
    @FXML private Button cancelButton;
    @FXML private Button clearButton;
    @FXML private Button submitButton;
    @FXML private Button startDemoButton;

    // Grid constraints (match FXML)
    private final int MAX_COLUMNS = 15;
    private final int MAX_ROWS = 5;
    private final int MAX_TOKENS = MAX_COLUMNS * MAX_ROWS; // 75
    private final int MAX_TOKENS_SAFETY = MAX_TOKENS;

    // Preferred token tile size to fit grid in visible area
    private final double PREF_TILE_WIDTH = 36.0;
    private final double PREF_TILE_HEIGHT = 28.0;

    // Countdown state (overlay)
    private Timeline countdownTimeline;      // for overlay count (start round)
    private int remainingSeconds;

    // Per-round timer state
    private Timeline perRoundTimeline;
    private int perRoundRemainingSeconds;

    @FXML
    public void initialize() {
        // UI setup on JavaFX thread
        Platform.runLater(() -> {
            if (backgroundImageView != null && rootPane != null) {
                backgroundImageView.fitWidthProperty().bind(rootPane.widthProperty());
                backgroundImageView.fitHeightProperty().bind(rootPane.heightProperty());
                backgroundImageView.setSmooth(true);
                backgroundImageView.setCache(true);
            }
            if (rootPane != null) {
                rootPane.setFocusTraversable(true);
                Platform.runLater(() -> rootPane.requestFocus());
            }
            if (inputGrid != null) {
                inputGrid.setPrefColumns(MAX_COLUMNS);
                inputGrid.setPrefTileWidth(PREF_TILE_WIDTH);
                inputGrid.setPrefTileHeight(PREF_TILE_HEIGHT);
            }
            if (inputScroll != null) {
                inputScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                inputScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            }

            // Load external CSS if available
            try {
                URL cssUrl = getClass().getResource("/css/gameplay.css");
                if (cssUrl != null) {
                    String css = cssUrl.toExternalForm();
                    if (!rootPane.getStylesheets().contains(css)) {
                        rootPane.getStylesheets().add(css);
                    }
                } else {
                    System.err.println("Warning: gameplay.css not found on classpath at /css/gameplay.css");
                }
            } catch (Exception ex) {
                System.err.println("Error loading CSS: " + ex.getMessage());
            }

            // Hide countdown overlay initially
            if (countdownOverlay != null) {
                countdownOverlay.setVisible(false);
                countdownOverlay.setManaged(false);
            }
        });

        // Backspace handler to undo last token
        if (rootPane != null) {
            rootPane.addEventHandler(KeyEvent.KEY_PRESSED, evt -> {
                if (evt.getCode() == KeyCode.BACK_SPACE) {
                    handleUndoLastToken();
                    evt.consume();
                }
            });
        }

        updateCounter();
    }

    // ---------------- Per-round timer API ----------------

    /**
     * Start the per-round timer that updates perRoundTimeLabel each second.
     * onFinish will be invoked once timeout reaches zero.
     */
    public void startPerRoundTimer(int seconds, Runnable onFinish) {
        stopPerRoundTimer();
        perRoundRemainingSeconds = Math.max(0, seconds);
        updatePerRoundLabel(perRoundRemainingSeconds);

        perRoundTimeline = new Timeline(new KeyFrame(Duration.seconds(1), evt -> {
            perRoundRemainingSeconds--;
            if (perRoundRemainingSeconds >= 0) {
                updatePerRoundLabel(perRoundRemainingSeconds);
            }
            if (perRoundRemainingSeconds <= 0) {
                stopPerRoundTimer();
                if (onFinish != null) Platform.runLater(onFinish);
            }
        }));
        perRoundTimeline.setCycleCount(perRoundRemainingSeconds + 1);
        perRoundTimeline.playFromStart();
    }

    public void stopPerRoundTimer() {
        if (perRoundTimeline != null) {
            perRoundTimeline.stop();
            perRoundTimeline = null;
        }
    }

    private void updatePerRoundLabel(int secondsLeft) {
        Platform.runLater(() -> {
            if (perRoundTimeLabel != null) {
                perRoundTimeLabel.setText(secondsLeft + "s");
            }
        });
    }

    // ---------------- Countdown overlay (start round) ----------------

    /**
     * Internal overlay countdown. Use onServerStartRequest to start from server.
     */
    private void startCountdown(int seconds, Runnable onFinish) {
        if (seconds <= 0) {
            if (onFinish != null) Platform.runLater(onFinish);
            return;
        }

        stopCountdown();
        remainingSeconds = seconds;

        Platform.runLater(() -> {
            if (countdownOverlay == null || countdownLabel == null) {
                if (onFinish != null) Platform.runLater(onFinish);
                return;
            }

            countdownLabel.setText(String.valueOf(remainingSeconds));
            countdownOverlay.setOpacity(0);
            countdownOverlay.setVisible(true);
            countdownOverlay.setManaged(true);

            if (cancelButton != null) cancelButton.setDisable(false);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(200), countdownOverlay);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.play();

            playCountTickAnimation(countdownLabel);

            countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), evt -> {
                remainingSeconds--;
                if (remainingSeconds > 0) {
                    countdownLabel.setText(String.valueOf(remainingSeconds));
                    playCountTickAnimation(countdownLabel);
                } else {
                    countdownLabel.setText("Bắt đầu");
                    playFinalAnimation(countdownLabel, () -> {
                        FadeTransition fadeOut = new FadeTransition(Duration.millis(220), countdownOverlay);
                        fadeOut.setFromValue(1.0);
                        fadeOut.setToValue(0.0);
                        fadeOut.setOnFinished(ev -> {
                            countdownOverlay.setVisible(false);
                            countdownOverlay.setManaged(false);
                            if (cancelButton != null) cancelButton.setDisable(true);
                            if (onFinish != null) Platform.runLater(onFinish);
                        });
                        fadeOut.play();
                    });
                    if (countdownTimeline != null) countdownTimeline.stop();
                }
            }));
            countdownTimeline.setCycleCount(Timeline.INDEFINITE);
            countdownTimeline.playFromStart();
        });
    }

    private void stopCountdown() {
        Platform.runLater(() -> {
            if (countdownTimeline != null) {
                countdownTimeline.stop();
                countdownTimeline = null;
            }
            if (countdownOverlay != null) {
                countdownOverlay.setVisible(false);
                countdownOverlay.setManaged(false);
            }
            if (cancelButton != null) cancelButton.setDisable(true);
        });
    }

    private void playCountTickAnimation(Node labelNode) {
        if (labelNode == null) return;

        ScaleTransition st = new ScaleTransition(Duration.millis(220), labelNode);
        st.setFromX(0.75);
        st.setFromY(0.75);
        st.setToX(1.18);
        st.setToY(1.18);
        st.setAutoReverse(true);
        st.setCycleCount(2);

        FadeTransition ft = new FadeTransition(Duration.millis(220), labelNode);
        ft.setFromValue(0.85);
        ft.setToValue(1.0);

        ParallelTransition pt = new ParallelTransition(st, ft);
        pt.play();
    }

    private void playFinalAnimation(Node labelNode, Runnable after) {
        if (labelNode == null) {
            if (after != null) after.run();
            return;
        }

        SequentialTransition seq = new SequentialTransition();

        ScaleTransition st1 = new ScaleTransition(Duration.millis(180), labelNode);
        st1.setFromX(0.9);
        st1.setFromY(0.9);
        st1.setToX(1.3);
        st1.setToY(1.3);

        FadeTransition ft1 = new FadeTransition(Duration.millis(180), labelNode);
        ft1.setFromValue(0.9);
        ft1.setToValue(1.0);

        PauseTransition pause = new PauseTransition(Duration.millis(320));

        seq.getChildren().addAll(new ParallelTransition(st1, ft1), pause);
        seq.setOnFinished(evt -> {
            if (after != null) after.run();
        });
        seq.play();
    }

    // ---------------- demo start & cancel handlers ----------------

    @FXML
    protected void handleStartDemo(ActionEvent event) {
        if (startDemoButton != null) startDemoButton.setDisable(true);

        // simulate network/server reply and then call onServerStartRequest
        PauseTransition simulateNetworkDelay = new PauseTransition(Duration.millis(300));
        simulateNetworkDelay.setOnFinished(e -> {
            onServerStartRequest(3, () -> Platform.runLater(() -> {
                if (startDemoButton != null) startDemoButton.setDisable(false);
                System.out.println("Server-triggered demo countdown finished.");
            }));
        });
        simulateNetworkDelay.play();
    }

    @FXML
    protected void handleCancel(ActionEvent event) {
        // abort any overlay countdown
        stopCountdown();
        // optionally abort per-round timer as well
        stopPerRoundTimer();
        if (startDemoButton != null) startDemoButton.setDisable(false);
        System.out.println("Countdown aborted by user (Hủy).");
    }

    /**
     * Called by server/network code to request start of round. Starts overlay countdown.
     */
    public void onServerStartRequest(int seconds, Runnable onFinish) {
        Platform.runLater(() -> {
            if (startDemoButton != null) startDemoButton.setDisable(true);
            startCountdown(seconds, () -> {
                if (startDemoButton != null) startDemoButton.setDisable(false);
                if (onFinish != null) onFinish.run();
            });
        });
    }

    // ---------------- Existing UI/token logic ----------------

    @FXML
    protected void handlePaletteButton(ActionEvent event) {
        if (!(event.getSource() instanceof Button)) return;
        Button src = (Button) event.getSource();
        if (inputGrid == null) return;

        String newText = src.getText();

        if (inputGrid.getChildren().size() >= MAX_TOKENS_SAFETY) {
            flashNode(src);
            return;
        }

        if (inputGrid.getChildren().size() > 0 && isDigitString(newText)) {
            Node last = inputGrid.getChildren().get(inputGrid.getChildren().size() - 1);
            if (last instanceof ToggleButton) {
                String lastText = ((ToggleButton) last).getText();
                if (isDigitString(lastText)) {
                    flashNode(src);
                    return;
                }
            }
        }

        ToggleButton token = new ToggleButton(newText);
        token.getStyleClass().addAll(src.getStyleClass());
        token.setFocusTraversable(false);
        token.setPrefSize(PREF_TILE_WIDTH, PREF_TILE_HEIGHT);
        token.setOnAction(e -> removeToken(token));

        inputGrid.getChildren().add(token);
        playPlaceAnimation(token);
        updateCounter();
        scrollToEnd();
    }

    private boolean isDigitString(String s) {
        if (s == null || s.length() == 0) return false;
        return s.length() == 1 && Character.isDigit(s.charAt(0));
    }

    private void removeToken(Node token) {
        if (token == null || inputGrid == null) return;
        inputGrid.getChildren().remove(token);
        playReturnAnimation(token);
        updateCounter();
    }

    private void scrollToEnd() {
        if (inputScroll == null) return;
        Platform.runLater(() -> {
            try {
                inputScroll.setHvalue(1.0);
                inputScroll.setVvalue(1.0);
            } catch (Exception ignored) {}
        });
    }

    private void updateCounter() {
        if (slotCounterLabel != null && inputGrid != null) {
            slotCounterLabel.setText(inputGrid.getChildren().size() + "/" + MAX_TOKENS);
        }
    }

    // ---------------- Animations / helpers ----------------

    private void playPlaceAnimation(Node node) {
        ScaleTransition st = new ScaleTransition(Duration.millis(130), node);
        st.setFromX(0.85);
        st.setFromY(0.85);
        st.setToX(1.0);
        st.setToY(1.0);

        FadeTransition ft = new FadeTransition(Duration.millis(130), node);
        ft.setFromValue(0.85);
        ft.setToValue(1.0);

        st.play();
        ft.play();
    }

    private void playReturnAnimation(Node node) {
        ScaleTransition st = new ScaleTransition(Duration.millis(120), node);
        st.setFromX(1.05);
        st.setFromY(1.05);
        st.setToX(1.0);
        st.setToY(1.0);
        st.play();
    }

    private void flashNode(Node node) {
        FadeTransition ft = new FadeTransition(Duration.millis(140), node);
        ft.setFromValue(1.0);
        ft.setToValue(0.5);
        ft.setAutoReverse(true);
        ft.setCycleCount(2);
        ft.play();
    }

    // ---------------- Clear / Submit / Undo / Evaluate ----------------

    @FXML
    protected void handleClear(ActionEvent event) {
        if (inputGrid == null) return;
        List<Node> snapshot = new ArrayList<>(inputGrid.getChildren());
        for (Node n : snapshot) {
            inputGrid.getChildren().remove(n);
        }
        updateCounter();
    }

    @FXML
    protected void handleSubmit(ActionEvent event) {
        if (inputGrid == null) return;
        StringBuilder sb = new StringBuilder();
        for (Node node : inputGrid.getChildren()) {
            if (node instanceof ToggleButton) {
                sb.append(((ToggleButton) node).getText());
            } else {
                sb.append(node.toString());
            }
        }
        String expr = sb.toString();
        String exprForEval = expr.replace("×", "*").replace("÷", "/");

        System.out.println("Submitting expression: " + exprForEval);
        try {
            Double result = evaluateExpression(exprForEval);
            if (result != null) {
                System.out.println("Result = " + result);
                if (targetNumberLabel != null) {
                    try {
                        double target = Double.parseDouble(targetNumberLabel.getText());
                        double eps = 1e-9;
                        if (Math.abs(result - target) <= eps) {
                            System.out.println("MATCH: expression equals the target!");
                        } else {
                            System.out.println("NOT MATCH: target = " + target);
                        }
                    } catch (NumberFormatException ignore) {
                        // ignore
                    }
                }
            } else {
                System.out.println("Invalid expression or evaluation error.");
            }
        } catch (Exception ex) {
            System.out.println("Evaluation error: " + ex.getMessage());
        }
    }

    public void handleUndoLastToken() {
        if (inputGrid == null) return;
        int n = inputGrid.getChildren().size();
        if (n == 0) return;
        Node last = inputGrid.getChildren().get(n - 1);
        removeToken(last);
    }

    // ---------------- Simple evaluator (shunting-yard + RPN eval) ----------------

    private Double evaluateExpression(String expr) {
        try {
            List<String> rpn = toRPN(expr);
            if (rpn == null) return null;
            Deque<Double> stack = new ArrayDeque<>();
            for (String token : rpn) {
                if (isNumber(token)) {
                    stack.push(Double.parseDouble(token));
                } else if (isOperator(token)) {
                    if (stack.size() < 2) return null;
                    double b = stack.pop();
                    double a = stack.pop();
                    double res;
                    switch (token) {
                        case "+":
                            res = a + b;
                            break;
                        case "-":
                            res = a - b;
                            break;
                        case "*":
                            res = a * b;
                            break;
                        case "/":
                            if (b == 0) return null;
                            res = a / b;
                            break;
                        default:
                            return null;
                    }
                    stack.push(res);
                } else {
                    return null;
                }
            }
            if (stack.size() != 1) return null;
            return stack.pop();
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> toRPN(String expr) {
        List<String> output = new ArrayList<>();
        Deque<String> ops = new ArrayDeque<>();

        int i = 0;
        int len = expr.length();
        while (i < len) {
            char c = expr.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            if (Character.isDigit(c) || c == '.') {
                StringBuilder num = new StringBuilder();
                while (i < len && (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.')) {
                    num.append(expr.charAt(i));
                    i++;
                }
                output.add(num.toString());
                continue;
            }

            if (isOperatorChar(c)) {
                String op = String.valueOf(c);
                while (!ops.isEmpty() && isOperator(ops.peek())) {
                    String top = ops.peek();
                    if ((isLeftAssociative(op) && precedence(op) <= precedence(top)) ||
                            (!isLeftAssociative(op) && precedence(op) < precedence(top))) {
                        output.add(ops.pop());
                    } else break;
                }
                ops.push(op);
                i++;
                continue;
            }

            if (c == '(') {
                ops.push("(");
                i++;
                continue;
            }

            if (c == ')') {
                boolean found = false;
                while (!ops.isEmpty()) {
                    String top = ops.pop();
                    if ("(".equals(top)) {
                        found = true;
                        break;
                    } else {
                        output.add(top);
                    }
                }
                if (!found) return null; // mismatched parentheses
                i++;
                continue;
            }

            return null;
        }

        while (!ops.isEmpty()) {
            String top = ops.pop();
            if ("(".equals(top) || ")".equals(top)) return null; // mismatched
            output.add(top);
        }
        return output;
    }

    private boolean isNumber(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private boolean isOperator(String s) {
        return "+".equals(s) || "-".equals(s) || "*".equals(s) || "/".equals(s);
    }

    private boolean isOperatorChar(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/' ;
    }

    private int precedence(String op) {
        if (op == null) return 0;
        switch (op) {
            case "+":
            case "-":
                return 1;
            case "*":
            case "/":
                return 2;
            default:
                return 0;
        }
    }

    private boolean isLeftAssociative(String op) {
        return true;
    }
}