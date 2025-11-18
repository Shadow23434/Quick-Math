package com.mathspeed.server.service;

import com.mathspeed.dao.GameDAO;
import com.mathspeed.network.ClientHandler;
import com.mathspeed.network.ClientRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameSessionManager {
    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();
    private final ClientRegistry clientRegistry;
    private final Locks locks = new Locks();
    private final GameDAO gameDAO;

    public GameSessionManager(ClientRegistry clientRegistry, GameDAO gameDAO) {
        this.clientRegistry = clientRegistry;
        this.gameDAO = gameDAO;
    }

    public synchronized GameSession createSessionSafely(ClientHandler p1,
                                                        ClientHandler p2,
                                                        int totalRounds,
                                                        long questionTimeoutSeconds) {
        if (p1 == null || p2 == null) {
            System.err.println("Không thể tạo session: một trong hai player null");
            return null;
        }

        if (p1.getCurrentGame() != null) {
            System.err.println("Player " + p1.getUsername() + " đã đang tham gia session khác");
            return null;
        }
        if (p2.getCurrentGame() != null) {
            System.err.println("Player " + p2.getUsername() + " đã đang tham gia session khác");
            return null;
        }

        try {
            GameSession session = new GameSession(p1, p2, totalRounds, questionTimeoutSeconds, this.gameDAO);
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
     * Kết thúc session theo id
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

            // Xoá session khỏi 2 người chơi
            session.getPlayerA().clearCurrentGame();
            session.getPlayerB().clearCurrentGame();
        }
    }

    /**
     * Kiểm tra xem người chơi có đang ở trong session nào không
     */
    private boolean isPlayerInSession(ClientHandler player) {
        for (GameSession s : sessions.values()) {
            if (s.getPlayerA() == player || s.getPlayerB() == player) return true;
        }
        return false;
    }

    /**
     * Dừng toàn bộ session khi server shutdown
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

    // Lớp lock để tránh việc 2 thread cùng tạo session cho 1 user
    private static class Locks {
        private final ConcurrentHashMap<String, Object> map = new ConcurrentHashMap<>();

        public Object lockFor(String username) {
            return map.computeIfAbsent(username, k -> new Object());
        }
    }

    /**
     * Helper: Tạo JSON đơn giản để gửi cho client
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
