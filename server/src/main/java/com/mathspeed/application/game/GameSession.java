package com.mathspeed.application.game;

import com.mathspeed.adapter.network.ClientHandler;
import com.mathspeed.adapter.network.protocol.MessageType;
import com.mathspeed.domain.model.GameHistory;
import com.mathspeed.domain.model.GameHistoryId;
import com.mathspeed.domain.model.GameMatch;
import com.mathspeed.domain.model.Player;
import com.mathspeed.domain.port.GameRepository;
import com.mathspeed.domain.puzzle.MathExpressionEvaluator;
import com.mathspeed.domain.puzzle.MathPuzzleFormat;
import com.mathspeed.domain.puzzle.MathPuzzleGenerator;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    private final AtomicInteger currentRound = new AtomicInteger(0);
    private final Map<ClientHandler, Integer> scores = new HashMap<>();
    private final Map<ClientHandler, Duration> totalPlayTime = new HashMap<>();
    private final List<Integer> difficultySequence;

    private final Map<ClientHandler, List<RoundResult>> roundHistory = new HashMap<>();

    private final List<Integer> revealedTargets = new ArrayList<>();

    private final List<MathPuzzleFormat> preGeneratedPuzzles = new ArrayList<>();
    private final int bufferAhead = 2;

    private ScheduledFuture<?> roundTimeoutFuture;
    private ScheduledFuture<?> startFuture;
    private ScheduledFuture<?> activationFuture;

    private MathPuzzleFormat currentPuzzle;
    private Instant roundStart;
    private boolean roundActive = false;
    private int activeRoundIndex = -1;

    private final boolean persistResults;
    private final GameRepository gameDAO;

    // matchSeed is now long (higher entropy)
    private final long matchSeed;
    private long matchStartTimeMs = -1L; // accurate start time (set when match actually starts)
    private long matchEndTimeMs = -1L;   // accurate end time (set when match finishes)
    private final AtomicBoolean finished = new AtomicBoolean(false);

    private final Map<ClientHandler, Boolean> readyMap = new HashMap<>();
    private final int initialCountdownMs = 5000; // 5s default
    private final int fastStartBufferMs = 500;

    // inter-round timing config (ms)
    private final long minInterRoundGapMs = 100;
    private final long defaultInterRoundGapMs = 300;

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

        try {
            playerA.setCurrentGame(this);
        } catch (Exception ignored) {
        }
        try {
            playerB.setCurrentGame(this);
        } catch (Exception ignored) {
        }

        readyMap.put(playerA, false);
        readyMap.put(playerB, false);

        this.difficultySequence = generateDifficultyList(this.totalRounds, this.matchSeed);
    }

    private List<Integer> generateDifficultyList(int rounds, long seed) {
        int capped = Math.max(1, Math.min(rounds, 20));
        double easyPct = 0.45;
        double mediumPct = 0.40;
        double hardPct = 1.0 - easyPct - mediumPct;

        int easyCount = (int) Math.round(capped * easyPct);
        int mediumCount = (int) Math.round(capped * mediumPct);
        int hardCount = capped - easyCount - mediumCount;

        if (hardCount < 0) {
            hardCount = 0;
            int sum = easyCount + mediumCount;
            if (sum > capped) {
                if (easyCount >= mediumCount) easyCount -= (sum - capped);
                else mediumCount -= (sum - capped);
            }
        }

        if (capped >= 3) {
            if (easyCount == 0) easyCount = 1;
            if (mediumCount == 0) mediumCount = 1;
            if (hardCount == 0) hardCount = 1;
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

        return Collections.unmodifiableList(list);
    }

    private void repeatAdd(List<Integer> list, int value, int times) {
        for (int i = 0; i < times; i++) list.add(value);
    }

    public String getSessionId() {
        return sessionId;
    }

    public ClientHandler getPlayerA() {
        return playerA;
    }

    public ClientHandler getPlayerB() {
        return playerB;
    }

    public long getMatchSeed() {
        return matchSeed;
    }

    public void beginGame() {
        // scheduled start time (may be moved earlier if both players ready)
        this.matchStartTimeMs = System.currentTimeMillis() + initialCountdownMs;

        preGenerateAllPuzzles();
        broadcastMatchInfo();

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
                long roundSeed = deriveRoundSeed(matchSeed, roundIndex, 0);
                p = generator.generatePuzzle(difficultySequence.get(roundIndex), new Random(roundSeed));
            }
            if (usedTargets.contains(p.getTarget())) {
                System.out.printf("WARN: duplicate target for round %d target=%d%n", roundIndex, p.getTarget());
            }
            usedTargets.add(p.getTarget());
            preGeneratedPuzzles.add(p);
        }
    }

    private void ensureBufferedPuzzles(int currentIndex) {
        int needUpTo = Math.min(totalRounds - 1, currentIndex + bufferAhead);
        Set<Integer> usedTargets = new HashSet<>();
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
        msg.put("server_time", now);
        msg.put("countdown_ms", Math.max(0, matchStartTimeMs - now));
        msg.put("question_count", totalRounds);
        msg.put("per_question_seconds", questionTimeoutSeconds);

        String json = JsonUtil.toJson(msg);
        safeSendMessage(playerA, json);
        safeSendMessage(playerB, json);
    }

    private void runStartMatch() {
        // mark the actual start time precisely when match begins
        this.matchStartTimeMs = System.currentTimeMillis();

        safeSendInfo(playerA, "Trận đấu bắt đầu!");
        safeSendInfo(playerB, "Trận đấu bắt đầu!");
        runStartNextRound();
    }

    private void runStartNextRound() {
        if (finished.get()) return;

        int roundIndex = currentRound.getAndIncrement();
        if (roundIndex >= totalRounds) {
            finishGameInternal();
            return;
        }

        activeRoundIndex = roundIndex;
        ensureBufferedPuzzles(roundIndex);

        currentPuzzle = getPuzzleForRound(roundIndex);
        int difficulty = difficultySequence.get(roundIndex);
        long roundSeed = deriveRoundSeed(matchSeed, roundIndex);

        long interGap = computeInterRoundGapMs();
        long serverRoundStartMs = System.currentTimeMillis() + interGap;

        try {
            System.out.printf("DEBUG preparing_round: session=%s round=%d roundSeed=%d target=%d startAt=%d%n",
                    sessionId, roundIndex, roundSeed, currentPuzzle.getTarget(), serverRoundStartMs);
        } catch (Exception ignored) {
        }

        revealedTargets.add(currentPuzzle.getTarget());

        Duration placeholderTime = Duration.ofSeconds(questionTimeoutSeconds);
        sendPuzzleToPlayers(currentPuzzle, difficulty, placeholderTime, roundIndex + 1, roundIndex, roundSeed, Instant.ofEpochMilli(serverRoundStartMs));

        if (activationFuture != null && !activationFuture.isDone()) activationFuture.cancel(false);
        if (roundTimeoutFuture != null && !roundTimeoutFuture.isDone()) roundTimeoutFuture.cancel(false);

        roundActive = false;
        roundStart = null;

        long delayToActivate = Math.max(0L, serverRoundStartMs - System.currentTimeMillis());
        activationFuture = scheduler.schedule(() -> {
            roundStart = Instant.ofEpochMilli(serverRoundStartMs);
            roundActive = true;
            roundTimeoutFuture = scheduler.schedule(this::onRoundTimeout, questionTimeoutSeconds * 1000L, TimeUnit.MILLISECONDS);
            safeSendInfo(playerA, "Bắt đầu vòng " + (roundIndex + 1));
            safeSendInfo(playerB, "Bắt đầu vòng " + (roundIndex + 1));
        }, delayToActivate, TimeUnit.MILLISECONDS);
    }

    private long computeInterRoundGapMs() {
        try {
            long rttA = (playerA != null) ? playerA.getEstimatedRttMs() : 0L;
            long rttB = (playerB != null) ? playerB.getEstimatedRttMs() : 0L;
            long maxRtt = Math.max(0L, Math.max(rttA, rttB));
            long computed = 2L * maxRtt + 50L;
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
        if (currentPuzzle == null) {
            sendSimpleAnswerResult(player, false, "no_active_round");
            return;
        }
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
            for (int a = 1; a < 10; a++) deck.add(a);
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

        if (!roundActive) return;
        roundActive = false;

        long playMs = Duration.between(roundStart, serverRecv).toMillis();
        long maxMs = questionTimeoutSeconds * 1000L;
        if (playMs < 0) playMs = 0;
        if (playMs > maxMs) playMs = maxMs;
        Duration playTime = Duration.ofMillis(playMs);

        totalPlayTime.put(player, totalPlayTime.getOrDefault(player, Duration.ZERO).plus(playTime));
        scores.put(player, scores.getOrDefault(player, 0) + 1);

        recordRoundResultsOnCorrect(player, activeRoundIndex, playTime);

        if (roundTimeoutFuture != null && !roundTimeoutFuture.isDone()) {
            roundTimeoutFuture.cancel(false);
        }
        if (activationFuture != null && !activationFuture.isDone()) {
            activationFuture.cancel(false);
        }

        broadcastRoundSummary(activeRoundIndex);
        runStartNextRound();
    }

    private void recordRoundResultsOnCorrect(ClientHandler winner, int roundIndex, Duration winnerPlayTime) {
        ClientHandler loser = (winner == playerA) ? playerB : playerA;

        RoundResult rWinner = new RoundResult(roundIndex, true, winnerPlayTime.toMillis(), Instant.now().toEpochMilli());
        roundHistory.get(winner).add(rWinner);

        RoundResult rLoser = new RoundResult(roundIndex, false, 0L, Instant.now().toEpochMilli());
        roundHistory.get(loser).add(rLoser);
    }

    private void onRoundTimeout() {
        if (!roundActive) {
            return;
        }
        roundActive = false;

        RoundResult rA = new RoundResult(activeRoundIndex, false, 0L, Instant.now().toEpochMilli());
        RoundResult rB = new RoundResult(activeRoundIndex, false, 0L, Instant.now().toEpochMilli());
        roundHistory.get(playerA).add(rA);
        roundHistory.get(playerB).add(rB);

        broadcastRoundSummary(activeRoundIndex);
        runStartNextRound();
    }

    public void handleReady(ClientHandler from) {
        if (from == null) return;
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

    public void handleForfeit(ClientHandler who) {
        if (who == null) return;
        scheduler.execute(() -> {
            if (finished.get()) return;
            ClientHandler other = (who == playerA) ? playerB : playerA;

            safeSendInfo(who, "Bạn đã hủy trận. Bạn thua.");
            if (other != null) safeSendInfo(other, "Đối thủ đã hủy, bạn thắng (forfeit).");

            applyForfeitScoringAndFinish(who);
        });
    }

    public void handlePlayerDisconnect(ClientHandler disconnected) {
        if (disconnected == null) return;
        scheduler.execute(() -> {
            if (finished.get()) return;
            ClientHandler other = (disconnected == playerA) ? playerB : playerA;

            if (other != null) safeSendInfo(other, "Đối thủ đã ngắt kết nối. Bạn thắng (forfeit).");

            applyForfeitScoringAndFinish(disconnected);
        });
    }

    private void applyForfeitScoringAndFinish(ClientHandler forfeiter) {
        if (forfeiter == null) return;
        if (finished.get()) return;

        ClientHandler winner = (forfeiter == playerA) ? playerB : playerA;
        long nowTs = Instant.now().toEpochMilli();

        scores.put(forfeiter, 0);
        scores.put(winner, totalRounds);

        List<RoundResult> histForfeiter = roundHistory.getOrDefault(forfeiter, new ArrayList<>());
        List<RoundResult> histWinner = roundHistory.getOrDefault(winner, new ArrayList<>());
        roundHistory.putIfAbsent(forfeiter, histForfeiter);
        roundHistory.putIfAbsent(winner, histWinner);

        Set<Integer> recForfeiter = new HashSet<>();
        for (RoundResult r : roundHistory.get(forfeiter)) recForfeiter.add(r.roundIndex);
        Set<Integer> recWinner = new HashSet<>();
        for (RoundResult r : roundHistory.get(winner)) recWinner.add(r.roundIndex);

        for (int r = 0; r < totalRounds; r++) {
            boolean fHas = recForfeiter.contains(r);
            boolean wHas = recWinner.contains(r);

            if (fHas && wHas) continue;

            if (!wHas) {
                RoundResult rw = new RoundResult(r, true, 0L, nowTs);
                roundHistory.get(winner).add(rw);
            }
            if (!fHas) {
                RoundResult rf = new RoundResult(r, false, 0L, nowTs);
                roundHistory.get(forfeiter).add(rf);
            }
        }

        finishGameInternal();
    }

    private void finishGameInternal() {
        if (!finished.compareAndSet(false, true)) return;

        // mark end time precisely when game finishes
        this.matchEndTimeMs = System.currentTimeMillis();

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
        msg.put("round_history", exportRoundHistory());
        msg.put("revealed_targets", revealedTargets);

        String json = JsonUtil.toJson(msg);
        safeSendMessage(playerA, json);
        safeSendMessage(playerB, json);

        persistResultsToDatabase(json);

        try {
            playerA.clearCurrentGame();
        } catch (Exception ignored) {
        }
        try {
            playerB.clearCurrentGame();
        } catch (Exception ignored) {
        }

        try {
            if (activationFuture != null && !activationFuture.isDone()) activationFuture.cancel(false);
        } catch (Exception ignored) {
        }
        try {
            if (roundTimeoutFuture != null && !roundTimeoutFuture.isDone()) roundTimeoutFuture.cancel(false);
        } catch (Exception ignored) {
        }
        try {
            if (startFuture != null && !startFuture.isDone()) startFuture.cancel(false);
        } catch (Exception ignored) {
        }
        try {
            scheduler.shutdownNow();
        } catch (Exception ignored) {
        }
    }

    private void persistResultsToDatabase(String gameOverJson) {
        if (!persistResults || gameDAO == null) return;

        Map<String, Integer> scoresMap = exportScores();
        Map<String, Long> playTimeMap = exportPlayTime();
        Map<String, List<Map<String, Object>>> roundHist = exportRoundHistory();
        String winnerId = null;
        try {
            int scoreA = scores.getOrDefault(playerA, 0);
            int scoreB = scores.getOrDefault(playerB, 0);
            if (scoreA > scoreB) winnerId = safeGetPlayerId(playerA);
            else if (scoreB > scoreA) winnerId = safeGetPlayerId(playerB);
            else {
                long timeA = playTimeMap.getOrDefault(safeGetPlayerId(playerA), 0L);
                long timeB = playTimeMap.getOrDefault(safeGetPlayerId(playerB), 0L);
                if (timeA < timeB) winnerId = safeGetPlayerId(playerA);
                else if (timeB < timeA) winnerId = safeGetPlayerId(playerB);
            }
        } catch (Exception ignored) {
        }

        try {
            GameMatch match = new GameMatch();
            match.setId(sessionId);

            // Use the precise start time if available, otherwise null
            if (matchStartTimeMs > 0) {
                LocalDateTime started = Instant.ofEpochMilli(matchStartTimeMs).atZone(ZoneId.systemDefault()).toLocalDateTime();
                match.setStartedAt(started);
            } else {
                match.setStartedAt(null);
            }

            match.setTotalRounds(totalRounds);
            match.setStatus("finished");

            // Use the precise end time if available; otherwise set to now
            if (matchEndTimeMs > 0) {
                LocalDateTime ended = Instant.ofEpochMilli(matchEndTimeMs).atZone(ZoneId.systemDefault()).toLocalDateTime();
                match.setEndedAt(ended);
            } else {
                match.setEndedAt(LocalDateTime.now());
            }

            List<GameHistory> histories = new ArrayList<>(2);

            // playerA
            Player pA = null;
            try { pA = playerA.getPlayer(); } catch (Exception ignored) {}
            if (pA == null) {
                pA = new Player();
                pA.setId(safeGetPlayerId(playerA));
                pA.setUsername(safeGetUsername(playerA));
            }
            GameHistory gha = new GameHistory();
            gha.setId(new GameHistoryId(sessionId, pA.getId()));
            gha.setMatch(match);
            gha.setPlayer(pA);
            gha.setFinalScore(scoresMap.getOrDefault(pA.getId(), 0));
            gha.setTotalTime(playTimeMap.getOrDefault(pA.getId(), 0L));
            gha.setResult(determineResultForPlayer(pA.getId(), winnerId));
            histories.add(gha);

            // playerB
            Player pB = null;
            try { pB = playerB.getPlayer(); } catch (Exception ignored) {}
            if (pB == null) {
                pB = new Player();
                pB.setId(safeGetPlayerId(playerB));
                pB.setUsername(safeGetUsername(playerB));
            }
            GameHistory ghb = new GameHistory();
            ghb.setId(new GameHistoryId(sessionId, pB.getId()));
            ghb.setMatch(match);
            ghb.setPlayer(pB);
            ghb.setFinalScore(scoresMap.getOrDefault(pB.getId(), 0));
            ghb.setTotalTime(playTimeMap.getOrDefault(pB.getId(), 0L));
            ghb.setResult(determineResultForPlayer(pB.getId(), winnerId));
            histories.add(ghb);

            gameDAO.persistGameFinal(match, histories, roundHist);
            System.out.println("[GameSession] persisted via GameRepository for game=" + sessionId);

        } catch (Exception ex) {
            System.err.println("[GameSession] Failed to persist via GameRepository: " + ex.getMessage());
            ex.printStackTrace();
            System.out.println("[GameSession] GameOver JSON:");
            System.out.println(gameOverJson);
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
        try {
            if (p != null) p.sendType(MessageType.INFO, text);
        } catch (Exception ignored) {
        }
    }

    private void safeSendMessage(ClientHandler p, String json) {
        try {
            if (p != null) p.sendMessage(json);
        } catch (Exception ignored) {
        }
    }

    private static long deriveRoundSeed(long sessionSeed, int roundIndex) {
        return deriveRoundSeed(sessionSeed, roundIndex, 0);
    }

    private static long deriveRoundSeed(long sessionSeed, int roundIndex, int attempt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(longToBytes(sessionSeed));
            md.update(intToBytes(roundIndex));
            md.update(intToBytes(attempt));
            byte[] digest = md.digest();
            return ByteBuffer.wrap(digest, 0, 8).getLong();
        } catch (Exception ex) {
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
            if (obj == null) {
                sb.append("null");
                return;
            }
            if (obj instanceof Number || obj instanceof Boolean) {
                sb.append(obj.toString());
            } else if (obj instanceof String) {
                sb.append('"').append(escape((String) obj)).append('"');
            } else if (obj instanceof Map) {
                sb.append('{');
                Map<?, ?> m = (Map<?, ?>) obj;
                Iterator<? extends Map.Entry<?, ?>> it = m.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<?, ?> e = it.next();
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
                sb.append('"').append(escape(String.valueOf(obj))).append('"');
            }
        }

        private static String escape(String s) {
            StringBuilder out = new StringBuilder(s.length() + 8);
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"':
                        out.append("\\\"");
                        break;
                    case '\\':
                        out.append("\\\\");
                        break;
                    case '\b':
                        out.append("\\b");
                        break;
                    case '\f':
                        out.append("\\f");
                        break;
                    case '\n':
                        out.append("\\n");
                        break;
                    case '\r':
                        out.append("\\r");
                        break;
                    case '\t':
                        out.append("\\t");
                        break;
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