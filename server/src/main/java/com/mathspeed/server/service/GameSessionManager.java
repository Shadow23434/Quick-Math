package com.mathspeed.server.service;

import com.mathspeed.dao.GameDAO;
import com.mathspeed.network.ClientHandler;
import com.mathspeed.puzzle.PuzzleQuestionProvider;
import com.mathspeed.puzzle.QuestionProvider;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages game sessions lifecycle and ensures safe creation (no races).
 *
 * Uses the extracted QuestionProvider interface. By default it uses PuzzleQuestionProvider
 * (wrapping MathPuzzleGenerator) with a configurable difficulty.
 *
 * This class creates GameSession instances and starts them. It ensures two players are not
 * in more than one game at the same time by taking per-username locks.
 */
public class GameSessionManager {
    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();
    private final ClientRegistry clientRegistry;
    private final Locks locks = new Locks();
    private final GameDAO gameDAO;

    // default settings (can be exposed via constructor or setters)
    private final int defaultQuestionCount = 10;
    private final long defaultQuestionTimeoutSec = 5L; // default 40s per question as requested
    private final int defaultPuzzleDifficulty = 1;

    public GameSessionManager(ClientRegistry clientRegistry, GameDAO gameDAO) {
        this.clientRegistry = clientRegistry;
        this.gameDAO = gameDAO;
    }

    /**
     * Create session using default provider (PuzzleQuestionProvider with default difficulty).
     */
    public void createSessionSafely(ClientHandler p1, ClientHandler p2) {
        PuzzleQuestionProvider provider = new PuzzleQuestionProvider();
        // pass the provider instance (not a method reference) to the overloaded method
        createSessionSafely(p1, p2, provider, defaultQuestionCount, defaultQuestionTimeoutSec);
    }

    /**
     * Create session and inject a specific provider / configuration.
     *
     * @param provider               implementation of QuestionProvider
     * @param totalQuestions         total number of questions for this session
     * @param questionTimeoutSeconds seconds allocated per question
     */
    public void createSessionSafely(ClientHandler p1,
                                    ClientHandler p2,
                                    QuestionProvider provider,
                                    int totalQuestions,
                                    long questionTimeoutSeconds) {
        if (p1 == null || p2 == null) return;
        String u1 = p1.getUsername();
        String u2 = p2.getUsername();
        if (u1 == null || u2 == null) return;
        if (u1.equals(u2)) return;

        // Acquire locks in a deterministic order to avoid deadlock
        Object l1 = locks.lockFor(u1);
        Object l2 = locks.lockFor(u2);
        Object first = l1, second = l2;
        if (System.identityHashCode(first) > System.identityHashCode(second)) {
            Object t = first;
            first = second;
            second = t;
        }

        synchronized (first) {
            synchronized (second) {
                // If either player already in a game, do nothing
                if (p1.getCurrentGame() != null || p2.getCurrentGame() != null) return;

                String gameId = UUID.randomUUID().toString();

                // If caller passed null provider, fall back to default puzzle provider
                QuestionProvider qp = provider;
                if (qp == null) {
                    PuzzleQuestionProvider defaultProv = new PuzzleQuestionProvider();
                    qp = defaultProv;
                }

                GameSession session = new GameSession(gameId, p1, p2, qp, totalQuestions, questionTimeoutSeconds, gameDAO);
                sessions.put(gameId, session);

                try {
                    p1.setCurrentGame(session);
                } catch (Exception ignored) {
                }
                try {
                    p2.setCurrentGame(session);
                } catch (Exception ignored) {
                }

                // Start the session
                session.start();

                // Notify players (redundant with session.start() but kept safe)
                try {
                    p1.sendType(com.mathspeed.protocol.MessageType.GAME_START, gameId + "|" + p2.getUsername());
                } catch (Exception e) {
                    try {
                        p1.sendMessage("GAME_START|" + gameId + "|" + p2.getUsername());
                    } catch (Exception ignored) {
                    }
                }
                try {
                    p2.sendType(com.mathspeed.protocol.MessageType.GAME_START, gameId + "|" + p1.getUsername());
                } catch (Exception e) {
                    try {
                        p2.sendMessage("GAME_START|" + gameId + "|" + p1.getUsername());
                    } catch (Exception ignored) {
                    }
                }

                // Broadcast updated online players list
                clientRegistry.broadcastOnlinePlayers();
            }
        }
    }

    public void endSession(String gameId) {
        GameSession s = sessions.remove(gameId);
        if (s != null) {
            try {
                s.end();
            } catch (Exception e) {
                System.err.println("Error ending session " + gameId + ": " + e.getMessage());
            }
            clientRegistry.broadcastOnlinePlayers();
        }
    }

    public void shutdown() {
        for (String id : sessions.keySet().toArray(new String[0])) {
            try {
                endSession(id);
            } catch (Exception e) {
                System.err.println("Error shutting down session " + id + ": " + e.getMessage());
            }
        }
        sessions.clear();
    }

    private static class Locks {
        private final ConcurrentHashMap<String, Object> map = new ConcurrentHashMap<>();

        public Object lockFor(String username) {
            return map.computeIfAbsent(username, k -> new Object());
        }
    }
}