package com.mathspeed.controller;

import com.mathspeed.model.*;
import com.mathspeed.network.NetworkGameplay;
import com.mathspeed.util.ExpressionEvaluator;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GameplayController (updated)
 *
 * - Binds overlay size to scene so overlay phủ lên toàn màn hình.
 * - Uses server timestamps (server_round_start / server_round_end) and offsetMs
 *   to compute exact remaining seconds so client doesn't end a round earlier than server.
 * - Starts periodic time sync when GameplayClient injected.
 */
public class GameplayController {

    @FXML private Label playerNameLabel;
    @FXML private Label opponentNameLabel;
    @FXML private ImageView playerAvatarImage;
    @FXML private ImageView opponentAvatarImage;
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

    @FXML private StackPane countdownOverlay;
    @FXML private VBox countdownContainer;
    @FXML private Label countdownLabel;
    @FXML private Label countdownMessageLabel;

    @FXML private StackPane roundTransitionOverlay;
    @FXML private VBox roundTransitionContainer;
    @FXML private Label roundTransitionTitle;
    @FXML private Label roundTransitionTarget;
    @FXML private Label roundTransitionTime;
    @FXML private Label roundTransitionPlayerScore;
    @FXML private Label roundTransitionOpponentScore;
    @FXML private Label roundTransitionMessage;

    private String playerDisplayName = "Bạn";
    private String opponentDisplayName = "Đối thủ";
    private StringBuilder expressionBuilder;
    private volatile boolean hasSubmitted = false;
    private volatile boolean hasExited = false;
    private int targetNumber;
    private int playerScore;
    private int opponentScore;
    private int timeRemaining;
    private int currentRound;
    private int currentLevel;
    private NetworkGameplay gameClient;
    private Timeline timer;
    private Timeline countdownTimer;
    private PauseTransition preRoundStartPause;
    private Timeline roundTransitionTimer;
    private Gson gson;
    private String playerUsername; // must be set externally to identify local player

    private volatile boolean initialCountdownShown = false;

    // Time sync
    private final AtomicLong offsetMs = new AtomicLong(0L);
    private final AtomicLong estimatedRttMs = new AtomicLong(150L);
    private final ConcurrentMap<Long, CompletableFuture<Long>> pendingPingReplies = new ConcurrentHashMap<>();
    private final ScheduledExecutorService syncExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "TimeSync");
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

        // Ensure overlay covers whole scene by binding to scene when available
        if (countdownOverlay != null) {
            countdownOverlay.setVisible(false);
            countdownOverlay.setManaged(false);
            countdownOverlay.sceneProperty().addListener((obs, oldS, newS) -> {
                if (newS != null) {
                    countdownOverlay.prefWidthProperty().bind(newS.widthProperty());
                    countdownOverlay.prefHeightProperty().bind(newS.heightProperty());
                    // ensure container centered
                    if (countdownContainer != null) StackPane.setAlignment(countdownContainer, javafx.geometry.Pos.CENTER);
                } else {
                    try {
                        countdownOverlay.prefWidthProperty().unbind();
                        countdownOverlay.prefHeightProperty().unbind();
                    } catch (Exception ignored) {}
                }
            });
        }

        if (roundTransitionOverlay != null) {
            roundTransitionOverlay.setVisible(false);
            roundTransitionOverlay.setManaged(false);
            roundTransitionOverlay.sceneProperty().addListener((obs, oldS, newS) -> {
                if (newS != null) {
                    roundTransitionOverlay.prefWidthProperty().bind(newS.widthProperty());
                    roundTransitionOverlay.prefHeightProperty().bind(newS.heightProperty());
                    if (roundTransitionContainer != null) StackPane.setAlignment(roundTransitionContainer, javafx.geometry.Pos.CENTER);
                } else {
                    try {
                        roundTransitionOverlay.prefWidthProperty().unbind();
                        roundTransitionOverlay.prefHeightProperty().unbind();
                    } catch (Exception ignored) {}
                }
            });
        }

        System.out.println("GameplayController.initialize instance=" + System.identityHashCode(this) + " playerUsername=" + playerUsername);
        updatePlayerNames();
        updateDisplay();

        try {
            String placeholder = getClass().getResource("/images/avatar-default.png").toExternalForm();
            setPlayerAvatarUrl(placeholder);
            setOpponentAvatarUrl(placeholder);
        } catch (Exception ex) {
            System.err.println("Không tìm thấy placeholder avatar resource: " + ex.getMessage());
        }
    }

    public void setPlayerAvatarUrl(String urlOrResourcePath) {
        if (urlOrResourcePath == null) return;
        Platform.runLater(() -> {
            try {
                Image img = new Image(urlOrResourcePath, 56, 56, true, true, true);
                playerAvatarImage.setImage(img);
                applyCircleClip(playerAvatarImage, 28);
            } catch (Exception ex) {
                System.err.println("setPlayerAvatarUrl error: " + ex.getMessage());
            }
        });
    }

    public void setOpponentAvatarUrl(String urlOrResourcePath) {
        if (urlOrResourcePath == null) return;
        Platform.runLater(() -> {
            try {
                javafx.scene.image.Image img = new javafx.scene.image.Image(urlOrResourcePath, 56, 56, true, true, true);
                opponentAvatarImage.setImage(img);
                applyCircleClip(opponentAvatarImage, 28);
            } catch (Exception ex) {
                System.err.println("setOpponentAvatarUrl error: " + ex.getMessage());
            }
        });
    }

    // utility để tạo clip tròn (center = fitWidth/2)
    private void applyCircleClip(javafx.scene.image.ImageView iv, double radius) {
        if (iv == null) return;
        javafx.scene.shape.Circle clip = new javafx.scene.shape.Circle(radius, radius, radius);
        iv.setClip(clip);
        // optional: add a subtle border by snapshotting clip and placing on container if needed
    }

    /**
     * Inject GameplayClient and register typed handlers.
     */
    public void setGameClient(NetworkGameplay client) {
        this.gameClient = client;
        if (client == null) return;

        // register typed handlers
        client.setMatchStartHandler(info -> Platform.runLater(() -> handleMatchStart(info)));

        client.setNewRoundHandler(nr -> Platform.runLater(() -> {
            // prefer using server timestamps if present to compute exact start/end
            handleNewRoundWithCountdown(nr);
        }));

        client.setAnswerResultHandler(ar -> Platform.runLater(() -> handleAnswerResult(ar)));

        client.setRoundResultHandler(rr -> Platform.runLater(() -> handleRoundResult(rr)));

        client.setGameEndHandler(rr -> Platform.runLater(() -> {
            handleGameEnd(rr);
            showMatchResult(gson.toJson(rr));
        }));

        client.setTimePongHandler(tp -> {
            if (tp != null && tp.client_send != null && tp.server_time != null) {
                CompletableFuture<Long> f = pendingPingReplies.remove(tp.client_send);
                if (f != null) f.complete(tp.server_time);
            }
        });

        client.setServerNowHandler(sn -> {
            if (sn != null && sn.server_time != null) {
                long clientNow = System.currentTimeMillis();
                long sampleOffset = sn.server_time - clientNow;
                offsetMs.set((long) Math.round(0.6 * sampleOffset + 0.4 * offsetMs.get()));
                baseSystemMs = System.currentTimeMillis();
                baseNano = System.nanoTime();
            }
        });

        client.setPlayerListUpdateHandler(raw -> {
            System.out.println("Player list update: " + raw);
        });

        System.out.println("setGameClient called on controller=" + System.identityHashCode(this) + " client=" + client);
        // perform an immediate small time sync to get good offset before rounds start
        startPeriodicTimeSync();
    }

    // ---------------- Time sync helpers ----------------

    private long currentServerTimeMs() {
        return System.currentTimeMillis() + offsetMs.get();
    }

    public void startPeriodicTimeSync() {
        // run an immediate sync on background thread then schedule periodic
        syncExecutor.execute(() -> {
            try { performTimeSync(4, 700); } catch (Exception ex) { System.err.println("Initial time sync failed: " + ex.getMessage()); }
        });

        if (periodicResyncFuture != null && !periodicResyncFuture.isCancelled()) {
            periodicResyncFuture.cancel(true);
        }
        periodicResyncFuture = syncExecutor.scheduleAtFixedRate(() -> {
            try { performTimeSync(2, 700); } catch (Exception ignored) {}
        }, 20, 20, TimeUnit.SECONDS);
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
                    gameClient.sendTimePing(t0);
                } else {
                    pendingPingReplies.remove(t0);
                    break;
                }
            } catch (Exception ex) {
                pendingPingReplies.remove(t0);
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
            } catch (TimeoutException | ExecutionException te) {
                pendingPingReplies.remove(t0);
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
            System.out.println("Time sync done: offset=" + offsetMs.get() + " rtt=" + estimatedRttMs.get());
            Platform.runLater(() -> System.out.println("Đồng bộ thời gian hoàn tất"));
        }
    }

    // ---------------- Match start & round flow ----------------

    private void handleMatchStart(MatchStartInfo info) {
        if (info == null) return;

        // reset state
        playerScore = 0;
        opponentScore = 0;
        currentRound = 0;
        currentLevel = 1;
        hasSubmitted = false;
        hasExited = false;
        if (expressionBuilder != null) expressionBuilder.setLength(0);
        updateDisplay();

        // determine opponent display name
        String opponent = "Đối thủ";
        if (info.players != null) {
            for (Player p : info.players) {
                if (p == null) continue;
                String u = p.getUsername() != null ? p.getUsername() : "";
                String d = p.getDisplayName() != null ? p.getDisplayName() : u;
                if (playerUsername != null && playerUsername.equalsIgnoreCase(u)) {
                    // local
                } else {
                    opponent = d;
                }
            }
        }
        opponentDisplayName = opponent;
        playerDisplayName = "Bạn";

        // compute countdown based on server start time if provided
        int countdownSec = 5;
        if (info.countdown_ms > 0) countdownSec = (int) Math.ceil(info.countdown_ms / 1000.0);

        // If server provided a precise start_time/server_time, compute time until start
        long nowServer = currentServerTimeMs();
        if (info.start_time > 0) {
            long msUntilStart = info.start_time - nowServer;
            if (msUntilStart > 0) {
                countdownSec = (int) Math.max(1, Math.ceil(msUntilStart / 1000.0));
            } else {
                countdownSec = 0;
            }
        } else if (info.server_time > 0 && info.countdown_ms > 0) {
            long approxStart = info.server_time + info.countdown_ms;
            long msUntilStart = approxStart - nowServer;
            if (msUntilStart > 0) countdownSec = (int) Math.max(1, Math.ceil(msUntilStart / 1000.0));
            else countdownSec = 0;
        }

        final int cs = countdownSec;
        Platform.runLater(() -> {
            updatePlayerNames();
            if (cs > 0) {
                if (countdownLabel != null) countdownLabel.setText(String.valueOf(cs));
                if (countdownMessageLabel != null) countdownMessageLabel.setText("Trận đấu sắp bắt đầu!");
                startMatchCountdown(cs);
            } else {
                // start immediately: server likely already started rounds; wait for NEW_ROUND
                showTemporaryFeedback("Trận đấu bắt đầu!", 1, "");
            }
        });

        initialCountdownShown = true;
    }

    private void handleNewRoundWithCountdown(NewRound round) {
        if (round == null) return;

        System.out.println("handleNewRound/Result called on controller=" + System.identityHashCode(this) + " playerUsername=" + playerUsername);

        // cancel any existing scheduled pre-round pause
        try {
            if (preRoundStartPause != null) {
                preRoundStartPause.stop();
                preRoundStartPause = null;
            }
        } catch (Exception ignored) {}

        long nowServer = currentServerTimeMs();

        long serverRoundStart = round.getServer_round_start(); // 0 if not provided
        long serverRoundEnd = round.getServer_round_end();     // 0 if not provided

        // debug log
        System.out.println("NEW_ROUND: round=" + round.getRound() + " target=" + round.getTarget()
                + " serverRoundStart=" + serverRoundStart + " serverRoundEnd=" + serverRoundEnd
                + " nowServer=" + nowServer);

        // CASE A: serverRoundStart in the future -> show overlay until that exact server time
        if (serverRoundStart > nowServer) {
            long msUntilStart = serverRoundStart - nowServer;
            int secsToShow = (int) Math.max(1, Math.ceil(msUntilStart / 1000.0));

            // start visual countdown (per-second) but schedule precise start at msUntilStart
            Platform.runLater(() -> {
                if (countdownLabel != null) countdownLabel.setText(String.valueOf(secsToShow));
                if (countdownMessageLabel != null) countdownMessageLabel.setText("Bắt đầu vòng " + round.getRound() + " trong...");
                // show a per-second overlay (this will be stopped by preRoundStartPause when time arrives)
                startRoundCountdown(round, secsToShow);
            });

            // schedule the exact beginRound at msUntilStart using PauseTransition on FX thread
            preRoundStartPause = new PauseTransition(Duration.millis(msUntilStart));
            preRoundStartPause.setOnFinished(ev -> {
                // ensure overlay stopped and then start round using server_round_end to compute rem time
                stopCountdown();
                // compute remaining seconds from server_round_end if present
                if (serverRoundEnd > 0) {
                    long remMs = serverRoundEnd - currentServerTimeMs();
                    round.setTime((int) Math.max(0, Math.ceil(remMs / 1000.0)));
                }
                beginRound(round);
                preRoundStartPause = null;
            });
            preRoundStartPause.play();
            return;
        }

        // CASE B: serverRoundStart is not in future. If serverRoundEnd is in future -> start immediately
        if (serverRoundEnd > nowServer) {
            long remMs = serverRoundEnd - nowServer;
            int remSec = (int) Math.max(0, Math.ceil(remMs / 1000.0));
            round.setTime(remSec);
            Platform.runLater(() -> beginRound(round));
            return;
        }

        // CASE C: no server timestamps or already expired - fallback to previous behavior
        int preCountdown = (round.getRound() == 1 && initialCountdownShown) ? 0 : ((round.getRound() == 1) ? 5 : 3);

        System.out.println("handleNewRound: computed preCountdown=" + preCountdown + " for round=" + round.getRound()
                + " initialCountdownShown=" + initialCountdownShown);
        if (preCountdown <= 0) {
            Platform.runLater(() -> beginRound(round));
        } else {
            Platform.runLater(() -> {
                if (countdownLabel != null) countdownLabel.setText(String.valueOf(preCountdown));
                if (countdownMessageLabel != null) countdownMessageLabel.setText("Bắt đầu vòng " + round.getRound() + " trong...");
                startRoundCountdown(round, preCountdown);
            });
        }
    }

    private void startMatchCountdown(int seconds) {
        if (seconds <= 0) {
            enableButtonsSafely();
            return;
        }
        showCountdownOverlay(seconds, "Trận đấu sắp bắt đầu!", () -> {
            enableButtonsSafely();
            showTemporaryFeedback("Trận đấu bắt đầu!", 1, "");
        });
    }

    private void startRoundCountdown(NewRound round, int seconds) {
        System.out.println("startRoundCountdown called: round=" + (round!=null?round.getRound():"null")
                + " seconds=" + seconds + " controller=" + System.identityHashCode(this));


        if (seconds <= 0) {
            beginRound(round);
            return;
        }
        showCountdownOverlay(seconds, "Bắt đầu vòng " + round.getRound() + " trong...", () -> beginRound(round));
    }

    private void showCountdownOverlay(int seconds, String message, Runnable onFinished) {
        // debug
        System.out.println("showCountdownOverlay called: seconds=" + seconds + " message=" + message
                + " overlayNull=" + (countdownOverlay == null)
                + " containerNull=" + (countdownContainer == null)
                + " labelNull=" + (countdownLabel == null)
                + " msgLabelNull=" + (countdownMessageLabel == null));

        if (countdownOverlay == null || countdownLabel == null || countdownMessageLabel == null) {
            System.out.println("Countdown UI nodes missing -> fallback PauseTransition");
            PauseTransition fallback = new PauseTransition(Duration.seconds(Math.max(0, seconds)));
            fallback.setOnFinished(e -> { if (onFinished != null) onFinished.run(); });
            fallback.play();
            return;
        }

        // attach binding now if scene already present, otherwise install listener to bind when scene set
        if (countdownOverlay.getScene() != null) {
            try {
                countdownOverlay.prefWidthProperty().bind(countdownOverlay.getScene().widthProperty());
                countdownOverlay.prefHeightProperty().bind(countdownOverlay.getScene().heightProperty());
            } catch (Exception ex) { System.out.println("bind immediate failed: " + ex.getMessage()); }
        } else {
            // ensure binding when scene becomes available
            countdownOverlay.sceneProperty().addListener((obs, oldS, newS) -> {
                if (newS != null) {
                    try {
                        countdownOverlay.prefWidthProperty().bind(newS.widthProperty());
                        countdownOverlay.prefHeightProperty().bind(newS.heightProperty());
                    } catch (Exception ignored) {}
                }
            });
        }

        // print sizes for diagnosis
        Platform.runLater(() -> {
            double ovW = countdownOverlay.getWidth();
            double ovH = countdownOverlay.getHeight();
            double scW = countdownOverlay.getScene() != null ? countdownOverlay.getScene().getWidth() : -1;
            double scH = countdownOverlay.getScene() != null ? countdownOverlay.getScene().getHeight() : -1;
            double contW = countdownContainer != null ? countdownContainer.getWidth() : -1;
            double contH = countdownContainer != null ? countdownContainer.getHeight() : -1;
            System.out.println("Overlay sizes: overlay(" + ovW + "x" + ovH + ") scene(" + scW + "x" + scH + ") container(" + contW + "x" + contH + ")");
        });

        // Make overlay definitely visible and on top. Also add a temporary background so it's unmistakable.
        countdownOverlay.setManaged(true);
        countdownOverlay.setVisible(true);
        countdownOverlay.toFront();
        countdownOverlay.setMouseTransparent(false);
        // Temporary visual override to ensure we can see it despite CSS; remove after debugging if desired
        countdownOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.45);");

        // Force label styles for visibility
        try {
            countdownLabel.setStyle("-fx-font-size: 48px; -fx-text-fill: white; -fx-font-weight: bold;");
            countdownMessageLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px;");
        } catch (Exception ignored) {}

        countdownMessageLabel.setText(message);
        countdownLabel.setText(String.valueOf(Math.max(0, seconds)));
        disableButtonsSafely();

        final int[] left = { Math.max(0, seconds) };
        if (countdownTimer != null) {
            try { countdownTimer.stop(); } catch (Exception ignored) {}
            countdownTimer = null;
        }

        if (left[0] <= 0) {
            PauseTransition shortPause = new PauseTransition(Duration.seconds(0.2));
            shortPause.setOnFinished(e -> {
                stopCountdown();
                if (onFinished != null) onFinished.run();
            });
            shortPause.play();
            return;
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
                    // remove temporary debug background/style so final UI returns to CSS styling
                    try { countdownOverlay.setStyle(null); } catch (Exception ignored) {}
                    try { countdownLabel.setStyle(null); countdownMessageLabel.setStyle(null); } catch (Exception ignored) {}
                    if (onFinished != null) onFinished.run();
                });
                hide.play();
            }
        }));
        countdownTimer.setCycleCount(Timeline.INDEFINITE);
        countdownTimer.play();
    }

    private void stopCountdown() {
        if (countdownTimer != null) {
            try { countdownTimer.stop(); } catch (Exception ignored) {}
            countdownTimer = null;
        }
        if (countdownOverlay != null) {
            countdownOverlay.setVisible(false);
            countdownOverlay.setManaged(false);
        }
    }

    private void beginRound(NewRound round) {
        if (round == null) return;

        currentRound = round.getRound();
        currentLevel = round.getDifficulty();
        targetNumber = round.getTarget();
        hasSubmitted = false;
        if (expressionBuilder != null) expressionBuilder.setLength(0);

        // If server provided a server_round_end, compute remaining time using currentServerTimeMs
        int seconds = round.getTime();
        long nowServer = currentServerTimeMs();
        if (round.getServer_round_end() != 0) {
            long remMs = round.getServer_round_end() - nowServer;
            if (remMs < 0) remMs = 0;
            seconds = (int) Math.max(0, Math.ceil(remMs / 1000.0));
        }

        timeRemaining = Math.max(0, seconds);
        enableButtonsSafely();
        startTimer(timeRemaining);
        updateDisplay();

        System.out.println("Round " + round.getRound() + " started locally (target=" + round.getTarget() + ", time=" + timeRemaining + "s)");
    }

    // ---------------- Input handlers ----------------

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
                } catch (Exception e) {
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
        if (hasExited) return;
        hasExited = true;
        stopTimer();
        stopCountdown();
        stopRoundTransition();
        if (gameClient != null && gameClient.isConnected()) {
            try { gameClient.sendForfeit(); } catch (Exception ignored) {}
        }
        showTemporaryFeedback("Đang thoát trận đấu...", 2, "");
        disableButtonsSafely();
    }

    private boolean isOperator(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/' || c == '×' || c == '÷';
    }

    // ---------------- Timer ----------------

    private void startTimer(int seconds) {
        stopTimer();
        if (seconds <= 0) seconds = 0;
        timeRemaining = seconds;
        updateDisplay();

        if (seconds <= 0) {
            handleTimeOut();
            return;
        }

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
            gameStatusLabel.setText("HẾT GIỜ!");
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
            try { gameClient.sendRaw("TIMEOUT_NOTIFICATION Round:" + currentRound); } catch (Exception ignored) {}
        }
        System.out.println("Time out for round " + currentRound);
    }

    // ---------------- Results ----------------

    private void handleAnswerResult(AnswerResult result) {
        if (result == null) return;
        if (result.accepted) {
            if (result.correct) {
                showTemporaryFeedback("Đúng rồi! +" + result.score_gained + " điểm", 2, "");
                // server should indicate which player; we assume local if accepted
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
        if (result == null) return;
        stopTimer();

        try {
            if (result.players != null) {
                for (RoundResult.PlayerResult pr : result.players) {
                    if (pr.username != null && pr.username.equalsIgnoreCase(playerUsername)) {
                        playerScore = pr.total_score;
                    } else {
                        opponentScore = pr.total_score;
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("handleRoundResult mapping error: " + ex.getMessage());
        }

        showTemporaryFeedback("Vòng " + result.round_number + " kết thúc", 2, "Chờ vòng tiếp theo...");
        updateDisplay();
    }

    private void handleGameEnd(RoundResult finalResult) {
        stopTimer();
        stopCountdown();
        stopRoundTransition();
        showTemporaryFeedback("Game kết thúc!", 4, "");
        updateDisplay();
        // showMatchResult called by caller that passed the final JSON
    }

    private void showMatchResult(String rawJson) {
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

    private void updatePlayerNames() {
        if (playerNameLabel != null) playerNameLabel.setText(playerDisplayName);
        if (opponentNameLabel != null) opponentNameLabel.setText(opponentDisplayName);
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
            if (timeRemaining < 1) {
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

    public void setPlayerUsername(String username) {

        System.out.println("setPlayerUsername called: " + username + " on controller=" + System.identityHashCode(this));
        this.playerUsername = username;
    }

    public void cleanup() {
        stopTimer();
        stopCountdown();
        stopRoundTransition();
        try {
            if (periodicResyncFuture != null) periodicResyncFuture.cancel(true);
        } catch (Exception ignored) {}
        try { syncExecutor.shutdownNow(); } catch (Exception ignored) {}
        if (gameClient != null) {
            try { gameClient.disconnect(); } catch (Exception ignored) {}
        }
    }

    private static class TimePong {
        @SerializedName("type") public String type;
        @SerializedName("client_send") public Long client_send;
        @SerializedName("server_time") public Long server_time;
    }
    private static class ServerNow {
        @SerializedName("type") public String type;
        @SerializedName("server_time") public Long server_time;
    }

    public void showTemporaryFeedback(String message, int seconds, String defaultText) {
        Platform.runLater(() -> {
            if (gameStatusLabel != null) gameStatusLabel.setText(message);
            if (seconds > 0) {
                PauseTransition pause = new PauseTransition(Duration.seconds(seconds));
                pause.setOnFinished(e -> {
                    if (gameStatusLabel != null) gameStatusLabel.setText(defaultText != null ? defaultText : "");
                });
                pause.play();
            }
        });
        System.out.println("Temporary feedback: " + message + " (for " + seconds + "s)");
    }
}