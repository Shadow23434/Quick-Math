package com.mathspeed.application.game;


import com.mathspeed.adapter.network.ClientHandler;
import com.mathspeed.adapter.network.ClientRegistry;
import com.mathspeed.adapter.network.protocol.MessageType;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

public class ChallengeManager {

    private static final class PendingChallenge {
        final String challengerRaw; // original challenger string (preserve case for messages)
        final String challengerKey; // normalized key
        final int rounds;
        final long createdMs;
        final ScheduledFuture<?> expiryFuture;

        PendingChallenge(String challengerRaw, String challengerKey, int rounds, long createdMs, ScheduledFuture<?> expiryFuture) {
            this.challengerRaw = challengerRaw;
            this.challengerKey = challengerKey;
            this.rounds = rounds;
            this.createdMs = createdMs;
            this.expiryFuture = expiryFuture;
        }
    }

    private final ClientRegistry clientRegistry;
    private final GameSessionManager sessionManager;

    // pending keyed by normalized target username
    private final ConcurrentMap<String, PendingChallenge> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ChallengeManager-Scheduler");
        t.setDaemon(true);
        return t;
    });

    private final int DEFAULT_TIME_PER_ROUNDS = 30;
    private final long PENDING_EXPIRY_SECONDS = 40L;

    public ChallengeManager(ClientRegistry clientRegistry, GameSessionManager sessionManager) {
        this.clientRegistry = Objects.requireNonNull(clientRegistry);
        this.sessionManager = Objects.requireNonNull(sessionManager);
    }

    /**
     * Send a challenge from challenger -> target for totalRounds rounds.
     * Notifies challenger on failure (offline/busy/target already has pending).
     */
    public void sendChallenge(String challenger, String target, int totalRounds) {
        if (challenger == null || target == null) return;
        String challengerKey = norm(challenger);
        String targetKey = norm(target);

        ClientHandler challengerHandler = resolveHandler(challenger);
        if (challengerHandler == null) {
            System.out.println("[ChallengeManager] sendChallenge: challenger not connected: " + challenger);
            return;
        }

        ClientHandler targetHandler = resolveHandler(target);
        if (targetHandler == null) {
            challengerHandler.sendType(MessageType.CHALLENGE_FAILED, "Player not online");
            System.out.println("[ChallengeManager] sendChallenge: target offline: " + target);
            return;
        }

        // busy checks
        if (isPlayerInGame(challengerKey) || isPlayerInGame(targetKey)) {
            challengerHandler.sendType(MessageType.CHALLENGE_FAILED, "Player busy or you are busy");
            System.out.println("[ChallengeManager] sendChallenge: busy challenger=" + challenger + " target=" + target);
            return;
        }

        // If target already has pending challenge, reject new challenge (prevent spam)
        PendingChallenge existing = pending.get(targetKey);
        if (existing != null) {
            challengerHandler.sendType(MessageType.CHALLENGE_FAILED, "Target has pending challenge");
            System.out.println("[ChallengeManager] sendChallenge: target already has pending challenge: " + target);
            return;
        }

        // schedule expiry
        ScheduledFuture<?> expiry = scheduler.schedule(() -> expirePending(targetKey), PENDING_EXPIRY_SECONDS, TimeUnit.SECONDS);

        PendingChallenge p = new PendingChallenge(challenger, challengerKey, totalRounds, System.currentTimeMillis(), expiry);
        pending.put(targetKey, p);

        // send notifications
        try {
            targetHandler.sendType(MessageType.CHALLENGE_REQUEST, challenger + "|" + totalRounds);
            challengerHandler.sendType(MessageType.CHALLENGE_SENT, target + "|" + totalRounds);
            System.out.println("[ChallengeManager] Challenge sent from " + challenger + " to " + target + " rounds=" + totalRounds);
        } catch (Exception ex) {
            // cleanup on failure
            PendingChallenge removed = pending.remove(targetKey);
            if (removed != null && removed.expiryFuture != null) removed.expiryFuture.cancel(false);
            ex.printStackTrace();
            try {
                challengerHandler.sendType(MessageType.CHALLENGE_FAILED, "Failed to deliver challenge");
            } catch (Exception ignored) {}
        }
    }

    public void acceptChallenge(String acceptor, String challenger) {
        if (acceptor == null || challenger == null) return;
        String acceptorKey = norm(acceptor);
        String challengerKey = norm(challenger);

        ClientHandler acceptorHandler = resolveHandler(acceptor);
        ClientHandler challengerHandler = resolveHandler(challenger);

        if (acceptorHandler == null) {
            System.out.println("[ChallengeManager] acceptChallenge: acceptor not connected: " + acceptor);
            return;
        }

        PendingChallenge p = pending.get(acceptorKey);
        if (p == null || !p.challengerKey.equals(challengerKey)) {
            acceptorHandler.sendType(MessageType.CHALLENGE_FAILED, "No pending challenge");
            System.out.println("[ChallengeManager] acceptChallenge: no pending for acceptor=" + acceptor + " from=" + challenger);
            return;
        }

        // cancel expiry and remove pending
        pending.remove(acceptorKey);
        if (p.expiryFuture != null) p.expiryFuture.cancel(false);

        if (challengerHandler == null) {
            acceptorHandler.sendType(MessageType.CHALLENGE_FAILED, "Other player offline");
            System.out.println("[ChallengeManager] acceptChallenge: challenger not connected: " + challenger);
            return;
        }

        // Notify both parties
        challengerHandler.sendType(MessageType.CHALLENGE_ACCEPTED, acceptor + "|" + p.rounds);
        acceptorHandler.sendType(MessageType.CHALLENGE_ACCEPTED, challenger + "|" + p.rounds);

        challengerHandler.sendType(MessageType.INFO, "Game starts in 5 seconds...");
        acceptorHandler.sendType(MessageType.INFO, "Game starts in 5 seconds...");

        // Create session shortly after to give clients brief moment
        scheduler.schedule(() -> {
            try {
                GameSession session = sessionManager.createSessionSafely(
                        challengerHandler,
                        acceptorHandler,
                        p.rounds,
                        DEFAULT_TIME_PER_ROUNDS
                );
                if (session == null) {
                    challengerHandler.sendType(MessageType.CHALLENGE_FAILED, "Cannot start game");
                    acceptorHandler.sendType(MessageType.CHALLENGE_FAILED, "Cannot start game");
                    System.out.println("[ChallengeManager] acceptChallenge: createSessionSafely returned null for " + challenger + " vs " + acceptor);
                    return;
                }

                session.beginGame();
                System.out.println("[ChallengeManager] Game session " + session.getSessionId() + " started between " +
                        challengerHandler.getUsername() + " and " + acceptorHandler.getUsername());
            } catch (Exception ex) {
                ex.printStackTrace();
                try {
                    challengerHandler.sendType(MessageType.CHALLENGE_FAILED, "Failed to begin game");
                    acceptorHandler.sendType(MessageType.CHALLENGE_FAILED, "Failed to begin game");
                } catch (Exception ignored) {}
            }
        }, 2, TimeUnit.SECONDS);
    }

    /**
     * Attempt to accept the pending challenge addressed to acceptorUsername.
     * If found, accept it (calls acceptChallenge) and return true; else return false.
     */
    public boolean acceptPendingFor(String acceptorUsername) {
        if (acceptorUsername == null) return false;
        String acceptorKey = norm(acceptorUsername);

        PendingChallenge p = pending.get(acceptorKey);
        if (p == null) {
            System.out.println("[ChallengeManager] acceptPendingFor: no pending for " + acceptorUsername);
            return false;
        }

        try {
            acceptChallenge(acceptorUsername, p.challengerRaw);
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    /**
     * Decline a pending challenge (decliner declines challenger's request).
     */
    public void declineChallenge(String challenger, String decliner) {
        if (challenger == null || decliner == null) return;
        String declinerKey = norm(decliner);

        PendingChallenge p = pending.remove(declinerKey);
        if (p != null && p.expiryFuture != null) p.expiryFuture.cancel(false);

        ClientHandler challengerHandler = resolveHandler(challenger);
        ClientHandler declinerHandler = resolveHandler(decliner);

        if (challengerHandler != null) challengerHandler.sendType(MessageType.CHALLENGE_DECLINED, decliner);
        if (declinerHandler != null) declinerHandler.sendType(MessageType.CHALLENGE_DECLINED, challenger);

        System.out.println("[ChallengeManager] " + decliner + " declined challenge from " + challenger);
    }

    private void expirePending(String targetKey) {
        PendingChallenge p = pending.remove(targetKey);
        if (p == null) return;
        ClientHandler challengerHandler = resolveHandler(p.challengerRaw);
        if (challengerHandler != null) {
            try {
                challengerHandler.sendType(MessageType.CHALLENGE_EXPIRED, targetKey);
            } catch (Exception ignored) {}
        }
        System.out.println("[ChallengeManager] pending challenge expired for targetKey=" + targetKey + " challenger=" + p.challengerRaw);
    }

    private boolean isPlayerInGame(String username) {
        ClientHandler h = resolveHandler(username);
        return h != null && h.getCurrentGame() != null;
    }

    private ClientHandler resolveHandler(String username) {
        if (username == null) return null;
        // try direct (some registries use original-case keys)
        try {
            ClientHandler ch = clientRegistry.getClientHandler(username);
            if (ch != null) return ch;
        } catch (Exception ignored) {}

        // normalized lookup
        try {
            ClientHandler ch = clientRegistry.getClient(username);
            if (ch != null) return ch;
        } catch (Exception ignored) {}

        // scan online users for case-insensitive match
        try {
            for (String online : clientRegistry.getOnlineUsers()) {
                if (online != null && online.equalsIgnoreCase(username)) {
                    ClientHandler found = clientRegistry.getClientHandler(online);
                    if (found != null) return found;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String norm(String s) {
        return s == null ? null : s.trim().toLowerCase(Locale.ROOT);
    }

    public void shutdown() {
        try {
            scheduler.shutdownNow();
        } catch (Exception ignored) {}
        pending.clear();
    }
}