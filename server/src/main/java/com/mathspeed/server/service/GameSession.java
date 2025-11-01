package com.mathspeed.server.service;

import com.mathspeed.dao.GameDAO;
import com.mathspeed.network.ClientHandler;
import com.mathspeed.protocol.MessageType;
import com.mathspeed.puzzle.*;

import static com.mathspeed.puzzle.MathPuzzleUtils.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GameSession - sends questions to two players, scores answers and optionally persists final results.
 *
 * Behavior:
 * - Uses PuzzleQuestionProvider (or injected provider) to get full JSON payload + canonical answer.
 * - Parses the provider payload to extract the exact target and the numbers set for validation.
 * - Sends to clients ONLY the "display" and the "numbers" fields (compact JSON) with questionId and difficulty.
 * - Uses MathExpressionEvaluator to evaluate client-submitted expressions and compare exactly with the stored target.
 * - First correct submission wins the round immediately (awarded 1 point), timeout for round is cancelled,
 *   and session proceeds to the next round (no waiting for remaining time).
 */
public class GameSession {

    private static final int DEFAULT_PUZZLE_DIFFICULTY = 1;

    // Difficulty constants
    private static final int DIFF_EASY = 1;
    private static final int DIFF_MEDIUM = 2;
    private static final int DIFF_HARD = 3;

    // desired distribution (as fractions)
    private static final double PCT_EASY = 0.50;   // 50%
    private static final double PCT_MEDIUM = 0.30; // 30%
//    private static final double PCT_HARD = 0.20;   // 20%

    private final String id;
    private final ClientHandler p1;
    private final ClientHandler p2;
    private final ScheduledExecutorService scheduler;
    private final QuestionProvider injectedQuestionProvider;
    private final GameDAO gameDAO; // may be null (no persistence)

    private final int totalQuestions;
    private final long questionTimeoutSeconds;
    private final AtomicInteger currentIndex = new AtomicInteger(0);

    // schedule of difficulties for each round (length == totalQuestions)
    private final List<Integer> difficultySchedule;

    // runtime state
    private final ConcurrentMap<String, String> currentAnswers = new ConcurrentHashMap<>(); // questionId -> provider answer string (legacy)
    private final ConcurrentMap<String, Integer> scores = new ConcurrentHashMap<>(); // userKey -> score
    private final ConcurrentMap<String, Long> totalElapsedMs = new ConcurrentHashMap<>(); // userKey -> total elapsed ms
    private final ConcurrentMap<String, String> firstCorrectAnswerByQuestion = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> roundStartTimes = new ConcurrentHashMap<>(); // questionId -> start instant

    // NEW: store per-question allowed numbers and numeric target (if present)
    private final ConcurrentMap<String, List<Integer>> currentQuestionNumbers = new ConcurrentHashMap<>(); // questionId -> allowed numbers
    private final ConcurrentMap<String, MathPuzzleUtils.Fraction> currentQuestionTargets = new ConcurrentHashMap<>(); // questionId -> exact fraction target

    // timeout future per active question (so we can cancel when a player answers first-correct)
    private final ConcurrentMap<String, ScheduledFuture<?>> questionTimeoutFutures = new ConcurrentHashMap<>();

    private volatile String currentQuestionId = null;
    private volatile ScheduledFuture<?> questionFuture; // for scheduling next send
    private final Object endLock = new Object();
    private volatile boolean ended = false;

    // evaluator for expressions
    private final MathExpressionEvaluator evaluator = new MathExpressionEvaluator();

    /**
     * Constructor with persistence support.
     *
     * @param id                    game id (UUID string) or null to generate one
     * @param p1                    player1 handler
     * @param p2                    player2 handler
     * @param questionProvider      provider for questions (may be null => PuzzleQuestionProvider used)
     * @param totalQuestions        total rounds in this match
     * @param questionTimeoutSeconds seconds for each question (pass 40 for your requested behaviour)
     * @param gameDAO               DAO used to persist final game summary (may be null)
     */
    public GameSession(String id,
                       ClientHandler p1,
                       ClientHandler p2,
                       QuestionProvider questionProvider,
                       int totalQuestions,
                       long questionTimeoutSeconds,
                       GameDAO gameDAO) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.p1 = p1;
        this.p2 = p2;
        this.injectedQuestionProvider = questionProvider;
        this.totalQuestions = Math.max(1, totalQuestions);
        this.questionTimeoutSeconds = Math.max(1, questionTimeoutSeconds);
        this.gameDAO = gameDAO;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "game-session-" + this.id);
            t.setDaemon(true);
            return t;
        });

        // prepare difficulty schedule according to desired distribution
        this.difficultySchedule = buildDifficultySchedule(this.totalQuestions);

        // initialize scores and elapsed for both players
        String u1 = resolvePlayerKey(p1);
        String u2 = resolvePlayerKey(p2);
        scores.put(u1, 0);
        scores.put(u2, 0);
        totalElapsedMs.put(u1, 0L);
        totalElapsedMs.put(u2, 0L);
    }

    public GameSession(String id, ClientHandler p1, ClientHandler p2) {
        this(id, p1, p2, null, 10, 40L, null); // default 40s per your requirement
    }

    public String getId() { return id; }
    public ClientHandler getPlayer1() { return p1; }
    public ClientHandler getPlayer2() { return p2; }

    /**
     * Start session and persist header (game + players) if DAO available.
     */
    public void start() {
        if (gameDAO != null) {
            try {
                gameDAO.insertGame(id, totalQuestions);
                List<String> userIds = Arrays.asList(resolvePlayerKey(p1), resolvePlayerKey(p2));
                gameDAO.insertGamePlayersByIds(id, userIds);
            } catch (Exception e) {
                System.err.println("GameSession: failed to persist game header: " + e.getMessage());
            }
        }

        try { p1.sendType(MessageType.GAME_START, id + "|" + resolvePlayerKey(p2)); } catch (Exception e) { p1.sendMessage("GAME_START|" + id + "|" + resolvePlayerKey(p2)); }
        try { p2.sendType(MessageType.GAME_START, id + "|" + resolvePlayerKey(p1)); } catch (Exception e) { p2.sendMessage("GAME_START|" + id + "|" + resolvePlayerKey(p1)); }

        scheduleNextQuestion(0);
    }

    private void scheduleNextQuestion(long delaySeconds) {
        questionFuture = scheduler.schedule(this::sendNextQuestion, delaySeconds, TimeUnit.SECONDS);
    }

    /**
     * sendNextQuestion: obtains a full provider payload but sends to clients only:
     *   { "display": "...", "numbers": [...] }
     * accompanied by questionId and difficulty.
     *
     * The full provider payload is parsed server-side to obtain exact target and allowed numbers for validation.
     */
    private void sendNextQuestion() {
        int idx = currentIndex.getAndIncrement();
        if (idx >= totalQuestions) {
            end();
            return;
        }

        // determine provider: prefer injected provider; if absent use PuzzleQuestionProvider(default difficulty)
        QuestionProvider provider = this.injectedQuestionProvider;

        // Determine difficulty for this round from schedule
        int difficultyForRound = DEFAULT_PUZZLE_DIFFICULTY;
        if (idx < difficultySchedule.size()) {
            difficultyForRound = difficultySchedule.get(idx);
        }

        List<String> qs;
        try {
            PuzzleQuestionProvider prov = new PuzzleQuestionProvider();
            qs = prov.getQuestions(1, difficultyForRound);
        } catch (Exception ex) {
            System.err.println("GameSession: question provider failed: " + ex.getMessage());
            end();
            return;
        }

        if (qs == null || qs.isEmpty()) {
            end();
            return;
        }

        String raw = qs.get(0);
        String questionPayload = raw;
        String correctAnswer = "";
        if (raw != null && raw.contains("@@")) {
            String[] parts = raw.split("@@", 2);
            questionPayload = parts[0];
            correctAnswer = parts[1];
        }

        String questionId = "q" + idx;
        currentQuestionId = questionId;
        currentAnswers.put(questionId, correctAnswer);
        firstCorrectAnswerByQuestion.remove(questionId);

        // Extract allowed numbers and exact target fraction from payload using helper methods
        List<Integer> numbers = extractNumbersFromPayload(questionPayload);
        MathPuzzleUtils.Fraction targetFraction = extractFractionTargetFromPayload(questionPayload);

        if (numbers != null && !numbers.isEmpty()) currentQuestionNumbers.put(questionId, numbers); else currentQuestionNumbers.remove(questionId);
        if (targetFraction != null) currentQuestionTargets.put(questionId, targetFraction); else currentQuestionTargets.remove(questionId);

        // record round start time
        roundStartTimes.put(questionId, Instant.now());

        // Build a minimal JSON for clients containing only "display" and "numbers".
        // We extract display value from the provider payload if present; fallback to canonical display from MathPuzzleFormat if needed.
        String displayValue = extractDisplayFromPayload(questionPayload);
        if (displayValue == null) {
            // fallback: use canonical display built from targetFraction
            if (targetFraction != null) {
                // build simple display: whole + fractional or just fraction
                long whole = targetFraction.wholePart();
                MathPuzzleUtils.Fraction frac = targetFraction.fractionalPart();
                if (targetFraction.isWhole()) {
                    displayValue = String.valueOf(targetFraction.getNumerator() / targetFraction.getDenominator());
                } else if (whole != 0) {
                    displayValue = whole + " " + Math.abs(frac.getNumerator()) + "/" + frac.getDenominator();
                } else {
                    displayValue = frac.toString();
                }
            } else {
                displayValue = ""; // unknown
            }
        }

        // Build client JSON: {"display":"...","numbers":[...]}
        StringBuilder clientJson = new StringBuilder(128);
        clientJson.append("{");
        clientJson.append("\"display\":\"").append(escapeForJson(displayValue)).append("\",");
        clientJson.append("\"numbers\":[");
        if (numbers != null && !numbers.isEmpty()) {
            for (int i = 0; i < numbers.size(); i++) {
                if (i > 0) clientJson.append(',');
                clientJson.append(numbers.get(i));
            }
        }
        clientJson.append("]");
        clientJson.append("}");

        // send to both players: questionId|<clientJson>|difficulty:<level>
        String payloadToClients = questionId + "|" + clientJson.toString() + "|" + "difficulty:" + difficultyForRound;
        safeSend(p1, MessageType.NEW_QUESTION, payloadToClients);
        safeSend(p2, MessageType.NEW_QUESTION, payloadToClients);

        // schedule question timeout handler for this round and keep future so it can be cancelled if needed
        ScheduledFuture<?> timeoutFuture = scheduler.schedule(() -> {
            // on timeout, handle end of round and schedule next question after 0s
            handleQuestionTimeout(questionId);
            scheduleNextQuestion(0);
        }, questionTimeoutSeconds, TimeUnit.SECONDS);

        questionTimeoutFutures.put(questionId, timeoutFuture);
    }

    /**
     * Submit an answer for the currently active question.
     *
     * Rules:
     * - Only submissions for the active questionId are accepted.
     * - If a player submits the first correct answer, they get 1 point immediately,
     *   the round ends immediately (timeout cancelled) and session proceeds to next question.
     * - Other player's subsequent submissions for same round cannot earn points.
     */
    public boolean submitAnswer(String usernameOrId, String questionId, String answer) {
        if (ended) return false;
        String currQ = currentQuestionId;
        if (currQ == null || !currQ.equals(questionId)) return false;

        String correct = currentAnswers.get(questionId);
        if (correct == null) return false;

        String a = answer == null ? "" : answer.trim();
        String corr = correct == null ? "" : correct.trim();

        // compute elapsed_ms from roundStartTimes
        Instant start = roundStartTimes.get(questionId);
        long elapsed = 0L;
        if (start != null) elapsed = Duration.between(start, Instant.now()).toMillis();

        String key1 = resolvePlayerKey(p1);
        String key2 = resolvePlayerKey(p2);
        String playerKey = mapSubmittedKey(usernameOrId, key1, key2);

        // If round already has a first correct, reject awarding points to others
        if (firstCorrectAnswerByQuestion.containsKey(questionId)) {
            // still record submission and elapsed but no points
            totalElapsedMs.merge(playerKey, elapsed, Long::sum);
            broadcastAnswerResult(playerKey, questionId, false, 0);
            return true;
        }

        boolean isCorrect = false;
        try {
            MathPuzzleUtils.Fraction targetFraction = currentQuestionTargets.get(questionId);
            List<Integer> allowedNumbers = currentQuestionNumbers.get(questionId);

            if (targetFraction != null) {
                // Validate using evaluator. evaluator expects expression string and allowedNumbers and target (as Fraction)
                if (evaluator.isExpressionValid(a, allowedNumbers != null ? allowedNumbers : Collections.emptyList(), targetFraction)) {
                    isCorrect = true;
                } else {
                    // fallback: try parse numeric literal exactly (client may have sent "10/3" or "3 1/3")
                    MathPuzzleUtils.Fraction clientVal = null;
                    try {
                        clientVal = evaluator.evaluateExpression(a); // returns Fraction or throws
                    } catch (Exception ignored) {}
                    if (clientVal != null && clientVal.equals(targetFraction)) isCorrect = true;
                }
            } else {
                // No exact target in payload: try evaluating both provider answer and client expression
                MathPuzzleUtils.Fraction clientVal = null;
                MathPuzzleUtils.Fraction providerVal = null;
                try { clientVal = evaluator.evaluateExpression(a); } catch (Exception ignored) {}
                try { providerVal = evaluator.evaluateExpression(corr); } catch (Exception ignored) {}

                if (clientVal != null && providerVal != null && clientVal.equals(providerVal)) {
                    isCorrect = true;
                } else {
                    // fallback to case-insensitive string equality
                    if (!corr.isEmpty() && corr.equalsIgnoreCase(a)) isCorrect = true;
                }
            }
        } catch (Exception e) {
            System.err.println("submitAnswer: evaluation failed for '" + a + "': " + e.getMessage());
            isCorrect = false;
        }

        int points = isCorrect ? 1 : 0;
        if (isCorrect) {
            // award point only if first correct; use atomic check via putIfAbsent
            String prev = firstCorrectAnswerByQuestion.putIfAbsent(questionId, playerKey);
            if (prev == null) {
                // we are the first correct
                scores.merge(playerKey, points, Integer::sum);
                // update total elapsed
                totalElapsedMs.merge(playerKey, elapsed, Long::sum);
                // cancel timeout for this question and remove future
                ScheduledFuture<?> f = questionTimeoutFutures.remove(questionId);
                if (f != null) f.cancel(false);

                // broadcast immediate result indicating the round winner
                broadcastAnswerResult(playerKey, questionId, true, points);

                // cleanup round state and schedule next question immediately
                finishRoundAndScheduleNext(questionId);
                return true;
            } else {
                // somehow another thread just beat us; treat as wrong for scoring but still ack
                totalElapsedMs.merge(playerKey, elapsed, Long::sum);
                broadcastAnswerResult(playerKey, questionId, false, 0);
                return true;
            }
        } else {
            // incorrect submission: record elapsed and notify
            totalElapsedMs.merge(playerKey, elapsed, Long::sum);
            broadcastAnswerResult(playerKey, questionId, false, 0);
            return true;
        }
    }

    // Called when a player is first-correct: cleanup and advance immediately
    private void finishRoundAndScheduleNext(String questionId) {
        // clean current round state
        currentAnswers.remove(questionId);
        roundStartTimes.remove(questionId);
        currentQuestionNumbers.remove(questionId);
        currentQuestionTargets.remove(questionId);
        currentQuestionId = null;
        // schedule next question immediately (0 seconds)
        scheduleNextQuestion(0);
    }

    private void handleQuestionTimeout(String questionId) {
        // timeout: no one answered correctly in time (or no first-correct)
        String first = firstCorrectAnswerByQuestion.get(questionId);
        String winnerInfo = first == null ? "NONE" : first;
        String scorePayload = scoreSnapshot();
        String payload = questionId + "|" + winnerInfo + "|" + scorePayload;
        safeSend(p1, MessageType.ANSWER_RESULT, payload);
        safeSend(p2, MessageType.ANSWER_RESULT, payload);

        // cleanup
        currentAnswers.remove(questionId);
        roundStartTimes.remove(questionId);
        currentQuestionNumbers.remove(questionId);
        currentQuestionTargets.remove(questionId);
        currentQuestionId = null;
        firstCorrectAnswerByQuestion.remove(questionId);
        questionTimeoutFutures.remove(questionId);
    }

    private String scoreSnapshot() {
        String k1 = resolvePlayerKey(p1);
        String k2 = resolvePlayerKey(p2);
        int s1 = scores.getOrDefault(k1, 0);
        int s2 = scores.getOrDefault(k2, 0);
        return k1 + ":" + s1 + "|" + k2 + ":" + s2;
    }

    /**
     * End the game: compute final summary (final_score, total_time_ms, result) and persist via GameDAO if available.
     */
    public void end() {
        synchronized (endLock) {
            if (ended) return;
            ended = true;
        }

        // cancel any outstanding timeouts
        for (ScheduledFuture<?> f : questionTimeoutFutures.values()) {
            if (f != null) f.cancel(false);
        }
        questionTimeoutFutures.clear();
        if (questionFuture != null) questionFuture.cancel(false);

        // prepare final summary
        String key1 = resolvePlayerKey(p1);
        String key2 = resolvePlayerKey(p2);
        int score1 = scores.getOrDefault(key1, 0);
        int score2 = scores.getOrDefault(key2, 0);
        long time1 = totalElapsedMs.getOrDefault(key1, 0L);
        long time2 = totalElapsedMs.getOrDefault(key2, 0L);

        String result1, result2;
        if (score1 > score2) {
            result1 = "win"; result2 = "lose";
        } else if (score2 > score1) {
            result1 = "lose"; result2 = "win";
        } else {
            if (time1 < time2) { result1 = "win"; result2 = "lose"; }
            else if (time2 < time1) { result1 = "lose"; result2 = "win"; }
            else { result1 = "draw"; result2 = "draw"; }
        }

        String finalPayload = id + "|" + key1 + ":" + score1 + ":" + time1 + "|" + key2 + ":" + score2 + ":" + time2 + "|" + result1 + "|" + result2;
        safeSend(p1, MessageType.GAME_END, finalPayload);
        safeSend(p2, MessageType.GAME_END, finalPayload);

        if (gameDAO != null) {
            try {
                GameDAO.PlayerSummary ps1 = new GameDAO.PlayerSummary(key1, score1, time1, result1);
                GameDAO.PlayerSummary ps2 = new GameDAO.PlayerSummary(key2, score2, time2, result2);
                gameDAO.persistGameFinal(id, Arrays.asList(ps1, ps2));
            } catch (Exception e) {
                System.err.println("GameSession: failed to persist final game: " + e.getMessage());
            }
        }

        try { p1.setCurrentGame(null); } catch (Exception ignored) {}
        try { p2.setCurrentGame(null); } catch (Exception ignored) {}

        try { scheduler.shutdownNow(); } catch (Exception ignored) {}
    }

    private void safeSend(ClientHandler ch, MessageType t, String payload) {
        try {
            ch.sendType(t, payload);
        } catch (Exception e) {
            try { ch.sendMessage(t.name() + (payload != null ? ("|" + payload) : "")); } catch (Exception ignored) {}
        }
    }

    // Utility: prefer user id if available, otherwise fallback to username
    private String resolvePlayerKey(ClientHandler ch) {
        if (ch == null) return "";
        try {
            String id = ch.getUserId();
            if (id != null && !id.isEmpty()) return id;
        } catch (NoSuchMethodError | AbstractMethodError | Exception ignored) {}
        return ch.getUsername();
    }

    // Helper to map submitted username/id into internal player key (use user id if available)
    private String mapSubmittedKey(String submitted, String key1, String key2) {
        if (submitted == null) return key1;
        if (submitted.equals(key1) || submitted.equals(key2)) return submitted;
        if (submitted.equalsIgnoreCase(p1.getUsername())) return key1;
        if (submitted.equalsIgnoreCase(p2.getUsername())) return key2;
        return submitted;
    }

    // Helper to extract numbers array from a JSON-like payload: looks for "numbers":[...]
    private List<Integer> extractNumbersFromPayload(String payload) {
        if (payload == null) return Collections.emptyList();
        List<Integer> nums = new ArrayList<>();
        try {
            Pattern p = Pattern.compile("\"numbers\"\\s*:\\s*\\[([^\\]]*)\\]");
            Matcher m = p.matcher(payload);
            if (m.find()) {
                String inside = m.group(1);
                Matcher numMatcher = Pattern.compile("-?\\d+").matcher(inside);
                while (numMatcher.find()) {
                    nums.add(Integer.parseInt(numMatcher.group()));
                }
            }
        } catch (Exception ignored) {}
        return nums;
    }

    // Helper to extract exact fraction target from JSON-like payload produced by PuzzleQuestionProvider
    private MathPuzzleUtils.Fraction extractFractionTargetFromPayload(String payload) {
        if (payload == null) return null;
        try {
            // try to find "target":{ ... "numerator":X, "denominator":Y ...}
            Pattern p = Pattern.compile("\"target\"\\s*:\\s*\\{[^}]*\"numerator\"\\s*:\\s*(-?\\d+)\\s*,[^}]*\"denominator\"\\s*:\\s*(\\d+)[^}]*\\}");
            Matcher m = p.matcher(payload);
            if (m.find()) {
                long num = Long.parseLong(m.group(1));
                long den = Long.parseLong(m.group(2));
                return new MathPuzzleUtils.Fraction(num, den);
            }

            // fallback: try "target": "3 1/3" or "target":"10/3" or "target":3.333
            Pattern p2 = Pattern.compile("\"target\"\\s*:\\s*\"([^\"]+)\"");
            Matcher m2 = p2.matcher(payload);
            if (m2.find()) {
                String t = m2.group(1).trim();
                // try "a b/c" mixed
                Matcher mm = Pattern.compile("(-?\\d+)\\s+[ _]?(\\d+)/(\\d+)").matcher(t);
                if (mm.matches()) {
                    long whole = Long.parseLong(mm.group(1));
                    long num = Long.parseLong(mm.group(2));
                    long den = Long.parseLong(mm.group(3));
                    long total = whole * den + (whole >= 0 ? num : -num);
                    return new MathPuzzleUtils.Fraction(total, den);
                }
                // try a/b
                Matcher mm2 = Pattern.compile("(-?\\d+)/(\\d+)").matcher(t);
                if (mm2.matches()) {
                    long num = Long.parseLong(mm2.group(1));
                    long den = Long.parseLong(mm2.group(2));
                    return new MathPuzzleUtils.Fraction(num, den);
                }
                // try decimal
                try {
                    double dv = Double.parseDouble(t);
                    // convert decimal approx to fraction with limited precision (use 1e-6)
                    long pwr = 1_000_000L;
                    long numerator = Math.round(dv * pwr);
                    return new MathPuzzleUtils.Fraction(numerator, pwr);
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception ignored) {}
        return null;
    }

    // Helper to extract "display" string from provider payload if present
    private String extractDisplayFromPayload(String payload) {
        if (payload == null) return null;
        try {
            Pattern p = Pattern.compile("\"display\"\\s*:\\s*\"([^\"]*)\"");
            Matcher m = p.matcher(payload);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Build a shuffled schedule of difficulty levels matching the configured distribution.
     */
    private static List<Integer> buildDifficultySchedule(int totalQuestions) {
        if (totalQuestions <= 0) return Collections.emptyList();

        int easyCount = (int) Math.round(totalQuestions * PCT_EASY);
        int mediumCount = (int) Math.round(totalQuestions * PCT_MEDIUM);
        int hardCount = totalQuestions - easyCount - mediumCount;

        if (hardCount < 0) {
            int deficit = -hardCount;
            mediumCount = Math.max(0, mediumCount - deficit);
            hardCount = totalQuestions - easyCount - mediumCount;
        }

        int sum = easyCount + mediumCount + hardCount;
        if (sum != totalQuestions) {
            int diff = totalQuestions - sum;
            easyCount += diff;
        }

        List<Integer> schedule = new ArrayList<>(totalQuestions);
        for (int i = 0; i < easyCount; i++) schedule.add(DIFF_EASY);
        for (int i = 0; i < mediumCount; i++) schedule.add(DIFF_MEDIUM);
        for (int i = 0; i < hardCount; i++) schedule.add(DIFF_HARD);

//        Collections.shuffle(schedule, new Random(System.nanoTime()));
        System.out.println(schedule);
        return schedule;
    }

    // Add this method into the GameSession class
    private void broadcastAnswerResult(String usernameKey, String questionId, boolean correct, int pointsAwarded) {
        String scorePayload = scoreSnapshot();
        // payload format: questionId|usernameKey|CORRECT/WRONG|points|scoreSnapshot
        String payload = questionId + "|" + usernameKey + "|" + (correct ? "CORRECT" : "WRONG") + "|" + pointsAwarded + "|" + scorePayload;
        safeSend(p1, MessageType.ANSWER_RESULT, payload);
        safeSend(p2, MessageType.ANSWER_RESULT, payload);
    }

    // escape for minimal JSON strings
    private static String escapeForJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}