package com.mathspeed.server.service;

import com.mathspeed.dao.GameDAO;
import com.mathspeed.network.ClientHandler;
import com.mathspeed.protocol.MessageType;
import com.mathspeed.puzzle.MathPuzzleFormat;
import com.mathspeed.puzzle.MathPuzzleGenerator;
import com.mathspeed.puzzle.MathExpressionEvaluator;

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

    // Scheduler: single-thread executor to serialize state mutations and avoid races.
    private final ScheduledExecutorService scheduler;

    // State that is only mutated on scheduler thread:
    private final AtomicInteger currentRound = new AtomicInteger(0);
    private final Map<ClientHandler, Integer> scores = new HashMap<>();
    private final Map<ClientHandler, Duration> totalPlayTime = new HashMap<>();
    private final List<Integer> difficultySequence;

    // Per-round history for each player (kept on scheduler thread)
    private final Map<ClientHandler, List<RoundResult>> roundHistory = new HashMap<>();

    // Futures scheduled on scheduler thread
    private ScheduledFuture<?> roundTimeoutFuture;
    private ScheduledFuture<?> startFuture;

    private MathPuzzleFormat currentPuzzle;
    private Instant roundStart;
    private boolean roundActive = false;
    private int activeRoundIndex = -1; // index of the currently active round

    private final boolean persistResults;
    private final GameDAO gameDAO;

    private final int matchSeed;
    private long matchStartTimeMs = -1L;
    private final AtomicBoolean finished = new AtomicBoolean(false);

    private final Map<ClientHandler, Boolean> readyMap = new HashMap<>();
    private final int initialCountdownMs = 5000; // 5s default
    private final int fastStartBufferMs = 500;

    public GameSession(ClientHandler playerA,
                       ClientHandler playerB,
                       int totalRounds,
                       long questionTimeoutSeconds,
                       GameDAO gameDAO) {
        this.sessionId = UUID.randomUUID().toString();
        this.playerA = Objects.requireNonNull(playerA);
        this.playerB = Objects.requireNonNull(playerB);
        this.totalRounds = totalRounds;
        this.questionTimeoutSeconds = questionTimeoutSeconds;
        this.generator = new MathPuzzleGenerator(1);
        this.persistResults = true;
        this.gameDAO = gameDAO;

        this.matchSeed = new Random().nextInt(Integer.MAX_VALUE);

        // scheduler thread named for easier debugging
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

        // Generate difficulty sequence, shuffle deterministically with matchSeed
        this.difficultySequence = generateDifficultyList(totalRounds, matchSeed);
    }

    private List<Integer> generateDifficultyList(int rounds, int seed) {
        int easyCount = (int) Math.round(rounds * 0.45);
        int mediumCount = (int) Math.round(rounds * 0.40);
        int hardCount = rounds - easyCount - mediumCount;

        List<Integer> list = new ArrayList<>(rounds);
        repeatAdd(list, 1, easyCount);
        repeatAdd(list, 2, mediumCount);
        repeatAdd(list, 3, hardCount);

        // Shuffle to avoid grouping all easy/medium/hard together, deterministic by seed
//        Collections.shuffle(list, new Random(seed));
        return Collections.unmodifiableList(list);
    }

    private void repeatAdd(List<Integer> list, int value, int times) {
        for (int i = 0; i < times; i++) list.add(value);
    }

    public String getSessionId() { return sessionId; }
    public ClientHandler getPlayerA() { return playerA; }
    public ClientHandler getPlayerB() { return playerB; }
    public int getMatchSeed() { return matchSeed; }

    public void beginGame() {
        this.matchStartTimeMs = System.currentTimeMillis() + initialCountdownMs;

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

        int difficulty = difficultySequence.get(roundIndex);
        long roundSeed = mixSeed(matchSeed, roundIndex);
        Random puzzleRnd = new Random(roundSeed);
        currentPuzzle = generator.generatePuzzle(difficulty, puzzleRnd);

        try {
            System.out.printf("DEBUG new_round: session=%s round=%d seed=%d target=%d%n",
                    sessionId, roundIndex, roundSeed, currentPuzzle.getTarget());
        } catch (Exception ignored) {}

        Duration roundTime = Duration.ofSeconds(questionTimeoutSeconds);
        roundStart = Instant.now();
        roundActive = true;

        sendPuzzleToPlayers(currentPuzzle, difficulty, roundTime, roundIndex + 1, roundIndex, roundSeed, roundStart);

        // cancel previous timeout if any
        if (roundTimeoutFuture != null && !roundTimeoutFuture.isDone()) {
            roundTimeoutFuture.cancel(false);
        }
        roundTimeoutFuture = scheduler.schedule(this::onRoundTimeout, roundTime.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static long mixSeed(int matchSeed, int roundIndex) {
        long a = Integer.toUnsignedLong(matchSeed);
        long b = Integer.toUnsignedLong(roundIndex);
        long mixed = a * 0x9E3779B97F4A7C15L + ((b << 32) | b);
        return mixed ^ (mixed >>> 32);
    }

    private void sendPuzzleToPlayers(MathPuzzleFormat puzzle, int difficulty, Duration roundTime,
                                     int roundNumber, int roundIndex, long roundSeed, Instant roundStartInstant) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", MessageType.NEW_ROUND.name());
        msg.put("round", roundNumber);
        msg.put("difficulty", difficulty);
        msg.put("target", puzzle.getTarget());
        msg.put("time", roundTime.getSeconds());
        msg.put("seed", matchSeed);          // match seed (global)
        msg.put("round_seed", roundSeed);   // per-round seed for reproducibility
        msg.put("round_index", roundIndex);
        msg.put("server_round_start", roundStartInstant.toEpochMilli());

        String json = JsonUtil.toJson(msg);
        safeSendMessage(playerA, json);
        safeSendMessage(playerB, json);
    }

    /**
     * Public API called from client threads. We enqueue the processing on the scheduler thread
     * so that state changes (scores, round transitions) are serialized.
     */
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
        if (currentPuzzle == null || roundStart == null || !roundActive) {
            sendSimpleAnswerResult(player, false, "no_active_round");
            return;
        }

        if (serverRecv.isBefore(roundStart)) {
            sendSimpleAnswerResult(player, false, "too_early");
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

        Instant now = serverRecv;
        Duration playTime = Duration.between(roundStart, now);
        totalPlayTime.put(player, totalPlayTime.getOrDefault(player, Duration.ZERO).plus(playTime));
        scores.put(player, scores.getOrDefault(player, 0) + 1);

        // record round result for both players
        recordRoundResultsOnCorrect(player, activeRoundIndex, playTime);

        // cancel timeout and start next round immediately
        if (roundTimeoutFuture != null && !roundTimeoutFuture.isDone()) {
            roundTimeoutFuture.cancel(false);
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
        // Just broadcast latest info - safe to call from any thread
        scheduler.execute(this::broadcastMatchInfo);
    }

    /**
     * Handle explicit forfeit (player pressed "Hủy" / forfeit) while still connected.
     * This will treat the player as having forfeited the match: opponent is awarded the win.
     * The forfeiting player remains connected (socket not closed) but is removed from the current game.
     */
    public void handleForfeit(ClientHandler who) {
        if (who == null) return;
        scheduler.execute(() -> {
            if (finished.get()) return;

            ClientHandler other = (who == playerA) ? playerB : playerA;

            safeSendInfo(who, "Bạn đã hủy trận. Bạn thua.");
            if (other != null) safeSendInfo(other, "Đối thủ đã hủy, bạn thắng (forfeit).");

            // award opponent a point (or apply your tournament rules)
            if (other != null) {
                scores.put(other, scores.getOrDefault(other, 0) + 1);
            }

            // record current round result if a round is active
            if (activeRoundIndex >= 0) {
                // forfeiter loses current round
                RoundResult rForfeit = new RoundResult(activeRoundIndex, false, 0L, Instant.now().toEpochMilli());
                roundHistory.get(who).add(rForfeit);

                // opponent wins current round (playTime 0 or as per rule)
                if (other != null) {
                    RoundResult rOther = new RoundResult(activeRoundIndex, true, 0L, Instant.now().toEpochMilli());
                    roundHistory.get(other).add(rOther);
                }
            }

            // mark game finished and persist/send results
            finishGameInternal();

            // keep socket open: do not call disconnected/clearCurrentGame here explicitly beyond finishGameInternal cleanup
            // (finishGameInternal will call playerX.clearCurrentGame()).
        });
    }

    public void handlePlayerDisconnect(ClientHandler disconnected) {
        if (disconnected == null) return;
        scheduler.execute(() -> {
            if (finished.get()) return;
            ClientHandler other = (disconnected == playerA) ? playerB : playerA;
            if (other != null) safeSendInfo(other, "Đối thủ đã ngắt kết nối. Bạn thắng (forfeit).");
            if (other != null) scores.put(other, scores.getOrDefault(other, 0) + 1);

            // record disconnect as a loss for disconnected player for current round if active
            if (roundActive && activeRoundIndex >= 0) {
                RoundResult rDisc = new RoundResult(activeRoundIndex, false, 0L, Instant.now().toEpochMilli());
                roundHistory.get(disconnected).add(rDisc);

                RoundResult rOther = new RoundResult(activeRoundIndex, true, 0L, Instant.now().toEpochMilli());
                roundHistory.get(other).add(rOther);
            }

            finishGameInternal();
        });
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

        String json = JsonUtil.toJson(msg);
        safeSendMessage(playerA, json);
        safeSendMessage(playerB, json);

        if (persistResults) {
            try {
                // Pass winner userId (String) instead of ClientHandler to match GameDAO API
                String winnerId = (winner != null) ? safeGetPlayerId(winner) : null;
                gameDAO.persistGameFinal(sessionId, exportScores(), exportPlayTime(), exportRoundHistory(), winnerId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        try { playerA.clearCurrentGame(); } catch (Exception ignored) {}
        try { playerB.clearCurrentGame(); } catch (Exception ignored) {}

        try { if (roundTimeoutFuture != null && !roundTimeoutFuture.isDone()) roundTimeoutFuture.cancel(false); } catch (Exception ignored) {}
        try { if (startFuture != null && !startFuture.isDone()) startFuture.cancel(false); } catch (Exception ignored) {}
        try { scheduler.shutdownNow(); } catch (Exception ignored) {}
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