package com.mathspeed.adapter.network.library;

import com.mathspeed.application.auth.AuthService;
import com.mathspeed.application.library.LibraryService;
import com.mathspeed.domain.model.Player;
import com.mathspeed.domain.model.Quiz;
import com.mathspeed.infrastructure.persistence.PlayerDAOImpl;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP handler that exposes library-related endpoints.
 * GET /api/library/all
 * GET /api/library/own?id=<playerId>
 */
public class LibraryHandler implements HttpHandler {
    private static final Logger logger = LogManager.getLogger(LibraryHandler.class);

    private final LibraryService libraryService;
    private final AuthService authService;

    public LibraryHandler(AuthService authService, LibraryService libraryService) {
        this.authService = authService;
        this.libraryService = libraryService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String fullPath = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();
        Map<String, String> params = parseQuery(query);

        if (fullPath.endsWith("/all")) {
            if (!"GET".equalsIgnoreCase(method)) {
                String json = "{\"ok\":false,\"status\":405,\"error\":\"Method not allowed\"}";
                sendJson(exchange, 405, json);
                return;
            }
            handleListAll(exchange);
            return;
        }

        if (fullPath.endsWith("/own")) {
            if (!"GET".equalsIgnoreCase(method)) {
                String json = "{\"ok\":false,\"status\":405,\"error\":\"Method not allowed\"}";
                sendJson(exchange, 405, json);
                return;
            }
            String id = params.get("id");
            handleListOwn(exchange, id);
            return;
        }

        String json = "{\"ok\":false,\"status\":404,\"error\":\"Not found\"}";
        sendJson(exchange, 404, json);
    }

    private void handleListOwn(HttpExchange exchange, String id) throws IOException {
        if (id == null || id.isEmpty()) {
            String json = "{\"ok\":false,\"status\":400,\"error\":\"Missing id\"}";
            sendJson(exchange, 400, json);
            return;
        }

        try {
            List<Quiz> quizzes = libraryService.listOwnQuizzes(id);
            StringBuilder sb = new StringBuilder();
            sb.append("{\"ok\":true,\"status\":200,\"quizzes\":[");
            boolean first = true;
            for (Quiz q : quizzes) {
                if (!first) sb.append(',');
                first = false;
                sb.append('{');
                sb.append("\"id\":\"").append(escapeJson(q.getId())).append('\"');
                sb.append(",\"title\":\"").append(escapeJson(q.getTitle())).append('\"');
                sb.append(",\"questionNumber\":").append(q.getQuestionNumber());

                Player p = null;
                try {
                    p = authService.getPlayerById(q.getPlayerId());
                } catch (Exception e) {
                    logger.error("Failed to fetch player for quiz id={} playerId={}", q.getId(), q.getPlayerId(), e);
                    try {
                        String errJson = "{\"ok\":false,\"status\":500,\"error\":\"Failed to fetch player info\"}";
                        sendJson(exchange, 500, errJson);
                    } catch (IOException ioe) {
                        logger.error("Failed to send error response for player fetch failure", ioe);
                    }
                    return;
                }

                if (p == null) {
                    try {
                        p = new PlayerDAOImpl().getPlayerById(q.getPlayerId());
                    } catch (SQLException sqle) {
                        logger.warn("Fallback player lookup failed for id={}", q.getPlayerId(), sqle);
                    }
                }

                if (p != null) {
                    sb.append(",\"player\":{");
                    sb.append("\"id\":\"").append(escapeJson(p.getId())).append('\"');
                    sb.append(",\"username\":\"").append(escapeJson(p.getUsername())).append('\"');
                    sb.append(",\"displayName\":\"").append(escapeJson(p.getDisplayName())).append('\"');
                    sb.append(",\"avatarUrl\":\"").append(escapeJson(p.getAvatarUrl())).append('\"');
                    sb.append(",\"countryCode\":\"").append(escapeJson(p.getCountryCode())).append('\"');
                    sb.append(",\"gender\":\"").append(escapeJson(p.getGender())).append('\"');
                    sb.append(",\"status\":\"").append(escapeJson(p.getStatus())).append('\"');
                    if (p.getLastActiveAt() != null) {
                        sb.append(",\"lastActiveAt\":\"").append(escapeJson(p.getLastActiveAt().toString())).append('\"');
                    }
                    if (p.getCreatedAt() != null) {
                        sb.append(",\"createdAt\":\"").append(escapeJson(p.getCreatedAt().toString())).append('\"');
                    }
                    sb.append('}');
                } else {
                    sb.append(",\"playerId\":\"").append(escapeJson(q.getPlayerId())).append('\"');
                }

                sb.append(",\"level\":\"").append(escapeJson(q.getLevel())).append('\"');
                if (q.getCreatedAt() != null) {
                    sb.append(",\"createdAt\":\"").append(escapeJson(q.getCreatedAt().toString())).append('\"');
                }
                sb.append('}');
            }
            sb.append("]}");
            sendJson(exchange, 200, sb.toString());
        } catch (Exception e) {
            logger.error("Unexpected error while listing own quizzes", e);
            String json = "{\"ok\":false,\"status\":500,\"error\":\"Internal error\"}";
            sendJson(exchange, 500, json);
        }
    }

    private void handleListAll(HttpExchange exchange) throws IOException {
        try {
            List<Quiz> quizzes = libraryService.listAllQuizzes();
            StringBuilder sb = new StringBuilder();
            sb.append("{\"ok\":true,\"status\":200,\"quizzes\":[");
            boolean first = true;
            for (Quiz q : quizzes) {
                if (!first) sb.append(',');
                first = false;
                sb.append('{');
                sb.append("\"id\":\"").append(escapeJson(q.getId())).append('\"');
                sb.append(",\"title\":\"").append(escapeJson(q.getTitle())).append('\"');
                sb.append(",\"questionNumber\":").append(q.getQuestionNumber());

                Player p = null;
                try {
                    p = authService.getPlayerById(q.getPlayerId());
                } catch (Exception e) {
                    // Log the error and return HTTP 500 to the client — do not continue building the response.
                    logger.error("Failed to fetch player for quiz id={} playerId={}", q.getId(), q.getPlayerId(), e);
                    try {
                        String errJson = "{\"ok\":false,\"status\":500,\"error\":\"Failed to fetch player info\"}";
                        sendJson(exchange, 500, errJson);
                    } catch (IOException ioe) {
                        logger.error("Failed to send error response for player fetch failure", ioe);
                    }
                    return;
                }

                // Fallback: if authService returned null, try direct DAO lookup
                if (p == null) {
                    try {
                        p = new PlayerDAOImpl().getPlayerById(q.getPlayerId());
                    } catch (SQLException sqle) {
                        logger.warn("Fallback player lookup failed for id={}", q.getPlayerId(), sqle);
                    }
                }

                if (p != null) {
                    sb.append(",\"player\":{");
                    sb.append("\"id\":\"").append(escapeJson(p.getId())).append('\"');
                    sb.append(",\"username\":\"").append(escapeJson(p.getUsername())).append('\"');
                    sb.append(",\"displayName\":\"").append(escapeJson(p.getDisplayName())).append('\"');
                    sb.append(",\"avatarUrl\":\"").append(escapeJson(p.getAvatarUrl())).append('\"');
                    sb.append(",\"countryCode\":\"").append(escapeJson(p.getCountryCode())).append('\"');
                    sb.append(",\"gender\":\"").append(escapeJson(p.getGender())).append('\"');
                    sb.append(",\"status\":\"").append(escapeJson(p.getStatus())).append('\"');
                    if (p.getLastActiveAt() != null) {
                        sb.append(",\"lastActiveAt\":\"").append(escapeJson(p.getLastActiveAt().toString())).append('\"');
                    }
                    if (p.getCreatedAt() != null) {
                        sb.append(",\"createdAt\":\"").append(escapeJson(p.getCreatedAt().toString())).append('\"');
                    }
                    sb.append('}');
                } else {
                    sb.append(",\"playerId\":\"").append(escapeJson(q.getPlayerId())).append('\"');
                }

                sb.append(",\"level\":\"").append(escapeJson(q.getLevel())).append('\"');
                if (q.getCreatedAt() != null) {
                    sb.append(",\"createdAt\":\"").append(escapeJson(q.getCreatedAt().toString())).append('\"');
                }
                sb.append('}');
            }
            sb.append("]}");
            sendJson(exchange, 200, sb.toString());
        } catch (Exception e) {
            logger.error("Unexpected error while listing quizzes", e);
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
