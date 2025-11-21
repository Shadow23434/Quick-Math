package com.mathspeed.adapter.network.auth;

import com.mathspeed.application.auth.AuthService;
import com.mathspeed.application.auth.AuthService.AuthResult;
import com.mathspeed.domain.model.Player;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AuthHandler implements HttpHandler {
    private final AuthService authService;
    private final Pattern userPattern = Pattern.compile("\"username\"\\s*:\\s*\"([^\"]+)\"");
    private final Pattern passPattern = Pattern.compile("\"password\"\\s*:\\s*\"([^\"]+)\"");
    private final Pattern displayPattern = Pattern.compile("\"displayName\"\\s*:\\s*\"([^\"]+)\"");

    public AuthHandler(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String fullPath = exchange.getRequestURI().getPath();
        // Expecting contexts like /api/auth/login, /api/auth/register, /api/auth/logout
        if (fullPath.endsWith("/login")) {
            if (!"POST".equalsIgnoreCase(method)) {
                String json = "{\"ok\":false,\"status\":405,\"error\":\"Method not allowed\"}";
                sendJson(exchange, 405, json);
                return;
            }
            String body = readRequestBody(exchange.getRequestBody());
            handleLogin(exchange, body);
            return;
        }
        else if (fullPath.endsWith("/register")) {
            if (!"POST".equalsIgnoreCase(method)) {
                String json = "{\"ok\":false,\"status\":405,\"error\":\"Method not allowed\"}";
                sendJson(exchange, 405, json);
                return;
            }
            String body = readRequestBody(exchange.getRequestBody());
            handleRegister(exchange, body);
            return;
        }
        else if (fullPath.endsWith("/logout")) {
            if (!"POST".equalsIgnoreCase(method)) {
                String json = "{\"ok\":false,\"status\":405,\"error\":\"Method not allowed\"}";
                sendJson(exchange, 405, json);
                return;
            }
            String body = readRequestBody(exchange.getRequestBody());
            handleLogout(exchange, body);
            return;
        }
        else {
            String json = "{\"ok\":false,\"status\":404,\"error\":\"Not found\"}";
            sendJson(exchange, 404, json);
            return;
        }
    }

    private void handleLogin(HttpExchange exchange, String body) throws IOException {
        String username = extract(body, userPattern);
        String password = extract(body, passPattern);

        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            String json = "{\"ok\":false,\"error\":\"Missing credentials\",\"status\":400}";
            sendJson(exchange, 400, json);
            return;
        }

        AuthResult result = authService.login(username, password);
        if (result.success) {
            Player p = result.player;
            StringBuilder sb = new StringBuilder();
            sb.append("{\"ok\":true,\"status\":200,\"token\":\"").append(escapeJson(result.token)).append('\"');
            if (p != null) {
                sb.append(",\"player\":{");
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
                sb.append("}");
            }
            sb.append("}");
            sendJson(exchange, 200, sb.toString());
        } else {
            String json = "{\"ok\":false,\"status\":401,\"error\":\"" + escapeJson(result.error) + "\"}";
            sendJson(exchange, 401, json);
        }
    }

    private void handleRegister(HttpExchange exchange, String body) throws IOException {
        String username = extract(body, userPattern);
        String password = extract(body, passPattern);
        String displayName = extract(body, displayPattern);

        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            String json = "{\"ok\":false,\"error\":\"Missing registration fields\",\"status\":400}";
            sendJson(exchange, 400, json);
            return;
        }

        AuthResult result = authService.register(username, password, displayName);
        if (result.success) {
            Player p = result.player;
            StringBuilder sb = new StringBuilder();
            sb.append("{\"ok\":true,\"status\":200,\"token\":\"").append(escapeJson(result.token)).append('\"');
            if (p != null) {
                sb.append(",\"player\":{");
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
                sb.append("}");
            }
            sb.append("}");
            sendJson(exchange, 200, sb.toString());
        } else {
            String json = "{\"ok\":false,\"status\":400,\"error\":\"" + escapeJson(result.error) + "\"}";
            sendJson(exchange, 400, json);
        }
    }

    private void handleLogout(HttpExchange exchange, String body) throws IOException {
        String username = extract(body, userPattern);
        if (username == null || username.isEmpty()) {
            String json = "{\"ok\":false,\"error\":\"Missing username\",\"status\":400}";
            sendJson(exchange, 400, json);
            return;
        }

        boolean ok = authService.logout(username);
        if (ok) {
            String json = "{\"ok\":true,\"status\":200}";
            sendJson(exchange, 200, json);
        } else {
            String json = "{\"ok\":false,\"status\":500,\"error\":\"Logout failed\"}";
            sendJson(exchange, 500, json);
        }
    }

    private String readRequestBody(InputStream is) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private String extract(String body, Pattern pattern) {
        if (body == null) return null;
        Matcher m = pattern.matcher(body);
        if (m.find()) return m.group(1);
        return null;
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
