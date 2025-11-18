package com.mathspeed.server.service;

import com.mathspeed.network.ClientHandler;
import com.mathspeed.network.ClientRegistry;
import com.mathspeed.protocol.MessageType;

import java.util.Map;
import java.util.concurrent.*;

public class ChallengeManager {

    private final ClientRegistry clientRegistry;
    private final GameSessionManager sessionManager;

    private final ConcurrentMap<String, Map<String, Integer>> pendingChallenges = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final int DEFAULT_TIME_PER_ROUNDS = 40;

    public ChallengeManager(ClientRegistry clientRegistry, GameSessionManager sessionManager) {
        this.clientRegistry = clientRegistry;
        this.sessionManager = sessionManager;
    }

    public void sendChallenge(String challenger, String target, int totalRounds) {
        ClientHandler chHandler = clientRegistry.getClientHandler(challenger);
        ClientHandler targetHandler = clientRegistry.getClientHandler(target);

        if (chHandler == null) return;

        if (targetHandler == null) {
            chHandler.sendType(MessageType.CHALLENGE_FAILED, "Player not online");
            return;
        }

        if (isPlayerInGame(challenger) || isPlayerInGame(target)) {
            chHandler.sendType(MessageType.CHALLENGE_FAILED, "Player busy or you are busy");
            return;
        }

        pendingChallenges
                .computeIfAbsent(challenger, k -> new ConcurrentHashMap<>())
                .put(target, totalRounds);

        System.out.println("Challenge sent from " + challenger + " to " + target + " for " + totalRounds + " rounds.");

        targetHandler.sendType(MessageType.CHALLENGE_REQUEST, challenger + "|" + totalRounds);
        chHandler.sendType(MessageType.CHALLENGE_SENT, target + "|" + totalRounds);

        scheduler.schedule(() -> {
            Map<String, Integer> map = pendingChallenges.get(challenger);
            if (map != null && map.remove(target) != null) {
                if (map.isEmpty()) pendingChallenges.remove(challenger);
                ClientHandler c = clientRegistry.getClientHandler(challenger);
                if (c != null) c.sendType(MessageType.CHALLENGE_EXPIRED, target);
            }
        }, 30, TimeUnit.SECONDS);
    }

    public void acceptChallenge(String accepter, String challenger) {
        ClientHandler chHandler = clientRegistry.getClientHandler(challenger);
        ClientHandler accepterHandler = clientRegistry.getClientHandler(accepter);

        if (chHandler == null || accepterHandler == null) {
            if (accepterHandler != null)
                accepterHandler.sendType(MessageType.CHALLENGE_FAILED, "Other player offline");
            return;
        }

        Map<String, Integer> map = pendingChallenges.get(challenger);
        if (map == null || !map.containsKey(accepter)) {
            accepterHandler.sendType(MessageType.CHALLENGE_FAILED, "No pending challenge");
            return;
        }

        int totalRounds = map.remove(accepter);
        if (map.isEmpty()) pendingChallenges.remove(challenger);

        chHandler.sendType(MessageType.CHALLENGE_ACCEPTED, accepter + "|" + totalRounds);
        accepterHandler.sendType(MessageType.CHALLENGE_ACCEPTED, challenger + "|" + totalRounds);

        chHandler.sendType(MessageType.INFO, "Game starts in 5 seconds...");
        accepterHandler.sendType(MessageType.INFO, "Game starts in 5 seconds...");

        scheduler.schedule(() -> {
            GameSession session = sessionManager.createSessionSafely(
                    chHandler,
                    accepterHandler,
                    totalRounds,
                    DEFAULT_TIME_PER_ROUNDS
            );

            if (session == null) {
                chHandler.sendType(MessageType.CHALLENGE_FAILED, "Cannot start game");
                accepterHandler.sendType(MessageType.CHALLENGE_FAILED, "Cannot start game");
                return;
            }

            try {
                session.beginGame();
            } catch (Exception ex) {
                ex.printStackTrace();
                chHandler.sendType(MessageType.CHALLENGE_FAILED, "Failed to begin game");
                accepterHandler.sendType(MessageType.CHALLENGE_FAILED, "Failed to begin game");
            }

            System.out.println("Game session " + session.getSessionId() + " started between " +
                    chHandler.getUsername() + " and " + accepterHandler.getUsername());

        }, 2, TimeUnit.SECONDS);
    }


    public void declineChallenge(String challenger, String decliner) {
        ClientHandler chHandler = clientRegistry.getClientHandler(challenger);
        ClientHandler declinerHandler = clientRegistry.getClientHandler(decliner);

        Map<String, Integer> map = pendingChallenges.get(challenger);
        if (map != null) {
            map.remove(decliner);
            if (map.isEmpty()) pendingChallenges.remove(challenger);
        }

        if (chHandler != null) chHandler.sendType(MessageType.CHALLENGE_DECLINED, decliner);
        if (declinerHandler != null) declinerHandler.sendType(MessageType.CHALLENGE_DECLINED, challenger);

        System.out.println(decliner + " declined challenge from " + challenger);
    }

    private boolean isPlayerInGame(String username) {
        ClientHandler h = clientRegistry.getClientHandler(username);
        return h != null && h.getGameSession() != null;
    }

    public void shutdown() {
        scheduler.shutdownNow();
        pendingChallenges.clear();
    }
}