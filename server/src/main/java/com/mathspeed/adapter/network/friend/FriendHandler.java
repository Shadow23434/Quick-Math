package com.mathspeed.adapter.network.friend;

import com.mathspeed.application.friend.FriendService;
import com.mathspeed.domain.model.Player;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * HTTP handler that exposes friend-related endpoints.
 * GET /api/friends/all?id=<requesterId>
 * GET /api/friends/online?id=<requesterId>
 * GET /api/friends/search?keyword=<kw>&id=<requesterId>
 */
public class FriendHandler implements HttpHandler {
    private final FriendService friendService;

    public FriendHandler(FriendService friendService) {
        this.friendService = friendService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String fullPath = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();
        Map<String, String> params = parseQuery(query);
        String id = params.get("id");

        if (fullPath.endsWith("/all")) {
            if (!"GET".equalsIgnoreCase(method)) {
                String json = "{\"ok\":false,\"status\":405,\"error\":\"Method not allowed\"}";
                sendJson(exchange, 405, json);
                return;
            }
            handleListAll(exchange, id);
        } else if (fullPath.endsWith("/online")) {
            if (!"GET".equalsIgnoreCase(method)) {
                String json = "{\"ok\":false,\"status\":405,\"error\":\"Method not allowed\"}";
                sendJson(exchange, 405, json);
                return;
            }
            handleListOnline(exchange, id);
        } else if (fullPath.endsWith("/search")) {
            if (!"GET".equalsIgnoreCase(method)) {
                String json = "{\"ok\":false,\"status\":405,\"error\":\"Method not allowed\"}";
                sendJson(exchange, 405, json);
                return;
            }
            handleSearch(exchange, params);
        } else {
            String json = "{\"ok\":false,\"status\":404,\"error\":\"Not found\"}";
            sendJson(exchange, 404, json);

        }
    }

    private void handleListAll(HttpExchange exchange, String id) throws IOException {
        if (id == null || id.isEmpty()) {
            String json = "{\"ok\":false,\"status\":400,\"error\":\"Missing id\"}";
            sendJson(exchange, 400, json);
            return;
        }

        try {
            if (!friendService.playerExistsById(id)) {
                String json = "{\"ok\":false,\"status\":400,\"error\":\"Invalid id\"}";
                sendJson(exchange, 400, json);
                return;
            }
            List<Player> players = friendService.listAllPlayers(id);
            StringBuilder sb = new StringBuilder();
            sb.append("{\"ok\":true,\"status\":200,\"players\":[");
            boolean first = true;
            for (Player p : players) {
                if (!first) sb.append(',');
                first = false;
                sb.append('{');
                sb.append("\"id\":\"").append(escapeJson(p.getId())).append("\"");
                sb.append(",\"username\":\"").append(escapeJson(p.getUsername())).append("\"");
                sb.append(",\"displayName\":\"").append(escapeJson(p.getDisplayName())).append("\"");
                sb.append(",\"avatarUrl\":\"").append(escapeJson(p.getAvatarUrl())).append("\"");
                sb.append(",\"countryCode\":\"").append(escapeJson(p.getCountryCode())).append("\"");
                sb.append(",\"gender\":\"").append(escapeJson(p.getGender())).append("\"");
                sb.append(",\"status\":\"").append(escapeJson(p.getStatus())).append("\"");
                if (p.getLastActiveAt() != null) {
                    sb.append(",\"lastActiveAt\":\"").append(escapeJson(p.getLastActiveAt().toString())).append("\"");
                }
                if (p.getCreatedAt() != null) {
                    sb.append(",\"createdAt\":\"").append(escapeJson(p.getCreatedAt().toString())).append("\"");
                }
                sb.append('}');
            }
            sb.append("]}");
            sendJson(exchange, 200, sb.toString());
        } catch (Exception e) {
            String json = "{\"ok\":false,\"status\":500,\"error\":\"Internal error\"}";
            sendJson(exchange, 500, json);
        }
    }

    private void handleListOnline(HttpExchange exchange, String id) throws IOException {
        if (id == null || id.isEmpty()) {
            String json = "{\"ok\":false,\"status\":400,\"error\":\"Missing id\"}";
            sendJson(exchange, 400, json);
            return;
        }

        try {
            if (!friendService.playerExistsById(id)) {
                String json = "{\"ok\":false,\"status\":400,\"error\":\"Invalid id\"}";
                sendJson(exchange, 400, json);
                return;
            }
            List<Player> players = friendService.listOnlinePlayers(id);
            StringBuilder sb = new StringBuilder();
            sb.append("{\"ok\":true,\"status\":200,\"players\":[");
            boolean first = true;
            for (Player p : players) {
                if (!first) sb.append(',');
                first = false;
                sb.append('{');
                sb.append("\"id\":\"").append(escapeJson(p.getId())).append("\"");
                sb.append(",\"username\":\"").append(escapeJson(p.getUsername())).append("\"");
                sb.append(",\"displayName\":\"").append(escapeJson(p.getDisplayName())).append("\"");
                sb.append(",\"avatarUrl\":\"").append(escapeJson(p.getAvatarUrl())).append("\"");
                sb.append(",\"countryCode\":\"").append(escapeJson(p.getCountryCode())).append("\"");
                sb.append(",\"gender\":\"").append(escapeJson(p.getGender())).append("\"");
                sb.append(",\"status\":\"").append(escapeJson(p.getStatus())).append("\"");
                if (p.getLastActiveAt() != null) {
                    sb.append(",\"lastActiveAt\":\"").append(escapeJson(p.getLastActiveAt().toString())).append("\"");
                }
                if (p.getCreatedAt() != null) {
                    sb.append(",\"createdAt\":\"").append(escapeJson(p.getCreatedAt().toString())).append("\"");
                }
                sb.append('}');
            }
            sb.append("]}");
            sendJson(exchange, 200, sb.toString());
        } catch (Exception e) {
            String json = "{\"ok\":false,\"status\":500,\"error\":\"Internal error\"}";
            sendJson(exchange, 500, json);
        }
    }

    private void handleSearch(HttpExchange exchange, Map<String, String> params) throws IOException {
        String keyword = params.get("keyword");
        String id = params.get("id");

        if (id == null || id.isEmpty()) {
            String json = "{\"ok\":false,\"status\":400,\"error\":\"Missing id\"}";
            sendJson(exchange, 400, json);
            return;
        }

        if (keyword == null || keyword.isEmpty()) {
            String json = "{\"ok\":false,\"status\":400,\"error\":\"Missing keyword\"}";
            sendJson(exchange, 400, json);
            return;
        }

        try {
            List<Player> players;
            if (!friendService.playerExistsById(id)) {
                String json = "{\"ok\":false,\"status\":400,\"error\":\"Invalid id\"}";
                sendJson(exchange, 400, json);
                return;
            }
            // perform search and exclude requester id from results
            List<Player> found = friendService.searchPlayers(keyword, id);
            List<Player> filtered = new java.util.ArrayList<>();
            for (Player p : found) {
                if (!id.equals(p.getId())) filtered.add(p);
            }
            players = filtered;
            StringBuilder sb = new StringBuilder();
            sb.append("{\"ok\":true,\"status\":200,\"players\":[");
            boolean first = true;
            for (Player p : players) {
                if (!first) sb.append(',');
                first = false;
                sb.append('{');
                sb.append("\"id\":\"").append(escapeJson(p.getId())).append("\"");
                sb.append(",\"username\":\"").append(escapeJson(p.getUsername())).append("\"");
                sb.append(",\"displayName\":\"").append(escapeJson(p.getDisplayName())).append("\"");
                sb.append(",\"avatarUrl\":\"").append(escapeJson(p.getAvatarUrl())).append("\"");
                sb.append(",\"countryCode\":\"").append(escapeJson(p.getCountryCode())).append("\"");
                sb.append(",\"gender\":\"").append(escapeJson(p.getGender())).append("\"");
                sb.append(",\"status\":\"").append(escapeJson(p.getStatus())).append("\"");
                if (p.getLastActiveAt() != null) {
                    sb.append(",\"lastActiveAt\":\"").append(escapeJson(p.getLastActiveAt().toString())).append("\"");
                }
                if (p.getCreatedAt() != null) {
                    sb.append(",\"createdAt\":\"").append(escapeJson(p.getCreatedAt().toString())).append("\"");
                }
                sb.append('}');
            }
            sb.append("]}");
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

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
