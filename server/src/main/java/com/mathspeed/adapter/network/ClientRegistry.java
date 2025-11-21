package com.mathspeed.adapter.network;

import com.mathspeed.domain.port.PlayerRepository;
import com.mathspeed.adapter.network.protocol.MessageType;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientRegistry {

    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final PlayerRepository PlayerRepository;

    // Heartbeat timeout (ms)
    private static final long HEARTBEAT_TIMEOUT = 180_000; // 3 phÃºt

    public ClientRegistry(PlayerRepository PlayerRepository) {
        this.PlayerRepository = PlayerRepository;
        Thread heartbeatThread = new Thread(this::heartbeatChecker, "ClientRegistry-Heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    public boolean registerClient(String username, ClientHandler handler) {
        ClientHandler prev = clients.putIfAbsent(username, handler);
        return prev == null;
    }

    public void removeClient(String username) {
        if (username == null) return;
        ClientHandler ch = clients.remove(username);
        if (ch != null) {
            ch.disconnect();
        }
    }

    // Lookup client báº±ng lowercase
    public ClientHandler getClientHandler(String username) {
        if (username == null) return null;
        return clients.get(username);
    }

    public Set<String> getOnlineUsers() {
        return Collections.unmodifiableSet(clients.keySet());
    }

    public void broadcastOnlinePlayers() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, ClientHandler> e : clients.entrySet()) {
            String u = e.getKey();
            String status = "ONLINE";
            ClientHandler ch = e.getValue();
            if (ch == null) continue;

            if (!ch.isAlive(HEARTBEAT_TIMEOUT)) {
                status = "OFFLINE";
            } else if (ch.getCurrentGame() != null) {
                status = "BUSY";
            }

            if (!first) sb.append("|");
            sb.append(u).append(":").append(status);
            first = false;
        }

        String payload = sb.toString();

        for (ClientHandler ch : clients.values()) {
            if (ch != null && ch.isAlive(HEARTBEAT_TIMEOUT)) {
                try { ch.sendType(MessageType.PLAYER_LIST_UPDATE, payload); }
                catch (Exception ex) {
                    System.err.println("Failed to send PLAYER_LIST_UPDATE to " + ch.getUsername() + ": " + ex.getMessage());
                }
            }
        }
    }

    private void heartbeatChecker() {
        while (true) {
            try { Thread.sleep(30_000); } catch (InterruptedException ignored) {}

            boolean needBroadcast = false;

            for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
                ClientHandler ch = entry.getValue();
                if (ch != null && !ch.isAlive(HEARTBEAT_TIMEOUT)) {
                    System.out.println("Player appears offline: " + entry.getKey());
                    needBroadcast = true; // chá»‰ mark offline, khÃ´ng remove ngay
                }
            }

            if (needBroadcast) {
                broadcastOnlinePlayers();
            }
        }
    }

    public ClientHandler getClient(String username) {
        if (username == null) return null;
        String key = username == null ? null : username.trim().toLowerCase();
        ClientHandler ch = clients.get(key);
        System.out.println("[ClientRegistry] getClient key=" + key + " -> " + (ch != null ? ch.getUsername() : "null"));
        return ch;
    }

    public void shutdown() {
        for (ClientHandler ch : clients.values()) {
            if (ch != null) ch.disconnect();
        }
        clients.clear();
    }
}


