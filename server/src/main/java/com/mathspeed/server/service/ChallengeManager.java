package com.mathspeed.server.service;

import com.mathspeed.network.ClientHandler;

import java.util.Set;
import java.util.concurrent.*;

/**
 * Manages one-to-one challenge flow with pending sets and expiration.
 */
public class ChallengeManager {
    private final ClientRegistry clientRegistry;
    private final GameSessionManager sessionManager;
    private final ConcurrentMap<String, Set<String>> pendingChallenges = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public ChallengeManager(ClientRegistry clientRegistry, GameSessionManager sessionManager) {
        this.clientRegistry = clientRegistry;
        this.sessionManager = sessionManager;
    }

    public void sendChallenge(String challenger, String target) {
        ClientHandler chHandler = clientRegistry.getClientHandler(challenger);
        ClientHandler targetHandler = clientRegistry.getClientHandler(target);
        if (chHandler == null) return;
        if (targetHandler == null) {
            chHandler.sendMessage("CHALLENGE_FAILED|Player not online");
            return;
        }
        if (isPlayerInGame(target) || isPlayerInGame(challenger)) {
            chHandler.sendMessage("CHALLENGE_FAILED|Player busy or you are busy");
            return;
        }

        pendingChallenges.computeIfAbsent(challenger, k -> ConcurrentHashMap.newKeySet()).add(target);
        targetHandler.sendMessage("CHALLENGE_RECEIVED|" + challenger);
        chHandler.sendMessage("CHALLENGE_SENT|" + target);

        // expiration
        scheduler.schedule(() -> {
            Set<String> set = pendingChallenges.get(challenger);
            if (set != null && set.remove(target)) {
                ClientHandler c = clientRegistry.getClientHandler(challenger);
                if (c != null) c.sendMessage("CHALLENGE_EXPIRED|" + target);
            }
        }, 30, TimeUnit.SECONDS);
    }

    public void acceptChallenge(String accepter, String challenger) {
        ClientHandler chHandler = clientRegistry.getClientHandler(challenger);
        ClientHandler accepterHandler = clientRegistry.getClientHandler(accepter);
        if (chHandler == null || accepterHandler == null) {
            if (accepterHandler != null) accepterHandler.sendMessage("CHALLENGE_FAILED|Other player offline");
            return;
        }

        Set<String> set = pendingChallenges.get(challenger);
        if (set == null || !set.contains(accepter)) {
            accepterHandler.sendMessage("CHALLENGE_FAILED|No pending challenge");
            return;
        }
        set.remove(accepter);
        if (set.isEmpty()) pendingChallenges.remove(challenger);

        chHandler.sendMessage("CHALLENGE_ACCEPTED|" + accepter);
        accepterHandler.sendMessage("CHALLENGE_ACCEPTED|" + challenger);

        // Start game after 5s
        scheduler.schedule(() -> sessionManager.createSessionSafely(chHandler, accepterHandler), 5, TimeUnit.SECONDS);
    }

    public void declineChallenge(String challenger, String decliner) {
        ClientHandler chHandler = clientRegistry.getClientHandler(challenger);
        ClientHandler declinerHandler = clientRegistry.getClientHandler(decliner);
        Set<String> set = pendingChallenges.get(challenger);
        if (set != null) {
            set.remove(decliner);
            if (set.isEmpty()) pendingChallenges.remove(challenger);
        }
        if (chHandler != null) chHandler.sendMessage("CHALLENGE_DECLINED|" + decliner);
        if (declinerHandler != null) declinerHandler.sendMessage("CHALLENGE_DECLINED|" + challenger);
    }

    private boolean isPlayerInGame(String username) {
        ClientHandler h = clientRegistry.getClientHandler(username);
        return h != null && h.getCurrentGame() != null;
    }

    public void shutdown() {
        scheduler.shutdownNow();
        pendingChallenges.clear();
    }
}
