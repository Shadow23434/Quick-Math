package com.mathspeed.adapter.network.stat;

import com.mathspeed.application.auth.AuthService;
import com.mathspeed.domain.port.GameHistoryRepository;
import com.mathspeed.domain.port.QuizzRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP handler for /api/stats?id=<playerId>
 */
public class StatsHandler implements HttpHandler {
    private final AuthService authService;
    private final QuizzRepository quizzRepository;
    private final GameHistoryRepository gameHistoryRepository;

    public StatsHandler(AuthService authService, QuizzRepository quizzRepository, GameHistoryRepository gameHistoryRepository) {
        this.authService = authService;
        this.quizzRepository = quizzRepository;
        this.gameHistoryRepository = gameHistoryRepository;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String query = exchange.getRequestURI().getQuery();
        Map<String, String> params = parseQuery(query);
        String id = params.get("id");

        if (!"GET".equalsIgnoreCase(method)) {
            String json = "{\"ok\":false,\"status\":405,\"error\":\"Method not allowed\"}";
            sendJson(exchange, 405, json);
            return;
        }

        if (id == null || id.isEmpty()) {
            String json = "{\"ok\":false,\"status\":400,\"error\":\"Missing id\"}";
            sendJson(exchange, 400, json);
            return;
        }

        try {
            if (!authService.exitstsById(id)) {
                String json = "{\"ok\":false,\"status\":400,\"error\":\"Invalid id\"}";
                sendJson(exchange, 400, json);
                return;
            }

            int totalQuizzes = 0;
            try {
                totalQuizzes = quizzRepository.getQuizCount();
            } catch (Exception ignored) {}

            int gamesPlayed = 0;
            try { gamesPlayed = gameHistoryRepository.getTotalGames(id); } catch (Exception ignored) {}

            int wins = 0;
            try { wins = gameHistoryRepository.getTotalWins(id); } catch (Exception ignored) {}

            int friends = 0;
            try { friends = authService.getTotalPlayers(); } catch (Exception ignored) {}

            StringBuilder sb = new StringBuilder();
            sb.append("{\"ok\":true,\"status\":200,\"stats\":{");
            sb.append("\"totalQuizzes\":").append(totalQuizzes).append(',');
            sb.append("\"gamesPlayed\":").append(gamesPlayed).append(',');
            sb.append("\"wins\":").append(wins).append(',');
            sb.append("\"friends\":").append(friends);
            sb.append("}}");

            sendJson(exchange, 200, sb.toString());
        } catch (Exception e) {
            String json = "{\"ok\":false,\"status\":500,\"error\":\"Internal error\"}";
            sendJson(exchange, 500, json);
        }
    }

    private Map<String, String> parseQuery(String q) {
        Map<String, String> map = new HashMap<>();
        if (q == null || q.isEmpty()) return map;
        String[] parts = q.split("&");
        for (String p : parts) {
            int idx = p.indexOf('=');
            if (idx > 0 && idx < p.length() - 1) {
                String k = p.substring(0, idx);
                String v = p.substring(idx + 1);
                map.put(k, decodeUrl(v));
            }
        }
        return map;
    }

    private String decodeUrl(String s) {
        try {
            return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        sendResponse(exchange, status, json);
    }

    private void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
