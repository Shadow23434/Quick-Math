package com.mathspeed.adapter.network;

import com.mathspeed.domain.model.Player;
import com.mathspeed.domain.port.LeaderboardRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class LeaderboardHandler implements HttpHandler {

    private final LeaderboardRepository leaderboardRepository;

    public LeaderboardHandler(LeaderboardRepository leaderboardRepository) {
        this.leaderboardRepository = leaderboardRepository;
    }

    @Override
    public void handle(HttpExchange exchange) {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"ok\":false,\"error\":\"Method not allowed\"}");
                return;
            }

            List<Player> leaderboard = leaderboardRepository.getLeaderboard();

            StringBuilder sb = new StringBuilder();
            sb.append("{\"ok\":true,\"leaderboard\":[");
            for (int i = 0; i < leaderboard.size(); i++) {
                com.mathspeed.domain.model.Player p = leaderboard.get(i);
                sb.append("{")
                        .append("\"id\":\"").append(p.getId()).append("\",")
                        .append("\"username\":\"").append(p.getUsername()).append("\",")
                        .append("\"displayName\":\"").append(p.getDisplayName()).append("\",")
                        .append("\"avatarUrl\":\"").append(p.getAvatarUrl()).append("\",")
                        .append("\"wins\":").append(p.getWins()).append(",")
                        .append("\"gamesPlayed\":").append(p.getGamesPlayed())
                        .append("}");
                if (i < leaderboard.size() - 1) sb.append(",");
            }
            sb.append("]}");

            sendJson(exchange, 200, sb.toString());

        } catch (Exception e) {
            try {
                sendJson(exchange, 500, "{\"ok\":false,\"error\":\"Internal server error\"}");
            } catch (Exception ignored) {}
        }
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws Exception {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
