package com.mathspeed.application.game;

import com.mathspeed.adapter.network.ClientHandler;
import com.mathspeed.adapter.network.protocol.MessageType;
import com.mathspeed.domain.port.GameRepository;
import com.mathspeed.domain.puzzle.MathExpressionEvaluator;
import com.mathspeed.domain.puzzle.MathPuzzleFormat;
import com.mathspeed.domain.puzzle.MathPuzzleGenerator;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class GameSession {

    private final String sessionId;
    private final ClientHandler playerA;
    private final ClientHandler playerB;
    private final int totalRounds;
    private final long questionTimeoutSeconds;
    private final MathPuzzleGenerator generator;
    private final ScheduledExecutorService scheduler;

    // State that is only mutated on scheduler thread:
    private final AtomicInteger currentRound = new AtomicInteger(0);
    private final Map<ClientHandler, Integer> scores = new HashMap<>();
    private final Map<ClientHandler, Duration> totalPlayTime = new HashMap<>();
    private final List<Integer> difficultySequence;

    // Per-round history for each player (kept on scheduler thread)
    private final Map<ClientHandler, List<RoundResult>> roundHistory = new HashMap<>();

    // keep revealed targets for replay / reconnect
    private final List<Integer> revealedTargets = new ArrayList<>();

    // pre-generated puzzles buffer to avoid generation latency between rounds
    private final List<MathPuzzleFormat> preGeneratedPuzzles = new ArrayList<>();
    private final int bufferAhead = 2; // number of rounds to pre-generate ahead (tuneable)

    // Futures scheduled on scheduler thread
    private ScheduledFuture<?> roundTimeoutFuture;
    private ScheduledFuture<?> startFuture;
    private ScheduledFuture<?> activationFuture;

    private MathPuzzleFormat currentPuzzle;
    private Instant roundStart;
    private boolean roundActive = false;
    private int activeRoundIndex = -1; // index of the currently active round

    private final boolean persistResults;
    private final GameRepository gameDAO;

    // matchSeed is now long (higher entropy)
    private final long matchSeed;
    private long matchStartTimeMs = -1L;
    private final AtomicBoolean finished = new AtomicBoolean(false);

    private final Map<ClientHandler, Boolean> readyMap = new HashMap<>();
    private final int initialCountdownMs = 5000; // 5s default
    private final int fastStartBufferMs = 500;

    // inter-round timing config (ms)
    private final long minInterRoundGapMs = 100;    // minimum gap to allow clients render
    private final long defaultInterRoundGapMs = 300; // default gap if no RTT info

    // uniqueness attempts when generating puzzles to avoid duplicate targets per match
    private final int MAX_UNIQUE_ATTEMPTS = 10;

    public GameSession(ClientHandler playerA,
                       ClientHandler playerB,
                       int totalRounds,
                       long questionTimeoutSeconds,
                       GameRepository gameDAO) {
        this.sessionId = UUID.randomUUID().toString();
        this.playerA = Objects.requireNonNull(playerA);
        this.playerB = Objects.requireNonNull(playerB);
        this.totalRounds = Math.max(1, Math.min(totalRounds, 20));
        this.questionTimeoutSeconds = questionTimeoutSeconds;
        this.generator = new MathPuzzleGenerator(1);
        this.persistResults = true;
        this.gameDAO = gameDAO;

        this.matchSeed = new SecureRandom().nextLong();

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GameSession-" + sessionId);
            t.setDaemon(true);
            return t;
        });

        scores.put(playerA, 0);
        scores.put(playerB, 0);
        totalPlayTime.put(playerA, Duration.ZERO);
        totalPlayTime.put(playerB, Duration.ZERO);

        roundHistory.put(playerA, new ArrayList<>());
        roundHistory.put(playerB, new ArrayList<>());

        try { playerA.setCurrentGame(this); } catch (Exception ignored) {}
        try { playerB.setCurrentGame(this); } catch (Exception ignored) {}

        readyMap.put(playerA, false);
        readyMap.put(playerB, false);

        this.difficultySequence = generateDifficultyList(this.totalRounds, this.matchSeed);
    }

    private List<Integer> generateDifficultyList(int rounds, long seed) {
        int capped = Math.max(1, Math.min(rounds, 20));
        // base percentages (tune as needed)
        double easyPct = 0.45;
        double mediumPct = 0.40;
        double hardPct = 1.0 - easyPct - mediumPct;

        int easyCount = (int)Math.round(capped * easyPct);
        int mediumCount = (int)Math.round(capped * mediumPct);
        int hardCount = capped - easyCount - mediumCount;

        // fix rounding issues
        if (hardCount < 0) {
            hardCount = 0;
            int sum = easyCount + mediumCount;
            if (sum > capped) {
                if (easyCount >= mediumCount) easyCount -= (sum - capped);
                else mediumCount -= (sum - capped);
            }
        }

        // ensure at least 1 per level when rounds >= 3
        if (capped >= 3) {
            if (easyCount == 0) easyCount = 1;
            if (mediumCount == 0) mediumCount = 1;
            if (hardCount == 0) hardCount = 1;
            // adjust to match total
            while (easyCount + mediumCount + hardCount > capped) {
                if (easyCount >= mediumCount && easyCount >= hardCount && easyCount > 1) easyCount--;
                else if (mediumCount >= easyCount && mediumCount >= hardCount && mediumCount > 1) mediumCount--;
                else if (hardCount > 1) hardCount--;
                else break;
            }
            while (easyCount + mediumCount + hardCount < capped) {
                mediumCount++;
            }
        } else {
            // small rounds: ensure sum == capped
            while (easyCount + mediumCount + hardCount != capped) {
                int sum = easyCount + mediumCount + hardCount;
                if (sum < capped) mediumCount++;
                else {
                    if (easyCount >= mediumCount && easyCount >= hardCount && easyCount > 0) easyCount--;
                    else if (mediumCount >= easyCount && mediumCount >= hardCount && mediumCount > 0) mediumCount--;
                    else if (hardCount > 0) hardCount--;
                }
            }
        }

        List<Integer> list = new ArrayList<>(capped);
        repeatAdd(list, 1, easyCount);
        repeatAdd(list, 2, mediumCount);
        repeatAdd(list, 3, hardCount);

        // Shuffle removed to keep low->high ordering (easy then medium then hard).
        // If you prefer a smooth ramp instead of blocky blocks, replace with a distribution algorithm.

        return Collections.unmodifiableList(list);
    }

    private void repeatAdd(List<Integer> list, int value, int times) {
        for (int i = 0; i < times; i++) list.add(value);
    }

    public String getSessionId() { return sessionId; }
    public ClientHandler getPlayerA() { return playerA; }
    public ClientHandler getPlayerB() { return playerB; }
    public long getMatchSeed() { return matchSeed; }

    public void beginGame() {
        this.matchStartTimeMs = System.currentTimeMillis() + initialCountdownMs;

        // Pre-generate puzzles so transitions are instant (with uniqueness enforcement)
        preGenerateAllPuzzles();

        // Broadcast initial info immediately
        broadcastMatchInfo();

        // Schedule countdown messages on scheduler (they will execute on scheduler thread)
        for (int i = 5; i >= 1; i--) {
            final int sec = i;
            scheduler.schedule(() -> {
                String msg = "Bắt đầu sau " + sec + " giây...";
                safeSendInfo(playerA, msg);
                safeSendInfo(playerB, msg);
            }, (5 - i), TimeUnit.SECONDS);
        }

        long delay = Math.max(0, matchStartTimeMs - System.currentTimeMillis());
        startFuture = scheduler.schedule(this::runStartMatch, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Pre-generate all puzzles for the match, attempting to avoid duplicate targets within the match.
     * Deterministic based on matchSeed and roundIndex and attempt counter.
     */
    private void preGenerateAllPuzzles() {
        preGeneratedPuzzles.clear();
        Set<Integer> usedTargets = new HashSet<>();
        for (int roundIndex = 0; roundIndex < totalRounds; roundIndex++) {
            MathPuzzleFormat p = null;
            int attempt = 0;
            while (attempt < MAX_UNIQUE_ATTEMPTS) {
                long roundSeed = deriveRoundSeed(matchSeed, roundIndex, attempt);
                Random puzzleRnd = new Random(roundSeed);
                p = generator.generatePuzzle(difficultySequence.get(roundIndex), puzzleRnd);
                if (!usedTargets.contains(p.getTarget())) break;
                attempt++;
            }
            if (p == null) {
                // fallback: generate without RNG (shouldn't happen)
                long roundSeed = deriveRoundSeed(matchSeed, roundIndex, 0);
                p = generator.generatePuzzle(difficultySequence.get(roundIndex), new Random(roundSeed));
            }
            if (usedTargets.contains(p.getTarget())) {
                // couldn't avoid duplicate after attempts; log a warning but accept it
                System.out.printf("WARN: duplicate target for round %d target=%d%n", roundIndex, p.getTarget());
            }
            usedTargets.add(p.getTarget());
            preGeneratedPuzzles.add(p);
        }
    }

    private void ensureBufferedPuzzles(int currentIndex) {
        int needUpTo = Math.min(totalRounds - 1, currentIndex + bufferAhead);
        Set<Integer> usedTargets = new HashSet<>();
        // collect used from existing preGeneratedPuzzles
        for (MathPuzzleFormat mp : preGeneratedPuzzles) usedTargets.add(mp.getTarget());

        for (int i = preGeneratedPuzzles.size(); i <= needUpTo; i++) {
            int roundIndex = i;
            MathPuzzleFormat p = null;
            int attempt = 0;
            while (attempt < MAX_UNIQUE_ATTEMPTS) {
                long roundSeed = deriveRoundSeed(matchSeed, roundIndex, attempt);
                Random puzzleRnd = new Random(roundSeed);
                p = generator.generatePuzzle(difficultySequence.get(roundIndex), puzzleRnd);
                if (!usedTargets.contains(p.getTarget())) break;
                attempt++;
            }
            if (p == null) {
                long roundSeed = deriveRoundSeed(matchSeed, roundIndex, 0);
                p = generator.generatePuzzle(difficultySequence.get(roundIndex), new Random(roundSeed));
            }
            usedTargets.add(p.getTarget());
            preGeneratedPuzzles.add(p);
        }
    }

    private MathPuzzleFormat getPuzzleForRound(int roundIndex) {
        if (roundIndex < preGeneratedPuzzles.size()) {
            return preGeneratedPuzzles.get(roundIndex);
        } else {
            int attempt = 0;
            MathPuzzleFormat p = null;
            Set<Integer> used = new HashSet<>();
            for (MathPuzzleFormat mp : preGeneratedPuzzles) used.add(mp.getTarget());
            while (attempt < MAX_UNIQUE_ATTEMPTS) {
                long roundSeed = deriveRoundSeed(matchSeed, roundIndex, attempt);
                Random puzzleRnd = new Random(roundSeed);
                p = generator.generatePuzzle(difficultySequence.get(roundIndex), puzzleRnd);
                if (!used.contains(p.getTarget())) break;
                attempt++;
            }
            if (p == null) {
                long roundSeed = deriveRoundSeed(matchSeed, roundIndex, 0);
                p = generator.generatePuzzle(difficultySequence.get(roundIndex), new Random(roundSeed));
            }
            return p;
        }
    }

    private void broadcastMatchInfo() {
        long now = System.currentTimeMillis();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", MessageType.MATCH_START_INFO.name());
        msg.put("seed", matchSeed);
        msg.put("start_time", matchStartTimeMs);
        msg.put("server_time", now); // <-- added field for client clock sync
        msg.put("countdown_ms", Math.max(0, matchStartTimeMs - now));
        msg.put("question_count", totalRounds);
        msg.put("per_question_seconds", questionTimeoutSeconds);

        String json = JsonUtil.toJson(msg);
        safeSendMessage(playerA, json);
        safeSendMessage(playerB, json);
    }

    private void runStartMatch() {
        // runs on scheduler thread
        safeSendInfo(playerA, "Trận đấu bắt đầu!");
        safeSendInfo(playerB, "Trận đấu bắt đầu!");
        runStartNextRound();
    }

    private void runStartNextRound() {
        // runs on scheduler thread
        if (finished.get()) return;

        int roundIndex = currentRound.getAndIncrement();
        if (roundIndex >= totalRounds) {
            finishGameInternal();
            return;
        }

        activeRoundIndex = roundIndex;

        // Ensure puzzles for upcoming rounds are ready (buffered)
        ensureBufferedPuzzles(roundIndex);

        // Use pre-generated puzzle (fast, no heavy computation at transition time)
        currentPuzzle = getPuzzleForRound(roundIndex);
        int difficulty = difficultySequence.get(roundIndex);
        long roundSeed = deriveRoundSeed(matchSeed, roundIndex); // canonical seed (attempt 0)

        // compute an inter-round gap to let clients render the new puzzle before accepting answers
        long interGap = computeInterRoundGapMs();
        long serverRoundStartMs = System.currentTimeMillis() + interGap;

        try {
            System.out.printf("DEBUG preparing_round: session=%s round=%d roundSeed=%d target=%d startAt=%d%n",
                    sessionId, roundIndex, roundSeed, currentPuzzle.getTarget(), serverRoundStartMs);
        } catch (Exception ignored) {}

        // store revealed target for reconnect/replay (we reveal it to clients now)
        revealedTargets.add(currentPuzzle.getTarget());

        // send NEW_ROUND immediately with server_round_start (clients will render but cannot answer until that time)
        Duration placeholderTime = Duration.ofSeconds(questionTimeoutSeconds);
        sendPuzzleToPlayers(currentPuzzle, difficulty, placeholderTime, roundIndex + 1, roundIndex, roundSeed, Instant.ofEpochMilli(serverRoundStartMs));

        // Cancel any pending activation/timeout
        if (activationFuture != null && !activationFuture.isDone()) activationFuture.cancel(false);
        if (roundTimeoutFuture != null && !roundTimeoutFuture.isDone()) roundTimeoutFuture.cancel(false);

        // Initially mark round as not active; activation will set roundActive=true at serverRoundStartMs
        roundActive = false;
        roundStart = null;

        // Schedule activation task to start accepting answers at the precise serverRoundStartMs
        long delayToActivate = Math.max(0L, serverRoundStartMs - System.currentTimeMillis());
        activationFuture = scheduler.schedule(() -> {
            // This runs on scheduler thread at activation time
            roundStart = Instant.ofEpochMilli(serverRoundStartMs);
            roundActive = true;
            // Start the actual question timeout from activation moment
            roundTimeoutFuture = scheduler.schedule(this::onRoundTimeout, questionTimeoutSeconds * 1000L, TimeUnit.MILLISECONDS);
            // Optionally notify players that round is now active (if UI needs cue)
            safeSendInfo(playerA, "Bắt đầu vòng " + (roundIndex + 1));
            safeSendInfo(playerB, "Bắt đầu vòng " + (roundIndex + 1));
        }, delayToActivate, TimeUnit.MILLISECONDS);
    }

    private long computeInterRoundGapMs() {
        try {
            long rttA = (playerA != null) ? playerA.getEstimatedRttMs() : 0L;
            long rttB = (playerB != null) ? playerB.getEstimatedRttMs() : 0L;
            long maxRtt = Math.max(0L, Math.max(rttA, rttB));
            long computed = 2L * maxRtt + 50L; // conservative
            computed = Math.min(1000L, computed);
            return Math.max(minInterRoundGapMs, Math.max(defaultInterRoundGapMs, computed));
        } catch (Exception ex) {
            return Math.max(minInterRoundGapMs, defaultInterRoundGapMs);
        }
    }

    public void submitAnswer(ClientHandler player, String expression) {
        if (finished.get()) {
            sendSimpleAnswerResult(player, false, "match_finished");
            return;
        }
        final Instant serverRecv = Instant.now();
        scheduler.execute(() -> processAnswer(player, expression, serverRecv));
    }

    private void processAnswer(ClientHandler player, String expression, Instant serverRecv) {
        // runs on scheduler thread
        if (currentPuzzle == null) {
            sendSimpleAnswerResult(player, false, "no_active_round");
            return;
        }
        // If round not active yet or serverRecv before roundStart -> too early
        if (roundStart == null || serverRecv.isBefore(roundStart)) {
            sendSimpleAnswerResult(player, false, "too_early");
            return;
        }
        if (!roundActive) {
            sendSimpleAnswerResult(player, false, "no_active_round");
            return;
        }

        int result;
        try {
            List<Integer> deck = new ArrayList<>();
            for(int a = 1; a < 10; a++) deck.add(a);
            result = MathExpressionEvaluator.evaluate(expression, deck);
        } catch (IllegalArgumentException evalEx) {
            Map<String, Object> err = new HashMap<>();
            err.put("type", MessageType.ANSWER_RESULT.name());
            err.put("accepted", false);
            err.put("reason", "invalid_expression");
            err.put("message", evalEx.getMessage());
            safeSendMessage(player, JsonUtil.toJson(err));
            System.err.println("Invalid expression from " + player.getUsername() + ": \"" + expression + "\" -> " + evalEx.getMessage());
            return;
        } catch (Exception ex) {
            Map<String, Object> err = new HashMap<>();
            err.put("type", MessageType.ANSWER_RESULT.name());
            err.put("accepted", false);
            err.put("reason", "internal_error");
            err.put("message", ex.getClass().getSimpleName());
            safeSendMessage(player, JsonUtil.toJson(err));
            ex.printStackTrace();
            return;
        }

        boolean correct = result == currentPuzzle.getTarget();

        Map<String, Object> resMsg = new HashMap<>();
        resMsg.put("type", MessageType.ANSWER_RESULT.name());
        resMsg.put("correct", correct);
        resMsg.put("accepted", true);
        resMsg.put("server_time", serverRecv.toEpochMilli());
        safeSendMessage(player, JsonUtil.toJson(resMsg));

        if (!correct) return;

        // If already closed by another correct answer or timeout, ignore
        if (!roundActive) return;
        roundActive = false;

        // Compute play time based on server timestamps and clamp to [0, questionTimeout]
        long playMs = Duration.between(roundStart, serverRecv).toMillis();
        long maxMs = questionTimeoutSeconds * 1000L;
        if (playMs < 0) playMs = 0;
        if (playMs > maxMs) playMs = maxMs;
        Duration playTime = Duration.ofMillis(playMs);

        totalPlayTime.put(player, totalPlayTime.getOrDefault(player, Duration.ZERO).plus(playTime));
        scores.put(player, scores.getOrDefault(player, 0) + 1);

        // record round result for both players (use serverRecv epoch as timestamp)
        recordRoundResultsOnCorrect(player, activeRoundIndex, playTime);

        // cancel timeout and start next round immediately
        if (roundTimeoutFuture != null && !roundTimeoutFuture.isDone()) {
            roundTimeoutFuture.cancel(false);
        }
        if (activationFuture != null && !activationFuture.isDone()) {
            activationFuture.cancel(false); // should not normally be pending
        }

        // Broadcast summary for this round (scores and times)
        broadcastRoundSummary(activeRoundIndex);

        // start next round immediately on scheduler thread
        runStartNextRound();
    }

    private void recordRoundResultsOnCorrect(ClientHandler winner, int roundIndex, Duration winnerPlayTime) {
        // winner: correct=true and playTime
        // loser: correct=false, playTime=0
        ClientHandler loser = (winner == playerA) ? playerB : playerA;

        RoundResult rWinner = new RoundResult(roundIndex, true, winnerPlayTime.toMillis(), Instant.now().toEpochMilli());
        roundHistory.get(winner).add(rWinner);

        RoundResult rLoser = new RoundResult(roundIndex, false, 0L, Instant.now().toEpochMilli());
        roundHistory.get(loser).add(rLoser);
    }

    private void onRoundTimeout() {
        // runs on scheduler thread when a round times out
        if (!roundActive) {
            // Already closed by correct answer
            return;
        }
        roundActive = false;

        // record both players as incorrect with playTime=0
        RoundResult rA = new RoundResult(activeRoundIndex, false, 0L, Instant.now().toEpochMilli());
        RoundResult rB = new RoundResult(activeRoundIndex, false, 0L, Instant.now().toEpochMilli());
        roundHistory.get(playerA).add(rA);
        roundHistory.get(playerB).add(rB);

        // Broadcast summary for this round (scores and times)
        broadcastRoundSummary(activeRoundIndex);

        // start next round
        runStartNextRound();
    }

    public void handleReady(ClientHandler from) {
        if (from == null) return;
        // perform ready handling on scheduler thread
        scheduler.execute(() -> {
            readyMap.put(from, true);
            ClientHandler other = (from == playerA) ? playerB : playerA;
            if (other != null) safeSendInfo(other, "Đối thủ đã sẵn sàng");

            boolean aReady = Boolean.TRUE.equals(readyMap.get(playerA));
            boolean bReady = Boolean.TRUE.equals(readyMap.get(playerB));
            if (aReady && bReady && startFuture != null && !startFuture.isDone()) {
                long now = System.currentTimeMillis();
                long potentialStart = now + fastStartBufferMs;
                if (potentialStart + 50 < matchStartTimeMs) {
                    startFuture.cancel(false);
                    matchStartTimeMs = potentialStart;
                    long delay = Math.max(0, matchStartTimeMs - System.currentTimeMillis());
                    startFuture = scheduler.schedule(this::runStartMatch, delay, TimeUnit.MILLISECONDS);
                    broadcastMatchInfo();
                }
            }
        });
    }

    public void handleRequestMatchInfo(ClientHandler from) {
        scheduler.execute(this::broadcastMatchInfo);
    }

    /**
     * Handle a user-initiated forfeit (still connected).
     */
    public void handleForfeit(ClientHandler who) {
        if (who == null) return;
        scheduler.execute(() -> {
            if (finished.get()) return;
            ClientHandler other = (who == playerA) ? playerB : playerA;

            // Inform both players appropriately
            safeSendInfo(who, "Bạn đã hủy trận. Bạn thua.");
            if (other != null) safeSendInfo(other, "Đối thủ đã hủy, bạn thắng (forfeit).");

            // Apply forfeit rules (scores, round history, finish)
            applyForfeitScoringAndFinish(who);
        });
    }

    /**
     * Handle unexpected disconnect - treat as a forfeit with slightly different messaging.
     */
    public void handlePlayerDisconnect(ClientHandler disconnected) {
        if (disconnected == null) return;
        scheduler.execute(() -> {
            if (finished.get()) return;
            ClientHandler other = (disconnected == playerA) ? playerB : playerA;

            if (other != null) safeSendInfo(other, "Đối thủ đã ngắt kết nối. Bạn thắng (forfeit).");

            applyForfeitScoringAndFinish(disconnected);
        });
    }

    /**
     * Centralized forfeit handling executed on the scheduler thread.
     *
     * Rules implemented:
     * - forfeiter's score = 0
     * - opponent's score = totalRounds (i.e. awarded 1 point per round)
     * - totalPlayTime for both players kept as-is (equal to time they have already played)
     * - fill any missing roundHistory entries: opponent correct=true, forfeiter correct=false, playTime=0
     * - finish the game
     */
    private void applyForfeitScoringAndFinish(ClientHandler forfeiter) {
        if (forfeiter == null) return;
        if (finished.get()) return;

        ClientHandler winner = (forfeiter == playerA) ? playerB : playerA;
        long nowTs = Instant.now().toEpochMilli();

        // Set scores: forfeiter 0, winner full points for the match
        scores.put(forfeiter, 0);
        scores.put(winner, totalRounds);

        // Ensure roundHistory lists exist
        List<RoundResult> histForfeiter = roundHistory.getOrDefault(forfeiter, new ArrayList<>());
        List<RoundResult> histWinner = roundHistory.getOrDefault(winner, new ArrayList<>());
        roundHistory.putIfAbsent(forfeiter, histForfeiter);
        roundHistory.putIfAbsent(winner, histWinner);

        // Build set of recorded round indices for each player for quick lookup
        Set<Integer> recForfeiter = new HashSet<>();
        for (RoundResult r : roundHistory.get(forfeiter)) recForfeiter.add(r.roundIndex);
        Set<Integer> recWinner = new HashSet<>();
        for (RoundResult r : roundHistory.get(winner)) recWinner.add(r.roundIndex);

        // For each round index not yet recorded, add entries: winner correct=true (playTime 0), forfeiter correct=false (playTime 0)
        for (int r = 0; r < totalRounds; r++) {
            boolean fHas = recForfeiter.contains(r);
            boolean wHas = recWinner.contains(r);

            if (fHas && wHas) continue; // both already have result for this round

            if (!wHas) {
                RoundResult rw = new RoundResult(r, true, 0L, nowTs);
                roundHistory.get(winner).add(rw);
            }
            if (!fHas) {
                RoundResult rf = new RoundResult(r, false, 0L, nowTs);
                roundHistory.get(forfeiter).add(rf);
            }
        }

        // Persist results / notify and finish
        finishGameInternal();
    }

    private void finishGameInternal() {
        if (!finished.compareAndSet(false, true)) return;

        ClientHandler winner = null;
        int scoreA = scores.getOrDefault(playerA, 0);
        int scoreB = scores.getOrDefault(playerB, 0);

        if (scoreA > scoreB) winner = playerA;
        else if (scoreB > scoreA) winner = playerB;
        else {
            Duration timeA = totalPlayTime.getOrDefault(playerA, Duration.ZERO);
            Duration timeB = totalPlayTime.getOrDefault(playerB, Duration.ZERO);
            if (timeA.compareTo(timeB) < 0) winner = playerA;
            else if (timeB.compareTo(timeA) < 0) winner = playerB;
        }

        Map<String, Object> msg = new HashMap<>();
        msg.put("type", MessageType.GAME_OVER.name());
        msg.put("scores", exportScores());
        msg.put("total_play_time_ms", exportPlayTime());
        msg.put("winner", winner != null ? safeGetPlayerId(winner) : null);
        msg.put("round_history", exportRoundHistory()); // per-player round-by-round history
        msg.put("revealed_targets", revealedTargets);

        String json = JsonUtil.toJson(msg);
        safeSendMessage(playerA, json);
        safeSendMessage(playerB, json);

        // Persist results: attempt detailed DB write; fall back to legacy persistGameFinal if needed.
        persistResultsToDatabase(json);

        try { playerA.clearCurrentGame(); } catch (Exception ignored) {}
        try { playerB.clearCurrentGame(); } catch (Exception ignored) {}

        try { if (activationFuture != null && !activationFuture.isDone()) activationFuture.cancel(false); } catch (Exception ignored) {}
        try { if (roundTimeoutFuture != null && !roundTimeoutFuture.isDone()) roundTimeoutFuture.cancel(false); } catch (Exception ignored) {}
        try { if (startFuture != null && !startFuture.isDone()) startFuture.cancel(false); } catch (Exception ignored) {}
        try { scheduler.shutdownNow(); } catch (Exception ignored) {}
    }

    /**
     * Try to persist detailed match data to DB.
     *
     * Strategy:
     * - If gameDAO exposes a getConnection() method (reflectively) we will use the returned Connection
     *   and write rows to games, game_rounds, round_results, game_players, game_events and game_finals.
     * - If not, fall back to calling gameDAO.persistGameFinal(sessionId, scores, playTime, roundHistory, winnerId)
     *   if such method exists.
     *
     * This is defensive: we don't want persistence failures to prevent game shutdown,
     * so exceptions are caught and logged.
     */
    private void persistResultsToDatabase(String gameOverJson) {
        if (!persistResults || gameDAO == null) return;

        // Build useful data structures
        Map<String, Integer> scoresMap = exportScores();
        Map<String, Long> playTimeMap = exportPlayTime();
        Map<String, List<Map<String, Object>>> roundHist = exportRoundHistory();
        String winnerId = null;
        try {
            // determine winner id
            int scoreA = scores.getOrDefault(playerA, 0);
            int scoreB = scores.getOrDefault(playerB, 0);
            if (scoreA > scoreB) winnerId = safeGetPlayerId(playerA);
            else if (scoreB > scoreA) winnerId = safeGetPlayerId(playerB);
        } catch (Exception ignored) {}

        // 1) Try to get Connection from GameDAO via reflection: public Connection getConnection()
        try {
            Method getConn = gameDAO.getClass().getMethod("getConnection");
            Object connObj = getConn.invoke(gameDAO);
            if (connObj instanceof Connection) {
                Connection conn = (Connection) connObj;
                persistViaConnection(conn, scoresMap, playTimeMap, roundHist, winnerId, gameOverJson);
                return;
            }
        } catch (NoSuchMethodException nsme) {
            // fall through: method not present
        } catch (Exception ex) {
            System.err.println("[GameSession] Failed to obtain Connection from GameDAO: " + ex.getMessage());
            ex.printStackTrace();
        }

        // 2) Fallback: call legacy persistGameFinal if present
        try {
            Method pgf = gameDAO.getClass().getMethod("persistGameFinal", String.class, Map.class, Map.class, Map.class, String.class);
            pgf.invoke(gameDAO, sessionId, scoresMap, playTimeMap, roundHist, winnerId);
            return;
        } catch (NoSuchMethodException nsme) {
            // no such method; fallthrough
        } catch (Exception ex) {
            System.err.println("[GameSession] Legacy persistGameFinal failed: " + ex.getMessage());
            ex.printStackTrace();
        }

        // 3) If we get here, no persistence performed; log JSON for debugging
        System.out.println("[GameSession] No DB persistence method available on GameDAO. GameOver JSON:");
        System.out.println(gameOverJson);
    }

    /**
     * Persist using an existing JDBC Connection. This method will attempt to insert rows into:
     * - games
     * - game_players (for both players)
     * - game_rounds (one per round)
     * - round_results (per player per round)
     * - game_events (GAME_OVER)
     * - game_finals (snapshot)
     *
     * All operations performed in a transaction; errors will roll back and be logged.
     */
    private void persistViaConnection(Connection conn,
                                      Map<String, Integer> scoresMap,
                                      Map<String, Long> playTimeMap,
                                      Map<String, List<Map<String, Object>>> roundHist,
                                      String winnerId,
                                      String gameOverJson) {
        if (conn == null) return;
        boolean previousAuto = true;
        try {
            previousAuto = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // 1) Insert into games (if not exists) - create or update to finished
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO games (id, created_at, started_at, ended_at, total_rounds, status) " +
                            "VALUES (?, NOW(), ?, NOW(), ?, 'finished') " +
                            "ON DUPLICATE KEY UPDATE ended_at=NOW(), total_rounds=VALUES(total_rounds), status='finished'")) {
                ps.setString(1, sessionId);
                if (matchStartTimeMs > 0) ps.setTimestamp(2, new Timestamp(matchStartTimeMs));
                else ps.setTimestamp(2, null);
                ps.setInt(3, totalRounds);
                ps.executeUpdate();
            }

            // 2) Insert/Upsert game_players (two participants)
            // Attempt to use safeGetPlayerId (may return username if id missing). For FK integrity, make sure real player ids are present.
            String pAId = safeGetPlayerId(playerA);
            String pBId = safeGetPlayerId(playerB);

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO game_players (game_id, player_id, joined_at, left_at, final_score, total_time, result) " +
                            "VALUES (?, ?, NOW(), NOW(), ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE final_score=VALUES(final_score), total_time=VALUES(total_time), result=VALUES(result), left_at=VALUES(left_at)")) {
                // playerA
                ps.setString(1, sessionId);
                ps.setString(2, pAId);
                ps.setInt(3, scoresMap.getOrDefault(pAId, 0));
                ps.setLong(4, playTimeMap.getOrDefault(pAId, 0L));
                String resA = determineResultForPlayer(pAId, winnerId);
                ps.setString(5, resA);
                ps.executeUpdate();

                // playerB
                ps.setString(1, sessionId);
                ps.setString(2, pBId);
                ps.setInt(3, scoresMap.getOrDefault(pBId, 0));
                ps.setLong(4, playTimeMap.getOrDefault(pBId, 0L));
                String resB = determineResultForPlayer(pBId, winnerId);
                ps.setString(5, resB);
                ps.executeUpdate();
            }

            // 3) Insert game_rounds and round_results
            // For each round index, attempt to find preGeneratedPuzzles entry for target/seed.
            for (int ri = 0; ri < totalRounds; ri++) {
                String roundId = UUID.randomUUID().toString();
                MathPuzzleFormat puzzle = (ri < preGeneratedPuzzles.size()) ? preGeneratedPuzzles.get(ri) : null;
                Integer target = (puzzle != null) ? puzzle.getTarget() : null;
                Integer difficulty = (ri < difficultySequence.size()) ? difficultySequence.get(ri) : null;
                long roundSeed = deriveRoundSeed(matchSeed, ri);
                long serverRoundStartMs = 0L; // unknown for persisted rows unless tracked earlier; leave 0
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO game_rounds (id, game_id, round_index, round_number, difficulty, target, time_seconds, seed, round_seed, server_round_start_ms) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, roundId);
                    ps.setString(2, sessionId);
                    ps.setInt(3, ri);
                    ps.setInt(4, ri + 1);
                    if (difficulty != null) ps.setInt(5, difficulty); else ps.setNull(5, java.sql.Types.INTEGER);
                    if (target != null) ps.setInt(6, target); else ps.setNull(6, java.sql.Types.INTEGER);
                    ps.setInt(7, (int) questionTimeoutSeconds);
                    ps.setLong(8, matchSeed);
                    ps.setLong(9, roundSeed);
                    if (serverRoundStartMs > 0) ps.setLong(10, serverRoundStartMs); else ps.setNull(10, java.sql.Types.BIGINT);
                    ps.executeUpdate();
                }

                // Insert round results for both players based on roundHistory map
                // roundHist maps playerId -> list of round map entries {round_index, correct, round_play_time_ms, timestamp}
                // We'll attempt to find the entries for this round for each player
                for (ClientHandler ch : Arrays.asList(playerA, playerB)) {
                    String playerId = safeGetPlayerId(ch);
                    List<Map<String, Object>> rh = roundHist.getOrDefault(playerId, Collections.emptyList());
                    Map<String, Object> entryForRound = null;
                    for (Map<String, Object> me : rh) {
                        Object idxObj = me.get("round_index");
                        if (idxObj instanceof Number && ((Number) idxObj).intValue() == ri) {
                            entryForRound = me;
                            break;
                        }
                    }
                    boolean correct = false;
                    long playTimeMs = 0L;
                    long ts = System.currentTimeMillis();
                    if (entryForRound != null) {
                        Object corr = entryForRound.get("correct");
                        if (corr instanceof Boolean) correct = (Boolean) corr;
                        else if (corr instanceof String) correct = Boolean.parseBoolean((String) corr);
                        Object pt = entryForRound.get("round_play_time_ms");
                        if (pt instanceof Number) playTimeMs = ((Number) pt).longValue();
                        Object tstamp = entryForRound.get("timestamp");
                        if (tstamp instanceof Number) ts = ((Number) tstamp).longValue();
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO round_results (id, round_id, player_id, correct, play_time_ms, timestamp_ms) VALUES (?, ?, ?, ?, ?, ?)")) {
                        ps.setString(1, UUID.randomUUID().toString());
                        ps.setString(2, roundId);
                        ps.setString(3, playerId);
                        ps.setBoolean(4, correct);
                        ps.setLong(5, playTimeMs);
                        ps.setLong(6, ts);
                        ps.executeUpdate();
                    }
                }
            }

            // 4) Insert a final snapshot in game_finals
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO game_finals (id, game_id, payload) VALUES (?, ?, ?)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, sessionId);
                ps.setString(3, gameOverJson);
                ps.executeUpdate();
            }

            // 5) Insert GAME_OVER event into game_events
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO game_events (id, game_id, event_type, payload) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, sessionId);
                ps.setString(3, "GAME_OVER");
                ps.setString(4, gameOverJson);
                ps.executeUpdate();
            }

            conn.commit();
            System.out.println("[GameSession] Persisted match results to DB for game=" + sessionId);
        } catch (Exception ex) {
            try { conn.rollback(); } catch (Exception ignored) {}
            System.err.println("[GameSession] Failed to persist match results DB: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            try {
                conn.setAutoCommit(previousAuto);
            } catch (Exception ignored) {}
        }
    }

    private String determineResultForPlayer(String playerId, String winnerId) {
        if (winnerId == null) return "draw";
        if (winnerId.equals(playerId)) return "win";
        return "lose";
    }

    public void finishGame() {
        scheduler.execute(this::finishGameInternal);
    }

    private Map<String, Integer> exportScores() {
        Map<String, Integer> out = new HashMap<>();
        out.put(safeGetPlayerId(playerA), scores.getOrDefault(playerA, 0));
        out.put(safeGetPlayerId(playerB), scores.getOrDefault(playerB, 0));
        return out;
    }

    private Map<String, Long> exportPlayTime() {
        Map<String, Long> out = new HashMap<>();
        out.put(safeGetPlayerId(playerA), totalPlayTime.getOrDefault(playerA, Duration.ZERO).toMillis());
        out.put(safeGetPlayerId(playerB), totalPlayTime.getOrDefault(playerB, Duration.ZERO).toMillis());
        return out;
    }

    private Map<String, List<Map<String, Object>>> exportRoundHistory() {
        Map<String, List<Map<String, Object>>> out = new HashMap<>();
        out.put(safeGetPlayerId(playerA), exportRoundList(roundHistory.getOrDefault(playerA, Collections.emptyList())));
        out.put(safeGetPlayerId(playerB), exportRoundList(roundHistory.getOrDefault(playerB, Collections.emptyList())));
        return out;
    }

    private List<Map<String, Object>> exportRoundList(List<RoundResult> list) {
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (RoundResult r : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("round_index", r.roundIndex);
            m.put("correct", r.correct);
            m.put("round_play_time_ms", r.playTimeMillis);
            m.put("timestamp", r.timestampMs);
            out.add(m);
        }
        return out;
    }

    private void broadcastRoundSummary(int roundIndex) {
        String idA = safeGetPlayerId(playerA);
        String idB = safeGetPlayerId(playerB);

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", MessageType.ROUND_RESULT.name());
        msg.put("round_index", roundIndex);
        msg.put("round_number", roundIndex + 1);

        String roundWinner = null;
        List<RoundResult> histA = roundHistory.getOrDefault(playerA, Collections.emptyList());
        List<RoundResult> histB = roundHistory.getOrDefault(playerB, Collections.emptyList());
        RoundResult lastA = histA.isEmpty() ? null : histA.get(histA.size() - 1);
        RoundResult lastB = histB.isEmpty() ? null : histB.get(histB.size() - 1);
        if (lastA != null && lastA.roundIndex == roundIndex && lastA.correct) roundWinner = idA;
        else if (lastB != null && lastB.roundIndex == roundIndex && lastB.correct) roundWinner = idB;

        msg.put("round_winner", roundWinner);

        List<Map<String, Object>> players = new ArrayList<>(2);
        players.add(makePlayerRoundSummary(playerA, lastA));
        players.add(makePlayerRoundSummary(playerB, lastB));
        msg.put("players", players);

        msg.put("scores", exportScores());
        msg.put("total_play_time_ms", exportPlayTime());

        String json = JsonUtil.toJson(msg);
        safeSendMessage(playerA, json);
        safeSendMessage(playerB, json);
    }

    private Map<String, Object> makePlayerRoundSummary(ClientHandler p, RoundResult last) {
        Map<String, Object> m = new LinkedHashMap<>();
        String id = safeGetPlayerId(p);
        m.put("id", id);
        m.put("username", safeGetUsername(p));
        if (last != null) {
            m.put("correct", last.correct);
            m.put("round_play_time_ms", last.playTimeMillis);
        } else {
            m.put("correct", false);
            m.put("round_play_time_ms", 0L);
        }
        m.put("total_score", scores.getOrDefault(p, 0));
        m.put("total_play_time_ms", totalPlayTime.getOrDefault(p, Duration.ZERO).toMillis());
        return m;
    }

    private String safeGetPlayerId(ClientHandler p) {
        try {
            return p.getPlayer() != null ? String.valueOf(p.getPlayer().getId()) : p.getUsername();
        } catch (Exception e) {
            return p.getUsername();
        }
    }

    private String safeGetUsername(ClientHandler p) {
        try {
            return p.getUsername();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void sendSimpleAnswerResult(ClientHandler player, boolean accepted, String reason) {
        Map<String, Object> resMsg = new HashMap<>();
        resMsg.put("type", MessageType.ANSWER_RESULT.name());
        resMsg.put("accepted", accepted);
        resMsg.put("reason", reason);
        safeSendMessage(player, JsonUtil.toJson(resMsg));
    }

    private void safeSendInfo(ClientHandler p, String text) {
        try { if (p != null) p.sendType(MessageType.INFO, text); } catch (Exception ignored) {}
    }

    private void safeSendMessage(ClientHandler p, String json) {
        try { if (p != null) p.sendMessage(json); } catch (Exception ignored) {}
    }

    private static long deriveRoundSeed(long sessionSeed, int roundIndex) {
        return deriveRoundSeed(sessionSeed, roundIndex, 0);
    }

    /**
     * Deterministic per-round seed derivation with an attempt counter to allow re-seeding when
     * avoiding duplicate targets.
     */
    private static long deriveRoundSeed(long sessionSeed, int roundIndex, int attempt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(longToBytes(sessionSeed));
            md.update(intToBytes(roundIndex));
            md.update(intToBytes(attempt));
            byte[] digest = md.digest();
            return ByteBuffer.wrap(digest, 0, 8).getLong();
        } catch (Exception ex) {
            // fallback mixing
            long a = sessionSeed;
            long b = Integer.toUnsignedLong(roundIndex);
            long mixed = a * 0x9E3779B97F4A7C15L + ((b << 32) | b) + attempt;
            return mixed ^ (mixed >>> 32);
        }
    }

    private static byte[] longToBytes(long x) {
        ByteBuffer bb = ByteBuffer.allocate(Long.BYTES);
        bb.putLong(x);
        return bb.array();
    }

    private static byte[] intToBytes(int x) {
        ByteBuffer bb = ByteBuffer.allocate(Integer.BYTES);
        bb.putInt(x);
        return bb.array();
    }

    private void sendPuzzleToPlayers(MathPuzzleFormat puzzle, int difficulty, Duration roundTime,
                                     int roundNumber, int roundIndex, long roundSeed, Instant serverRoundStartInstant) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", MessageType.NEW_ROUND.name());
        msg.put("round", roundNumber);
        msg.put("difficulty", difficulty);
        msg.put("target", puzzle.getTarget());
        msg.put("time", roundTime.getSeconds());
        msg.put("seed", matchSeed);          // match seed (global)
        msg.put("round_seed", roundSeed);   // per-round seed for reproducibility
        msg.put("round_index", roundIndex);
        msg.put("server_round_start", serverRoundStartInstant.toEpochMilli());

        String json = JsonUtil.toJson(msg);
        safeSendMessage(playerA, json);
        safeSendMessage(playerB, json);
    }

    private static class JsonUtil {
        static String toJson(Object obj) {
            StringBuilder sb = new StringBuilder(256);
            serialize(obj, sb);
            return sb.toString();
        }

        private static void serialize(Object obj, StringBuilder sb) {
            if (obj == null) { sb.append("null"); return; }
            if (obj instanceof Number || obj instanceof Boolean) {
                sb.append(obj.toString());
            } else if (obj instanceof String) {
                sb.append('"').append(escape((String) obj)).append('"');
            } else if (obj instanceof Map) {
                sb.append('{');
                Map<?, ?> m = (Map<?, ?>) obj;
                Iterator<? extends Map.Entry<?,?>> it = m.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<?,?> e = it.next();
                    sb.append('"').append(escape(String.valueOf(e.getKey()))).append("\":");
                    serialize(e.getValue(), sb);
                    if (it.hasNext()) sb.append(',');
                }
                sb.append('}');
            } else if (obj instanceof Collection) {
                sb.append('[');
                Iterator<?> it = ((Collection<?>) obj).iterator();
                while (it.hasNext()) {
                    serialize(it.next(), sb);
                    if (it.hasNext()) sb.append(',');
                }
                sb.append(']');
            } else if (obj.getClass().isArray()) {
                sb.append('[');
                int len = java.lang.reflect.Array.getLength(obj);
                for (int i = 0; i < len; i++) {
                    serialize(java.lang.reflect.Array.get(obj, i), sb);
                    if (i + 1 < len) sb.append(',');
                }
                sb.append(']');
            } else {
                // Fallback to string representation
                sb.append('"').append(escape(String.valueOf(obj))).append('"');
            }
        }

        private static String escape(String s) {
            StringBuilder out = new StringBuilder(s.length() + 8);
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"': out.append("\\\""); break;
                    case '\\': out.append("\\\\"); break;
                    case '\b': out.append("\\b"); break;
                    case '\f': out.append("\\f"); break;
                    case '\n': out.append("\\n"); break;
                    case '\r': out.append("\\r"); break;
                    case '\t': out.append("\\t"); break;
                    default:
                        if (c < 0x20 || c > 0x7E) {
                            out.append(String.format("\\u%04x", (int) c));
                        } else out.append(c);
                }
            }
            return out.toString();
        }
    }

    private static class RoundResult {
        final int roundIndex;
        final boolean correct;
        final long playTimeMillis;
        final long timestampMs;

        RoundResult(int roundIndex, boolean correct, long playTimeMillis, long timestampMs) {
            this.roundIndex = roundIndex;
            this.correct = correct;
            this.playTimeMillis = playTimeMillis;
            this.timestampMs = timestampMs;
        }
    }
}