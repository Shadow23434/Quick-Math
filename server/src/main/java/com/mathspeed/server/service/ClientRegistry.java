package com.mathspeed.server.service;

import com.mathspeed.network.ClientHandler;
import com.mathspeed.dao.PlayerDAO;
import com.mathspeed.protocol.MessageType;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages online clients and broadcasts.
 *
 * Notes:
 * - This class no longer calls playerDAO.setAllUsersOffline() in the constructor because
 *   the persistence layer may not expose that method. If you need a startup reset,
 *   call the appropriate persistence API from the application bootstrap code.
 * - Uses ClientHandler API that exposes sendType(MessageType, String) / sendMessage(String) / disconnect().
 */
public class ClientRegistry {
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final PlayerDAO playerDAO;

    public ClientRegistry(PlayerDAO playerDAO) {
        this.playerDAO = playerDAO;
        // If you want to reset all users to offline on startup, do it from bootstrap code:
        // playerDAO.setAllUsersOffline();  // <-- only if implemented in persistence
    }

    /**
     * Register a client if username not taken.
     * Returns true if registration succeeded.
     */
    public boolean registerClient(String username, ClientHandler handler) {
        ClientHandler prev = clients.putIfAbsent(username, handler);
        return prev == null;
    }

    /**
     * Remove client and perform minimal cleanup.
     */
    public void removeClient(String username) {
        clients.remove(username);
        try {
            playerDAO.logout(username);
        } catch (Exception e) {
            // Persistence error shouldn't prevent removal; log for debugging
            System.err.println("PlayerDAO.logout failed for " + username + ": " + e.getMessage());
        }
    }

    public ClientHandler getClientHandler(String username) {
        return clients.get(username);
    }

    /**
     * Return a snapshot of online usernames.
     */
    public Set<String> getOnlineUsers() {
        return Collections.unmodifiableSet(clients.keySet());
    }

    /**
     * Broadcast a standardized player list update message to all connected clients.
     *
     * Uses MessageType.PLAYER_LIST_UPDATE with payload format:
     *   user:STATUS|user2:STATUS2|...
     * where STATUS is ONLINE or BUSY.
     */
    public void broadcastOnlinePlayers() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, ClientHandler> e : clients.entrySet()) {
            String u = e.getKey();
            String status = (e.getValue() != null && e.getValue().getCurrentGame() != null) ? "BUSY" : "ONLINE";
            if (!first) sb.append("|");
            sb.append(u).append(":").append(status);
            first = false;
        }
        String payload = sb.toString();

        for (ClientHandler ch : clients.values()) {
            try {
                // Prefer structured message type API
                ch.sendType(MessageType.PLAYER_LIST_UPDATE, payload);
            } catch (Exception ex) {
                // if sending fails, attempt to disconnect the client and remove it
                System.err.println("Failed to send PLAYER_LIST_UPDATE to a client: " + ex.getMessage());
                try {
                    ch.disconnect();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Shutdown: disconnect all clients and clear registry.
     */
    public void shutdown() {
        for (ClientHandler ch : clients.values()) {
            try { ch.disconnect(); } catch (Exception ignored) {}
        }
        clients.clear();
    }
}