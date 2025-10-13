package org.example.server;

import java.util.*;
import java.util.concurrent.*;

public class GameSession {
    private final ClientHandler player1;
    private final ClientHandler player2;
    private final Map<String, Integer> scores;
    private final Map<String, Long> times;
    private final int totalRounds = 5;
    private int currentRound;
    private final ScheduledExecutorService scheduler;
    private static final int ROUND_TIME_LIMIT = 20; // seconds
    private static final int COUNTDOWN_SECONDS = 5; // waiting room countdown
    private boolean gameStarted = false;
    private List<MathPuzzle> questions; // Pre-generated questions using MathPuzzle
    private MathPuzzleGenerator puzzleGenerator; // Puzzle generator
    private int difficultyLevel = 1; // Default difficulty level (1-3)
    private MathExpressionEvaluator expressionEvaluator = new MathExpressionEvaluator();

    // Variables for tracking player submissions
    private Map<String, String> playerAnswers;
    private Map<String, Long> playerSubmissionTimes;
    private Map<String, Boolean> correctAnswers; // Track correct/incorrect for current round
    private Set<String> submittedPlayers;
    private long roundStartTime;
    private ScheduledFuture<?> roundTimer;

    public GameSession(ClientHandler player1, ClientHandler player2) {
        this.player1 = player1;
        this.player2 = player2;
        this.scores = new ConcurrentHashMap<>();
        this.times = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.questions = new ArrayList<>();
        this.puzzleGenerator = new MathPuzzleGenerator(difficultyLevel);

        // Initialize submission tracking
        this.playerAnswers = new ConcurrentHashMap<>();
        this.playerSubmissionTimes = new ConcurrentHashMap<>();
        this.correctAnswers = new ConcurrentHashMap<>();
        this.submittedPlayers = ConcurrentHashMap.newKeySet();

        scores.put(player1.getUsername(), 0);
        scores.put(player2.getUsername(), 0);
        times.put(player1.getUsername(), 0L);
        times.put(player2.getUsername(), 0L);

        // Start waiting room countdown
        startWaitingRoom();
    }

    private void startWaitingRoom() {
        // Notify both players they're in waiting room
        player1.sendMessage("WAITING_ROOM|" + COUNTDOWN_SECONDS);
        player2.sendMessage("WAITING_ROOM|" + COUNTDOWN_SECONDS);

        // Generate all questions during countdown
        generateAllQuestions();

        // Start countdown
        startCountdown();
    }

    private void generateAllQuestions() {
        questions.clear();

        for (int i = 0; i < totalRounds; i++) {
            // Tăng dần độ khó qua các vòng nếu muốn
            if (i >= totalRounds / 2) {
                puzzleGenerator.setDifficultyLevel(Math.min(difficultyLevel + 1, 3));
            }

            MathPuzzle puzzle = puzzleGenerator.generatePuzzle();
            questions.add(puzzle);
        }

        System.out.println("Generated " + totalRounds + " math puzzles for game between " +
                player1.getUsername() + " and " + player2.getUsername());
    }

    private void startCountdown() {
        for (int i = COUNTDOWN_SECONDS; i > 0; i--) {
            final int countdown = i;
            scheduler.schedule(() -> {
                player1.sendMessage("COUNTDOWN|" + countdown);
                player2.sendMessage("COUNTDOWN|" + countdown);
            }, (COUNTDOWN_SECONDS - i), TimeUnit.SECONDS);
        }

        // Start game after countdown
        scheduler.schedule(() -> {
            gameStarted = true;
            player1.sendMessage("GAME_READY");
            player2.sendMessage("GAME_READY");
            startNewRound();
        }, COUNTDOWN_SECONDS, TimeUnit.SECONDS);
    }

    private void startNewRound() {
        if (!gameStarted) return;

        if (currentRound >= totalRounds) {
            endGame();
            return;
        }

        // Clear previous round data
        playerAnswers.clear();
        playerSubmissionTimes.clear();
        submittedPlayers.clear();
        correctAnswers.clear();

        // Use pre-generated question
        MathPuzzle puzzle = questions.get(currentRound);
        String targetDisplay = puzzle.getTargetDisplay(); // Lấy target hiển thị (có thể là phân số/số thập phân)
        List<Integer> numberSet = puzzle.getNumbers();
        currentRound++;

        // Log question details for debugging
        System.out.println("Round " + currentRound + ": Target=" + targetDisplay +
                ", Numbers=" + numberSet);

        // Gửi cả target hiển thị và bộ số
        String gameState = String.format("ROUND_START|%d|%s|%s",
                currentRound, targetDisplay, numberSet.toString().replaceAll("[\\[\\]]", ""));

        player1.sendMessage(gameState);
        player2.sendMessage(gameState);

        // Record round start time
        roundStartTime = System.currentTimeMillis();

        // Set timer for round
        if (roundTimer != null && !roundTimer.isDone()) {
            roundTimer.cancel(false);
        }
        roundTimer = scheduler.schedule(this::timeoutRound, ROUND_TIME_LIMIT, TimeUnit.SECONDS);
    }

    public void submitAnswer(ClientHandler player, String answer) {
        if (!gameStarted) return;

        String username = player.getUsername();

        // Only accept first submission from each player
        if (submittedPlayers.contains(username)) {
            player.sendMessage("ALREADY_SUBMITTED");
            return;
        }

        // Record answer and submission time
        long submissionTime = System.currentTimeMillis() - roundStartTime;
        playerAnswers.put(username, answer);
        playerSubmissionTimes.put(username, submissionTime);
        submittedPlayers.add(username);

        System.out.println(username + " submitted answer: " + answer + " in " +
                (submissionTime/1000.0) + " seconds");

        // Acknowledge submission without revealing result yet
        player.sendMessage("ANSWER_SUBMITTED|" + submissionTime / 1000.0); // Send time in seconds

        // Check if both players have submitted or time is almost up
        if (submittedPlayers.size() == 2) {
            // Cancel the round timer if still active
            if (roundTimer != null && !roundTimer.isDone()) {
                roundTimer.cancel(false);
                evaluateRound();
            }
        } else if (submittedPlayers.size() == 1 &&
                System.currentTimeMillis() - roundStartTime >= (ROUND_TIME_LIMIT - 1) * 1000) {
            // If one player submitted and less than 1 second left, end round early
            if (roundTimer != null && !roundTimer.isDone()) {
                roundTimer.cancel(false);
                evaluateRound();
            }
        }
    }

    private void timeoutRound() {
        if (!gameStarted) return;

        System.out.println("Round " + currentRound + " timed out");

        // Evaluate answers if time is up
        evaluateRound();
    }

    private void evaluateRound() {
        if (currentRound <= 0 || currentRound > questions.size()) {
            return;
        }

        MathPuzzle currentPuzzle = questions.get(currentRound - 1);

        // Get player usernames
        String player1Name = player1.getUsername();
        String player2Name = player2.getUsername();

        // Store previous scores to detect changes
        int previousScore1 = scores.get(player1Name);
        int previousScore2 = scores.get(player2Name);

        // Check player 1's answer
        evaluatePlayerAnswer(player1Name, currentPuzzle);

        // Check player 2's answer
        evaluatePlayerAnswer(player2Name, currentPuzzle);

        // Send round results to both players
        sendRoundResults(previousScore1, previousScore2);

        // Schedule next round
        scheduler.schedule(this::startNewRound, 3, TimeUnit.SECONDS);
    }

    private void evaluatePlayerAnswer(String playerName, MathPuzzle puzzle) {
        // Get player's answer and submission time
        String answer = playerAnswers.getOrDefault(playerName, "");
        long submissionTime = playerSubmissionTimes.getOrDefault(playerName, Long.MAX_VALUE);

        // Check if player submitted within time limit
        boolean isWithinTimeLimit = submissionTime <= ROUND_TIME_LIMIT * 1000;

        // Evaluate answer
        boolean isCorrect = false;
        if (isWithinTimeLimit && !answer.isEmpty()) {
            // Sử dụng MathExpressionEvaluator để đánh giá biểu thức
            isCorrect = expressionEvaluator.isExpressionValid(
                    answer, puzzle.getNumbers(), puzzle.getTarget());

            // Store whether this answer is correct
            correctAnswers.put(playerName, isCorrect);

            // Update score if correct
            if (isCorrect) {
                scores.put(playerName, scores.get(playerName) + 1);
                System.out.println(playerName + " answered correctly: " + answer);
            } else {
                System.out.println(playerName + " answered incorrectly: " + answer);
            }
        } else {
            // No submission or too late
            correctAnswers.put(playerName, false);
            System.out.println(playerName + " did not submit a valid answer");
        }

        // Update total time if player submitted
        if (submissionTime != Long.MAX_VALUE) {
            times.put(playerName, times.get(playerName) + submissionTime);
        }
    }

    private void sendRoundResults(int previousScore1, int previousScore2) {
        // Get player usernames
        String player1Name = player1.getUsername();
        String player2Name = player2.getUsername();

        // Get current scores
        int currentScore1 = scores.get(player1Name);
        int currentScore2 = scores.get(player2Name);

        // Determine if players answered correctly this round
        boolean player1Correct = correctAnswers.getOrDefault(player1Name, false);
        boolean player2Correct = correctAnswers.getOrDefault(player2Name, false);

        String player1Answer = playerAnswers.getOrDefault(player1Name, "No answer");
        String player2Answer = playerAnswers.getOrDefault(player2Name, "No answer");

        // Format: ROUND_RESULT|correct/incorrect|your_score|opponent_score|your_answer|opponent_answer|your_time|opponent_time

        // Get submission times (or max if not submitted)
        double player1Time = playerSubmissionTimes.getOrDefault(player1Name, Long.MAX_VALUE) / 1000.0;
        double player2Time = playerSubmissionTimes.getOrDefault(player2Name, Long.MAX_VALUE) / 1000.0;

        String player1TimeStr = player1Time == ROUND_TIME_LIMIT ? "timeout" : String.format("%.2f", player1Time);
        String player2TimeStr = player2Time == ROUND_TIME_LIMIT ? "timeout" : String.format("%.2f", player2Time);

        // Send result to player 1
        String resultPlayer1 = String.format("ROUND_RESULT|%s|%d|%d|%s|%s|%s|%s",
                player1Correct ? "correct" : "incorrect",
                currentScore1,
                currentScore2,
                player1Answer,
                player2Answer,
                player1TimeStr,
                player2TimeStr);
        player1.sendMessage(resultPlayer1);

        // Send result to player 2
        String resultPlayer2 = String.format("ROUND_RESULT|%s|%d|%d|%s|%s|%s|%s",
                player2Correct ? "correct" : "incorrect",
                currentScore2,
                currentScore1,
                player2Answer,
                player1Answer,
                player2TimeStr,
                player1TimeStr);
        player2.sendMessage(resultPlayer2);
    }

    public void playerQuit(ClientHandler player) {
        if (!gameStarted) return;

        ClientHandler winner = (player == player1) ? player2 : player1;
        String quitter = player.getUsername();
        String winnerName = winner.getUsername();

        System.out.println(quitter + " quit the game. " + winnerName + " wins by default.");
        winner.sendMessage("OPPONENT_QUIT|YOU_WIN");
        endGame();
    }

    public String getOpponent(String playerUsername) {
        if (player1.getUsername().equals(playerUsername)) {
            return player2.getUsername();
        } else if (player2.getUsername().equals(playerUsername)) {
            return player1.getUsername();
        }
        return null;
    }

    public void endGame() {
        gameStarted = false;
        if (roundTimer != null && !roundTimer.isDone()) {
            roundTimer.cancel(false);
        }
        scheduler.shutdownNow(); // Use shutdownNow to cancel all pending tasks

        // Determine winner
        String player1Name = player1.getUsername();
        String player2Name = player2.getUsername();
        int player1Score = scores.get(player1Name);
        int player2Score = scores.get(player2Name);
        long player1Time = times.get(player1Name);
        long player2Time = times.get(player2Name);

        System.out.println("Game ended: " + player1Name + " (" + player1Score + " points, " +
                player1Time/1000.0 + "s) vs " + player2Name + " (" + player2Score +
                " points, " + player2Time/1000.0 + "s)");

        String result;
        if (player1Score > player2Score) {
            result = String.format("GAME_END|WIN|%d|%d|%.2f|%.2f",
                    player1Score, player2Score, player1Time/1000.0, player2Time/1000.0);
            player1.sendMessage(result);
            result = String.format("GAME_END|LOSE|%d|%d|%.2f|%.2f",
                    player2Score, player1Score, player2Time/1000.0, player1Time/1000.0);
            player2.sendMessage(result);
        } else if (player2Score > player1Score) {
            result = String.format("GAME_END|WIN|%d|%d|%.2f|%.2f",
                    player2Score, player1Score, player2Time/1000.0, player1Time/1000.0);
            player2.sendMessage(result);
            result = String.format("GAME_END|LOSE|%d|%d|%.2f|%.2f",
                    player1Score, player2Score, player1Time/1000.0, player2Time/1000.0);
            player1.sendMessage(result);
        } else {
            // Scores are tied, use time to determine winner
            if (player1Time < player2Time) {
                result = String.format("GAME_END|WIN_TIME|%d|%d|%.2f|%.2f",
                        player1Score, player2Score, player1Time/1000.0, player2Time/1000.0);
                player1.sendMessage(result);
                result = String.format("GAME_END|LOSE_TIME|%d|%d|%.2f|%.2f",
                        player2Score, player1Score, player2Time/1000.0, player1Time/1000.0);
                player2.sendMessage(result);
            } else if (player2Time < player1Time) {
                result = String.format("GAME_END|WIN_TIME|%d|%d|%.2f|%.2f",
                        player2Score, player1Score, player2Time/1000.0, player1Time/1000.0);
                player2.sendMessage(result);
                result = String.format("GAME_END|LOSE_TIME|%d|%d|%.2f|%.2f",
                        player1Score, player2Score, player1Time/1000.0, player2Time/1000.0);
                player1.sendMessage(result);
            } else {
                // Even times are tied (very unlikely)
                result = String.format("GAME_END|DRAW|%d|%d|%.2f|%.2f",
                        player1Score, player2Score, player1Time/1000.0, player2Time/1000.0);
                player1.sendMessage(result);
                player2.sendMessage(result);
            }
        }

        // Clear current game for both players
        player1.setCurrentGame(null);
        player2.setCurrentGame(null);

        System.out.println("Game completed between " + player1Name + " and " + player2Name);
    }

    // Thay đổi độ khó của game
    public void setDifficultyLevel(int level) {
        if (level >= 1 && level <= 3) {
            this.difficultyLevel = level;
            this.puzzleGenerator.setDifficultyLevel(level);
            System.out.println("Difficulty set to level " + level);
        }
    }
}