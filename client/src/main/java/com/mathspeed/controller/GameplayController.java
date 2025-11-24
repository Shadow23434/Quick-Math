package com.mathspeed.controller;

import com.mathspeed.model.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
import javafx.application.Platform;
import javafx.stage.Modality;
import javafx.stage.Stage;
import com.mathspeed.util.ExpressionEvaluator;
import com.mathspeed.client.GameplayClient;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GameplayController
 *
 * Responsibilities:
 * - Populate player/opponent names from MATCH_START_INFO
 * - Show initial 5s countdown before round 1 (only once)
 * - Show 3s countdown before subsequent rounds
 * - Run per-round decreasing timer updating timerLabel
 * - Handle expression input (numbers/operators/brackets/backspace/clear/submit)
 *
 * Note: keep existing UI elements and behavior intact; only extend functionality.
 */
public class GameplayController {

    @FXML private Label playerNameLabel;
    @FXML private Label opponentNameLabel;
    @FXML private Label playerScoreLabel;
    @FXML private Label opponentScoreLabel;
    @FXML private Label timerLabel;
    @FXML private Label targetLabel;
    @FXML private Label expressionLabel;
    @FXML private Label roundLabel;
    @FXML private Label levelLabel;
    @FXML private Label gameStatusLabel;
    @FXML private Button clearButton;
    @FXML private Button submitButton;
    @FXML private Button exitButton;
    @FXML private Button backspaceButton;

    // Countdown overlay components (used for both initial and inter-round countdowns)
    @FXML private StackPane countdownOverlay;
    @FXML private VBox countdownContainer;
    @FXML private Label countdownLabel;
    @FXML private Label countdownMessageLabel;

    // Optional round transition overlay (may be null)
    @FXML private StackPane roundTransitionOverlay;
    @FXML private VBox roundTransitionContainer;
    @FXML private Label roundTransitionTitle;
    @FXML private Label roundTransitionTarget;
    @FXML private Label roundTransitionTime;
    @FXML private Label roundTransitionPlayerScore;
    @FXML private Label roundTransitionOpponentScore;
    @FXML private Label roundTransitionMessage;

    private String playerDisplayName = "You";
    private String opponentDisplayName = "Opponent";
    private StringBuilder expressionBuilder;
    private volatile boolean hasSubmitted = false;
    private volatile boolean hasExited = false;
    private int targetNumber;
    private int playerScore;
    private int opponentScore;
    private int timeRemaining;
    private int currentRound;
    private int currentLevel;
    private GameplayClient gameClient;
    private Timeline timer;
    private Timeline countdownTimer;
    private Timeline roundTransitionTimer;
    private Gson gson;
    private String playerUsername; // must be set externally to identify local player

    // Track whether the initial (5s) countdown has already been shown
    private volatile boolean initialCountdownShown = false;

    // Time sync fields
    private final AtomicLong offsetMs = new AtomicLong(0L);
    private final AtomicLong estimatedRttMs = new AtomicLong(150L);
    private final ConcurrentMap<Long, CompletableFuture<Long>> pendingPingReplies = new ConcurrentHashMap<>();

    private final ScheduledExecutorService syncExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "TimeSync");
        t.setDaemon(true);
        return t;
    });

    private final ScheduledExecutorService schedulingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "GameScheduling");
        t.setDaemon(true);
        return t;
    });

    private ScheduledFuture<?> periodicResyncFuture;
    private volatile long baseSystemMs = System.currentTimeMillis();
    private volatile long baseNano = System.nanoTime();

    @FXML
    public void initialize() {
        expressionBuilder = new StringBuilder();
        targetNumber = 0;
        playerScore = 0;
        opponentScore = 0;
        timeRemaining = 30;
        currentRound = 0;
        currentLevel = 1;
        gson = new Gson();

        if (countdownOverlay != null) {
            countdownOverlay.setVisible(false);
            countdownOverlay.setManaged(true);
            countdownOverlay.toFront();
        }
        if (roundTransitionOverlay != null) {
            roundTransitionOverlay.setVisible(false);
            roundTransitionOverlay.setManaged(true);
        }

        updatePlayerNames();
        updateDisplay();
    }

    /**
     * Initialize the GameplayClient and register handlers.
     * Call setPlayerUsername(...) before using this.
     */
    private void initializeGameClient() {
        gameClient = new GameplayClient("localhost", 8888,
                this::handleServerMessage,
                this::handleMatchStart); // typed handler

        try {
            gameClient.connect();
            showTemporaryFeedback("Đã kết nối server thành công", 3, "Chờ bắt đầu game...");
            startPeriodicTimeSync();
        } catch (IOException e) {
            showFeedback("Không thể kết nối server: " + e.getMessage());
        }
    }

    /**
     * Unified handler for MATCH_START_INFO messages.
     * Resets state, updates names and triggers initial countdown (only once).
     */
    private void handleMatchStart(MatchStartInfo info) {
        if (info == null) return;

        // reset match state
        playerScore = 0;
        opponentScore = 0;
        currentRound = 0;
        currentLevel = 1;
        hasSubmitted = false;
        hasExited = false;
        if (expressionBuilder != null) expressionBuilder.setLength(0);
        updateDisplay();

        // determine opponent display name
        String opponentDisplay = "Opponent";
        if (info.players != null) {
            for (Player p : info.players) {
                if (p == null) continue;
                String username = p.getUsername() != null ? p.getUsername() : "";
                String display = p.getDisplayName() != null ? p.getDisplayName() : "";
                if (playerUsername != null && !playerUsername.isEmpty() && username.equalsIgnoreCase(playerUsername)) {
                    // local — leave as "You"
                } else {
                    opponentDisplay = display;
                }
            }
        }

        final String finalOpp = opponentDisplay;
        int countdownSec = info.countdown_ms > 0 ? (int) (info.countdown_ms / 1000L) : 5;

        // update UI and start countdown (only once)
        if (initialCountdownShown) {
            Platform.runLater(() -> {
                opponentDisplayName = finalOpp;
                playerDisplayName = "You";
                updatePlayerNames();
                if (countdownLabel != null) countdownLabel.setText(String.valueOf(countdownSec));
                if (gameStatusLabel != null) gameStatusLabel.setText("Chơi với " + finalOpp);
            });
            return;
        }

        initialCountdownShown = true;

        Platform.runLater(() -> {
            opponentDisplayName = finalOpp;
            playerDisplayName = "You";
            updatePlayerNames();

            if (countdownLabel != null) countdownLabel.setText(String.valueOf(countdownSec));
            if (countdownMessageLabel != null) countdownMessageLabel.setText("Trận đấu sắp bắt đầu!");
            if (gameStatusLabel != null) gameStatusLabel.setText("Chuẩn bị trận đấu...");

            startMatchCountdown(countdownSec);
        });
    }

    private void updatePlayerNames() {
        if (playerNameLabel != null) playerNameLabel.setText(playerDisplayName);
        if (opponentNameLabel != null) opponentNameLabel.setText(opponentDisplayName);
    }

    // ---------------- Time sync ----------------

    public void startPeriodicTimeSync() {
        syncExecutor.execute(() -> {
            try {
                performTimeSync(6, 1200);
            } catch (Exception ex) {
                System.err.println("Initial time sync failed: " + ex.getMessage());
            }
        });

        periodicResyncFuture = syncExecutor.scheduleAtFixedRate(() -> {
            try {
                performTimeSync(2, 800);
            } catch (Exception ex) {
                // ignore periodic sync failures
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private void performTimeSync(int samples, int perSampleTimeoutMs) throws InterruptedException {
        long bestOffset = offsetMs.get();
        long bestRtt = Long.MAX_VALUE;

        for (int i = 0; i < Math.max(1, samples); i++) {
            long t0 = System.currentTimeMillis();
            CompletableFuture<Long> replyFuture = new CompletableFuture<>();
            pendingPingReplies.put(t0, replyFuture);

            try {
                if (gameClient != null && gameClient.isConnected()) {
                    gameClient.sendRaw("TIME_PING " + t0);
                } else {
                    pendingPingReplies.remove(t0);
                    break;
                }
            } catch (Exception ex) {
                pendingPingReplies.remove(t0);
                System.err.println("performTimeSync: failed to send TIME_PING: " + ex.getMessage());
                break;
            }

            try {
                Long serverTime = replyFuture.get(perSampleTimeoutMs, TimeUnit.MILLISECONDS);
                long t1 = System.currentTimeMillis();
                long rtt = Math.max(0L, t1 - t0);
                long estClientAtReply = t0 + rtt / 2;
                long sampleOffset = serverTime - estClientAtReply;

                if (rtt < bestRtt) {
                    bestRtt = rtt;
                    bestOffset = sampleOffset;
                }
            } catch (TimeoutException te) {
                pendingPingReplies.remove(t0);
                System.err.println("performTimeSync: TIME_PING timeout for sample " + i);
            } catch (ExecutionException ee) {
                pendingPingReplies.remove(t0);
                System.err.println("performTimeSync: TIME_PING execution error: " + ee.getMessage());
            } finally {
                Thread.sleep(40);
            }
        }

        if (bestRtt != Long.MAX_VALUE) {
            long prevOffset = offsetMs.get();
            long newOffset = (long) Math.round(0.7 * bestOffset + 0.3 * prevOffset);
            offsetMs.set(newOffset);
            estimatedRttMs.set(Math.max(0L, bestRtt));
            baseSystemMs = System.currentTimeMillis();
            baseNano = System.nanoTime();

            Platform.runLater(() -> showTemporaryFeedback("Đồng bộ thời gian hoàn tất (offset=" + offsetMs.get() + "ms)", 2, ""));

            System.out.println("performTimeSync: offset=" + offsetMs.get() + " rtt=" + estimatedRttMs.get());
        } else {
            System.err.println("performTimeSync: no successful time samples");
        }
    }

    // ---------------- Countdown & Round flow ----------------

    private void startMatchCountdown(int seconds) {
        if (seconds <= 0) seconds = 3;
        System.out.println("Starting countdown overlay for " + seconds + "s");

        if (countdownOverlay == null || countdownLabel == null || countdownMessageLabel == null) {
            System.err.println("Countdown UI not available; skipping overlay");
            PauseTransition fallback = new PauseTransition(Duration.seconds(seconds));
            fallback.setOnFinished(e -> enableButtonsSafely());
            fallback.play();
            return;
        }

        countdownOverlay.setVisible(true);
        countdownOverlay.setManaged(true);
        countdownOverlay.toFront();

        countdownMessageLabel.setText("Trận đấu sắp bắt đầu!");
        countdownLabel.setText(String.valueOf(seconds));
        disableButtonsSafely();

        final int[] left = {seconds};
        if (countdownTimer != null) {
            countdownTimer.stop();
            countdownTimer = null;
        }
        countdownTimer = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
            left[0]--;
            if (left[0] > 0) {
                countdownLabel.setText(String.valueOf(left[0]));
            } else {
                countdownLabel.setText("BẮT ĐẦU!");
                countdownMessageLabel.setText("Chúc may mắn!");
                PauseTransition hide = new PauseTransition(Duration.seconds(1));
                hide.setOnFinished(e2 -> {
                    stopCountdown();
                    enableButtonsSafely();
                    showTemporaryFeedback("Trận đấu bắt đầu!", 2, "");
                });
                hide.play();
            }
        }));
        countdownTimer.setCycleCount(seconds);
        countdownTimer.play();
    }

    private void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.stop();
            countdownTimer = null;
        }
        if (countdownOverlay != null) {
            countdownOverlay.setVisible(false);
            countdownOverlay.setManaged(false);
        }
    }

    /**
     * Handles server raw messages (generic dispatch).
     * This method is also used by TestClient via reflection.
     */
    public void handleServerMessage(String message) {
        System.out.println("Server message: " + message);

        if (message == null) return;
        String trimmed = message.trim();
        if (trimmed.isEmpty()) return;

        try {
            if (trimmed.startsWith("{") && (trimmed.contains("\"type\":\"time_pong\"") || trimmed.contains("\"type\":\"TIME_PONG\""))) {
                TimePong pong = gson.fromJson(trimmed, TimePong.class);
                // If a time_pong contains client_send that matches pending, complete it
                if (pong != null && pong.client_send != null && pong.server_time != null) {
                    CompletableFuture<Long> f = pendingPingReplies.remove(pong.client_send);
                    if (f != null) f.complete(pong.server_time);
                }
                return;
            }
            if (trimmed.startsWith("{") && trimmed.contains("\"type\":\"server_now\"")) {
                ServerNow now = gson.fromJson(trimmed, ServerNow.class);
                if (now != null && now.server_time != null) {
                    long clientNow = System.currentTimeMillis();
                    long sampleOffset = now.server_time - clientNow;
                    offsetMs.set((long) Math.round(0.6 * sampleOffset + 0.4 * offsetMs.get()));
                    baseSystemMs = System.currentTimeMillis();
                    baseNano = System.nanoTime();
                }
            }
        } catch (Exception ex) {
            // ignore parsing errors for time messages
        }

        Platform.runLater(() -> {
            try {
                if (trimmed.startsWith("{")) {
                    JsonElement je = JsonParser.parseString(trimmed);
                    JsonObject jo = je.getAsJsonObject();
                    String type = jo.has("type") ? jo.get("type").getAsString() : "";

                    if ("MATCH_START_INFO".equalsIgnoreCase(type)) {
                        MatchStartInfo info = gson.fromJson(trimmed, MatchStartInfo.class);
                        // ensure match start is handled (typed handler also calls handleMatchStart)
                        handleMatchStart(info);
                    } else if ("NEW_ROUND".equalsIgnoreCase(type)) {
                        NewRound round = gson.fromJson(trimmed, NewRound.class);
                        handleNewRoundWithCountdown(round);
                    } else if ("ANSWER_RESULT".equalsIgnoreCase(type)) {
                        AnswerResult result = gson.fromJson(trimmed, AnswerResult.class);
                        handleAnswerResult(result);
                    } else if ("ROUND_RESULT".equalsIgnoreCase(type)) {
                        RoundResult result = gson.fromJson(trimmed, RoundResult.class);
                        handleRoundResult(result);
                    } else if ("GAME_END".equalsIgnoreCase(type) || "GAME_OVER".equalsIgnoreCase(type)) {
                        RoundResult finalResult = gson.fromJson(trimmed, RoundResult.class);
                        handleGameEnd(finalResult);
                        showMatchResult(trimmed);
                    }
                } else if (trimmed.startsWith("INFO|")) {
                    showTemporaryFeedback(trimmed.substring(5), 3, "");
                } else if (trimmed.startsWith("ERROR|")) {
                    showTemporaryFeedback("Lỗi: " + trimmed.substring(6), 5, "");
                } else if (trimmed.equals("FORFEIT_ACK")) {
                    showTemporaryFeedback("Đã thoát trận đấu", 3, "");
                } else {
                    showTemporaryFeedback(trimmed, 2, "");
                }
            } catch (Exception e) {
                System.err.println("Error handling message: " + e.getMessage());
            }
        });
    }

    private void handleNewRoundWithCountdown(NewRound round) {
        if (round == null) return;

        // If this is round 1 and initial countdown already shown, start immediately
        if (round.round == 1 && initialCountdownShown) {
            beginRound(round);
            return;
        }

        int countdownSec = (round.round == 1) ? 5 : 3;
        if (countdownOverlay != null && countdownLabel != null && countdownMessageLabel != null) {
            Platform.runLater(() -> {
                countdownMessageLabel.setText("Bắt đầu vòng " + round.round + " trong...");
                startRoundCountdown(round, countdownSec);
            });
        } else {
            beginRound(round);
        }
    }

    private void startRoundCountdown(NewRound round, int seconds) {
        if (seconds <= 0) seconds = 3;
        countdownOverlay.setVisible(true);
        countdownOverlay.setManaged(true);
        countdownOverlay.toFront();
        disableButtonsSafely();

        countdownLabel.setText(String.valueOf(seconds));
        final int[] left = {seconds};

        if (countdownTimer != null) {
            countdownTimer.stop();
            countdownTimer = null;
        }
        countdownTimer = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
            left[0]--;
            if (left[0] > 0) {
                countdownLabel.setText(String.valueOf(left[0]));
            } else {
                countdownLabel.setText("BẮT ĐẦU!");
                countdownMessageLabel.setText("Chúc may mắn!");
                PauseTransition hide = new PauseTransition(Duration.seconds(1));
                hide.setOnFinished(e2 -> {
                    stopCountdown();
                    beginRound(round);
                });
                hide.play();
            }
        }));
        countdownTimer.setCycleCount(seconds);
        countdownTimer.play();
    }

    private void beginRound(NewRound round) {
        if (round == null) return;

        currentRound = round.round;
        currentLevel = round.difficulty;
        targetNumber = round.target;
        hasSubmitted = false;
        if (expressionBuilder != null) expressionBuilder.setLength(0);

        timeRemaining = round.time;
        enableButtonsSafely();
        startTimer(round.time);
        updateDisplay();

        System.out.println("Round " + round.round + " started locally (target=" + round.target + ", time=" + round.time + "s)");
    }

    // ---------------- Input handling (numbers / operators / brackets / backspace / clear / submit) ----------------

    @FXML
    private void handleNumberInput(javafx.event.ActionEvent event) {
        if (hasSubmitted || hasExited || timeRemaining <= 0) return;
        try {
            Object src = event.getSource();
            if (!(src instanceof Button)) return;
            Button btn = (Button) src;
            String txt = btn.getText();
            if (txt == null || txt.isEmpty()) return;
            expressionBuilder.append(txt);
            updateDisplay();
        } catch (Exception ex) {
            System.err.println("handleNumberInput error: " + ex.getMessage());
        }
    }

    @FXML
    private void handleOperatorInput(javafx.event.ActionEvent event) {
        if (hasSubmitted || hasExited || timeRemaining <= 0) return;
        try {
            Object src = event.getSource();
            if (!(src instanceof Button)) return;
            Button btn = (Button) src;
            String op = btn.getText();
            if (op == null || op.isEmpty()) return;

            String current = expressionBuilder.toString();
            if (current.isEmpty()) return;
            char last = current.charAt(current.length() - 1);
            if (last == '(' || isOperator(last)) return;

            if (op.equals("×")) op = "*";
            if (op.equals("÷")) op = "/";

            expressionBuilder.append(op);
            updateDisplay();
        } catch (Exception ex) {
            System.err.println("handleOperatorInput error: " + ex.getMessage());
        }
    }

    @FXML
    private void handleBracketInput(javafx.event.ActionEvent event) {
        if (hasSubmitted || hasExited || timeRemaining <= 0) return;
        try {
            Object src = event.getSource();
            if (!(src instanceof Button)) return;
            Button btn = (Button) src;
            String b = btn.getText();
            if (b == null || b.isEmpty()) return;

            if (b.equals("(")) {
                expressionBuilder.append('(');
                updateDisplay();
                return;
            } else if (b.equals(")")) {
                String cur = expressionBuilder.toString();
                long open = cur.chars().filter(ch -> ch == '(').count();
                long close = cur.chars().filter(ch -> ch == ')').count();
                if (open > close) {
                    if (!cur.isEmpty()) {
                        char last = cur.charAt(cur.length() - 1);
                        if (!isOperator(last) && last != '(') {
                            expressionBuilder.append(')');
                            updateDisplay();
                        }
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("handleBracketInput error: " + ex.getMessage());
        }
    }

    @FXML
    private void handleBackspace() {
        if (hasSubmitted || hasExited || timeRemaining <= 0) return;
        try {
            if (expressionBuilder.length() > 0) {
                expressionBuilder.deleteCharAt(expressionBuilder.length() - 1);
                updateDisplay();
            }
        } catch (Exception ex) {
            System.err.println("handleBackspace error: " + ex.getMessage());
        }
    }

    @FXML
    private void handleClear() {
        if (hasSubmitted || hasExited || timeRemaining <= 0) return;
        try {
            expressionBuilder.setLength(0);
            updateDisplay();
        } catch (Exception ex) {
            System.err.println("handleClear error: " + ex.getMessage());
        }
    }

    @FXML
    private void handleSubmit() {
        if (hasSubmitted || hasExited || timeRemaining <= 0) return;
        String expression = expressionBuilder.toString().trim();
        if (expression.isEmpty()) {
            showTemporaryFeedback("Nhập biểu thức trước", 2, "");
            return;
        }

        String normalized = expression.replace('×', '*').replace('÷', '/');

        try {
            double value = new ExpressionEvaluator().evaluate(normalized);
            showTemporaryFeedback("Biểu thức = " + value + ", gửi...", 1, "");
        } catch (Exception ex) {
            showTemporaryFeedback("Biểu thức không hợp lệ", 2, "");
            return;
        }

        try {
            if (gameClient != null && gameClient.isConnected()) {
                try {
                    gameClient.sendSubmitAnswer(normalized);
                } catch (Throwable ignored) {
                    gameClient.sendRaw("ANSWER " + normalized);
                }
                hasSubmitted = true;
                if (submitButton != null) submitButton.setDisable(true);
            } else {
                showTemporaryFeedback("Không có kết nối server", 2, "");
            }
        } catch (Exception ex) {
            System.err.println("handleSubmit send error: " + ex.getMessage());
            showTemporaryFeedback("Gửi thất bại", 2, "");
        }
    }

    @FXML
    private void handleExit(javafx.event.ActionEvent event) {
        // guard to avoid double processing
        if (hasExited) return;
        hasExited = true;

        // stop local timers and overlays
        stopTimer();
        stopCountdown();
        stopRoundTransition();

        // tell server we forfeit / exit the match
        if (gameClient != null && gameClient.isConnected()) {
            try {
                // prefer a formal API if available
                try {
                    gameClient.sendRaw("FORFEIT");
                } catch (UnsupportedOperationException ignored) {
                    // fallback if sendRaw is not present for custom wrappers
                }
            } catch (Exception ex) {
                System.err.println("Failed to send FORFEIT: " + ex.getMessage());
            }
        }

        // update UI
        showTemporaryFeedback("Đang thoát trận đấu...", 2, "");
        disableButtonsSafely();

        // optionally close the window containing this controller:
        try {
            javafx.scene.Node src = (javafx.scene.Node) event.getSource();
            if (src != null && src.getScene() != null && src.getScene().getWindow() instanceof Stage) {
                Stage st = (Stage) src.getScene().getWindow();
                // don't close if it's the main app stage you want to keep — adjust as needed
                // st.close();
            }
        } catch (Exception ignored) {}
    }

    private boolean isOperator(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/' || c == '×' || c == '÷';
    }

    // ---------------- Round timer ----------------

    private void startTimer(int seconds) {
        stopTimer();
        if (seconds <= 0) seconds = 30;
        timeRemaining = seconds;
        updateDisplay();

        timer = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
            timeRemaining--;
            updateDisplay();
            if (timeRemaining <= 0) {
                stopTimer();
                handleTimeOut();
                disableButtonsSafely();
            }
        }));
        timer.setCycleCount(seconds);
        timer.play();
    }

    private void stopTimer() {
        if (timer != null) {
            try { timer.stop(); } catch (Exception ignored) {}
            timer = null;
        }
    }

    private void handleTimeOut() {
        hasSubmitted = true;
        if (gameStatusLabel != null) {
            gameStatusLabel.setText("⏰ HẾT GIỜ!");
            gameStatusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        }

        PauseTransition resetStyle = new PauseTransition(Duration.seconds(3));
        resetStyle.setOnFinished(e -> {
            if (gameStatusLabel != null) {
                gameStatusLabel.setStyle("-fx-text-fill: black; -fx-font-weight: normal;");
            }
        });
        resetStyle.play();

        if (gameClient != null && gameClient.isConnected()) {
            try {
                gameClient.sendRaw("TIMEOUT_NOTIFICATION Round:" + currentRound);
            } catch (Exception ignored) {}
        }
        System.out.println("Time out for round " + currentRound);
    }

    // ---------------- Result handlers (stubs - integrate existing logic) ----------------

    private void handleAnswerResult(AnswerResult result) {
        // Integrate your existing logic here (update scores, show messages)
        if (result == null) return;
        if (result.accepted) {
            if (result.correct) {
                showTemporaryFeedback("Đúng rồi! +" + result.score_gained + " điểm", 2, "");
                playerScore += result.score_gained;
            } else {
                showTemporaryFeedback("Sai! Kết quả: " + result.result, 2, "");
            }
        } else {
            showTemporaryFeedback("Đáp án không được chấp nhận", 2, "");
        }
        updateDisplay();
    }

    private void handleRoundResult(RoundResult result) {
        // update local state from round result
        if (result == null) return;
        stopTimer();
        if (result.players != null) {
            for (RoundResult.PlayerResult pr : result.players) {
                if (pr.username != null && pr.username.equalsIgnoreCase(playerUsername)) {
                    playerScore = pr.total_score;
                } else {
                    opponentScore = pr.total_score;
                }
            }
        }
        String msg = "Vòng " + result.round_number + " kết thúc";
        showTemporaryFeedback(msg, 2, "Chờ vòng tiếp theo...");
        updateDisplay();
    }

    private void handleGameEnd(RoundResult finalResult) {
        stopTimer();
        stopCountdown();
        stopRoundTransition();
        String message = "Game kết thúc!";
        showTemporaryFeedback(message, 4, "");
        updateDisplay();
    }

    private void showMatchResult(String rawJson) {
        // Load match_result.fxml and show modal (existing logic)
        Platform.runLater(() -> {
            try {
                URL fxmlUrl = getClass().getResource("/fxml/pages/matchresult.fxml");
                if (fxmlUrl == null) fxmlUrl = getClass().getResource("/fxml/match_result.fxml");
                if (fxmlUrl == null) {
                    System.err.println("match_result.fxml not found");
                    return;
                }
                FXMLLoader loader = new FXMLLoader(fxmlUrl);
                Parent root = loader.load();
                Object ctrl = loader.getController();
                if (ctrl instanceof com.mathspeed.controller.MatchResultController) {
                    ((com.mathspeed.controller.MatchResultController) ctrl).populateFromJson(rawJson);
                }
                Stage stage = new Stage();
                stage.initModality(Modality.APPLICATION_MODAL);
                stage.setTitle("Kết quả trận đấu");
                stage.setScene(new Scene(root));
                stage.setResizable(false);
                stage.show();
            } catch (IOException ioe) {
                System.err.println("Failed to show match result: " + ioe.getMessage());
            }
        });
    }

    // ---------------- Utilities ----------------

    private void stopRoundTransition() {
        if (roundTransitionTimer != null) {
            try { roundTransitionTimer.stop(); } catch (Exception ignored) {}
            roundTransitionTimer = null;
        }
        if (roundTransitionOverlay != null) roundTransitionOverlay.setVisible(false);
    }

    private void startRoundAfterTransition(NewRound round) { beginRound(round); }

    private void enableButtonsSafely() {
        if (!hasSubmitted && !hasExited && timeRemaining > 0) {
            if (submitButton != null) submitButton.setDisable(false);
            if (clearButton != null) clearButton.setDisable(false);
            if (backspaceButton != null) backspaceButton.setDisable(false);
        }
        if (exitButton != null && !hasExited) exitButton.setDisable(false);
    }

    private void disableButtonsSafely() {
        if (submitButton != null) submitButton.setDisable(true);
        if (clearButton != null) clearButton.setDisable(true);
        if (backspaceButton != null) backspaceButton.setDisable(true);
        if (exitButton != null) exitButton.setDisable(true);
    }

    private void updateDisplay() {
        if (playerScoreLabel != null) playerScoreLabel.setText(String.valueOf(playerScore));
        if (opponentScoreLabel != null) opponentScoreLabel.setText(String.valueOf(opponentScore));
        if (targetLabel != null) targetLabel.setText(String.valueOf(targetNumber));
        if (expressionLabel != null) expressionLabel.setText(expressionBuilder.toString());
        if (roundLabel != null) roundLabel.setText(String.valueOf(currentRound));
        if (levelLabel != null) levelLabel.setText(String.valueOf(currentLevel));
        if (playerNameLabel != null) playerNameLabel.setText(playerDisplayName);

        if (timerLabel != null) {
            if (timeRemaining <= 0) {
                timerLabel.setText("HẾT GIỜ!");
                timerLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            } else {
                timerLabel.setText(timeRemaining + "s");
                if (timeRemaining <= 5) {
                    timerLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold; -fx-font-size: 14px;");
                } else if (timeRemaining <= 10) {
                    timerLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
                } else {
                    timerLabel.setStyle("-fx-text-fill: black; -fx-font-weight: normal;");
                }
            }
        }
    }

    public void setPlayerUsername(String username) { this.playerUsername = username; }
    public void setGameClient(GameplayClient client) { this.gameClient = client; }

    public void cleanup() {
        stopTimer();
        stopCountdown();
        stopRoundTransition();
        try {
            if (periodicResyncFuture != null) periodicResyncFuture.cancel(true);
        } catch (Exception ignored) {}
        try { syncExecutor.shutdownNow(); } catch (Exception ignored) {}
        try { schedulingExecutor.shutdownNow(); } catch (Exception ignored) {}
        if (gameClient != null) {
            try { gameClient.disconnect(); } catch (Exception ignored) {}
        }
    }

    // TimePong and ServerNow helper classes
    private static class TimePong {
        @SerializedName("type") public String type;
        @SerializedName("client_send") public Long client_send;
        @SerializedName("server_time") public Long server_time;
    }
    private static class ServerNow {
        @SerializedName("type") public String type;
        @SerializedName("server_time") public Long server_time;
    }

    public void showFeedback(String message) {
        Platform.runLater(() -> {
            if (gameStatusLabel != null) {
                gameStatusLabel.setText(message);
            }
        });
        System.out.println("Feedback: " + message);
    }



    /**
     * Show a temporary message on the UI for `seconds` then restore defaultText.
     * Public so other classes (tests, wrappers) can call it.
     */
    public void showTemporaryFeedback(String message, int seconds, String defaultText) {
        Platform.runLater(() -> {
            if (gameStatusLabel != null) {
                gameStatusLabel.setText(message);
            }
            // schedule restore after `seconds`
            if (seconds > 0) {
                PauseTransition pause = new PauseTransition(Duration.seconds(seconds));
                pause.setOnFinished(e -> {
                    if (gameStatusLabel != null) {
                        gameStatusLabel.setText(defaultText != null ? defaultText : "");
                    }
                });
                pause.play();
            }
        });
        System.out.println("Temporary feedback: " + message + " (for " + seconds + "s)");
    }
}