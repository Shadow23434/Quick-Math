package com.mathspeed.service;

import com.google.gson.Gson;
import com.mathspeed.util.Config;
import com.mathspeed.util.GsonFactory;
import com.mathspeed.service.entity.AuthResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mathspeed.client.SessionManager;

public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private static final String AUTH_URL = Config.getApiUrl() + "/auth";
    private final HttpClient client;
    private final Gson gson;

    public AuthService() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.gson = GsonFactory.createGson();
    }

    public CompletableFuture<AuthResponse> login(String username, String password) {
        Map<String, String> requestBody = Map.of(
                "username", username,
                "password", password
        );
        String jsonBody = gson.toJson(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AUTH_URL + "/login"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    try {
                        if (response.statusCode() == 200) {
                            AuthResponse lr = gson.fromJson(response.body(), AuthResponse.class);
                            return lr != null ? lr : createErrorResponse("Invalid server response", response.statusCode());
                        } else {
                            // Try to parse error body - server may return JSON with { status, error }
                            try {
                                AuthResponse parsed = gson.fromJson(response.body(), AuthResponse.class);
                                if (parsed != null) {
                                    // Ensure the parsed response indicates failure and include HTTP status if missing
                                    parsed.setSuccess(false);
                                    if (parsed.getStatusCode() == null) parsed.setStatusCode(response.statusCode());
                                    return parsed;
                                }
                            } catch (Exception ignored) {
                                // fall through to create a minimal error response
                            }

                            AuthResponse errorResponse = new AuthResponse();
                            errorResponse.setSuccess(false);
                            errorResponse.setMessage("Authentication failed: HTTP " + response.statusCode());
                            errorResponse.setStatusCode(response.statusCode());
                            return errorResponse;
                        }
                    } catch (Exception e) {
                        return createErrorResponse("Error parsing server response: " + e.getMessage(), 500);
                    }
                })
                .exceptionally(ex -> createErrorResponse("Connection error: " + ex.getMessage()));
    }

    public CompletableFuture<AuthResponse> logout(String username) {
        String token = SessionManager.getInstance().getAuthToken();
        Map<String, String> logoutRequest = Map.of(
                "username", username
        );
        String jsonBody = gson.toJson(logoutRequest);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(AUTH_URL + "/logout"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

        if (token != null && !token.isBlank()) {
            reqBuilder.header("Authorization", "Bearer " + token);
        }

        HttpRequest request = reqBuilder.build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    try {
                        if (response.statusCode() == 200) {
                            AuthResponse lr = gson.fromJson(response.body(), AuthResponse.class);
                            return lr != null ? lr : createErrorResponse("Invalid server response", response.statusCode());
                        } else {
                            try {
                                AuthResponse parsed = gson.fromJson(response.body(), AuthResponse.class);
                                if (parsed != null) {
                                    parsed.setSuccess(false);
                                    if (parsed.getStatusCode() == null) parsed.setStatusCode(response.statusCode());
                                    return parsed;
                                }
                            } catch (Exception ignored) {}

                            AuthResponse errorResponse = new AuthResponse();
                            errorResponse.setSuccess(false);
                            errorResponse.setMessage("Logout failed: HTTP " + response.statusCode());
                            errorResponse.setStatusCode(response.statusCode());
                            return errorResponse;
                        }
                    } catch (Exception e) {
                        return createErrorResponse("Error parsing server response: " + e.getMessage(), 500);
                    }
                })
                .exceptionally(ex -> createErrorResponse("Connection error: " + ex.getMessage()));
    }

    public CompletableFuture<AuthResponse> register(String username, String password, String displayName, String gender, String countryCode) {
        Map<String, String> requestBody = Map.of(
                "username", username,
                "password", password,
                "displayName", displayName,
                "gender", gender,
                "countryCode", countryCode
        );
        String jsonBody = gson.toJson(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AUTH_URL + "/register"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    try {
                        int status = response.statusCode();
                        if (status == 200 || status == 201) {
                            AuthResponse ar = gson.fromJson(response.body(), AuthResponse.class);
                            return ar != null ? ar : createErrorResponse("Invalid server response", status);
                        } else {
                            try {
                                AuthResponse parsed = gson.fromJson(response.body(), AuthResponse.class);
                                if (parsed != null) {
                                    parsed.setSuccess(false);
                                    if (parsed.getStatusCode() == null) parsed.setStatusCode(status);
                                    return parsed;
                                }
                            } catch (Exception ignored) {}

                            AuthResponse errorResponse = new AuthResponse();
                            errorResponse.setSuccess(false);
                            errorResponse.setMessage("Registration failed: HTTP " + status);
                            errorResponse.setStatusCode(status);
                            return errorResponse;
                        }
                    } catch (Exception e) {
                        return createErrorResponse("Error parsing server response: " + e.getMessage(), 500);
                    }
                })
                .exceptionally(ex -> createErrorResponse("Connection error: " + ex.getMessage()));
    }

    private AuthResponse createErrorResponse(String message) {
        return createErrorResponse(message, 500);
    }

    private AuthResponse createErrorResponse(String message, int statusCode) {
        AuthResponse errorResponse = new AuthResponse();
        errorResponse.setSuccess(false);
        errorResponse.setMessage(message);
        errorResponse.setStatusCode(statusCode);
        return errorResponse;
    }
}