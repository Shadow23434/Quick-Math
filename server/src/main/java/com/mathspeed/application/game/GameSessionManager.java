package com.mathspeed.application.game;

import com.mathspeed.domain.port.GameRepository;
import com.mathspeed.adapter.network.ClientHandler;
import com.mathspeed.adapter.network.ClientRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameSessionManager {
    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();
    private final ClientRegistry clientRegistry;
    private final Locks locks = new Locks();
    private final GameRepository GameRepository;

    public GameSessionManager(ClientRegistry clientRegistry, GameRepository GameRepository) {
        this.clientRegistry = clientRegistry;
        this.GameRepository = GameRepository;
    }

    public synchronized GameSession createSessionSafely(ClientHandler p1,
                                                        ClientHandler p2,
                                                        int totalRounds,
                                                        long questionTimeoutSeconds) {
        if (p1 == null || p2 == null) {
            System.err.println("KhÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ tÃƒÂ¡Ã‚ÂºÃ‚Â¡o session: mÃƒÂ¡Ã‚Â»Ã¢â€žÂ¢t trong hai player null");
            return null;
        }

        if (p1.getCurrentGame() != null) {
            System.err.println("Player " + p1.getUsername() + " Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ Ãƒâ€žÃ¢â‚¬Ëœang tham gia session khÃƒÆ’Ã‚Â¡c");
            return null;
        }
        if (p2.getCurrentGame() != null) {
            System.err.println("Player " + p2.getUsername() + " Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ Ãƒâ€žÃ¢â‚¬Ëœang tham gia session khÃƒÆ’Ã‚Â¡c");
            return null;
        }

        try {
            GameSession session = new GameSession(p1, p2, totalRounds, questionTimeoutSeconds, this.GameRepository);
            p1.setCurrentGame(session);
            p2.setCurrentGame(session);

            if (this.sessions != null) {
                this.sessions.put(session.getSessionId(), session);
            }

            return session;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * KÃƒÂ¡Ã‚ÂºÃ‚Â¿t thÃƒÆ’Ã‚Âºc session theo id
     */
    public void endSession(String gameId) {
        GameSession session = sessions.remove(gameId);
        if (session != null) {
            System.out.println("Ending session: " + gameId);
            try {
                session.finishGame();
            } catch (Exception e) {
                System.err.println("Error finishing game " + gameId + ": " + e.getMessage());
            }

            // XoÃƒÆ’Ã‚Â¡ session khÃƒÂ¡Ã‚Â»Ã‚Âi 2 ngÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âi chÃƒâ€ Ã‚Â¡i
            session.getPlayerA().clearCurrentGame();
            session.getPlayerB().clearCurrentGame();
        }
    }

    /**
     * KiÃƒÂ¡Ã‚Â»Ã†â€™m tra xem ngÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âi chÃƒâ€ Ã‚Â¡i cÃƒÆ’Ã‚Â³ Ãƒâ€žÃ¢â‚¬Ëœang ÃƒÂ¡Ã‚Â»Ã…Â¸ trong session nÃƒÆ’Ã‚Â o khÃƒÆ’Ã‚Â´ng
     */
    private boolean isPlayerInSession(ClientHandler player) {
        for (GameSession s : sessions.values()) {
            if (s.getPlayerA() == player || s.getPlayerB() == player) return true;
        }
        return false;
    }

    /**
     * DÃƒÂ¡Ã‚Â»Ã‚Â«ng toÃƒÆ’Ã‚Â n bÃƒÂ¡Ã‚Â»Ã¢â€žÂ¢ session khi server shutdown
     */
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

    // LÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºp lock Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã†â€™ trÃƒÆ’Ã‚Â¡nh viÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡c 2 thread cÃƒÆ’Ã‚Â¹ng tÃƒÂ¡Ã‚ÂºÃ‚Â¡o session cho 1 user
    private static class Locks {
        private final ConcurrentHashMap<String, Object> map = new ConcurrentHashMap<>();

        public Object lockFor(String username) {
            return map.computeIfAbsent(username, k -> new Object());
        }
    }

    /**
     * Helper: TÃƒÂ¡Ã‚ÂºÃ‚Â¡o JSON Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â¡n giÃƒÂ¡Ã‚ÂºÃ‚Â£n Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã†â€™ gÃƒÂ¡Ã‚Â»Ã‚Â­i cho client
     */
    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            sb.append("\"").append(e.getKey()).append("\":");
            Object val = e.getValue();
            if (val instanceof Number || val instanceof Boolean) {
                sb.append(val);
            } else {
                sb.append("\"").append(val).append("\"");
            }
            if (++i < map.size()) sb.append(",");
        }
        sb.append("}");
        return sb.toString();
    }
}

