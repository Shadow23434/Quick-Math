package com.mathspeed.service;

import com.google.gson.Gson;
import com.mathspeed.model.auth.LogoutRequest;
import com.mathspeed.util.Config;
import com.mathspeed.util.GsonFactory;
import com.mathspeed.model.auth.LoginRequest;
import com.mathspeed.model.auth.LoginResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
        logger.info("AuthService using AUTH URL: {}", AUTH_URL);
    }

    public CompletableFuture<LoginResponse> login(String username, String password) {
        LoginRequest requestBody = new LoginRequest(username, password);
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
                            LoginResponse lr = gson.fromJson(response.body(), LoginResponse.class);
                            return lr != null ? lr : createErrorResponse("Invalid server response", response.statusCode());
                        } else {
                            // Try to parse error body - server may return JSON with { status, error }
                            try {
                                LoginResponse parsed = gson.fromJson(response.body(), LoginResponse.class);
                                if (parsed != null) {
                                    // Ensure the parsed response indicates failure and include HTTP status if missing
                                    parsed.setSuccess(false);
                                    if (parsed.getStatusCode() == null) parsed.setStatusCode(response.statusCode());
                                    return parsed;
                                }
                            } catch (Exception ignored) {
                                // fall through to create a minimal error response
                            }

                            LoginResponse errorResponse = new LoginResponse();
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

    public CompletableFuture<LoginResponse> logout(String username) {
        String token = SessionManager.getInstance().getAuthToken();
        LogoutRequest logoutRequest = new LogoutRequest(username);
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
                            LoginResponse lr = gson.fromJson(response.body(), LoginResponse.class);
                            return lr != null ? lr : createErrorResponse("Invalid server response", response.statusCode());
                        } else {
                            try {
                                LoginResponse parsed = gson.fromJson(response.body(), LoginResponse.class);
                                if (parsed != null) {
                                    parsed.setSuccess(false);
                                    if (parsed.getStatusCode() == null) parsed.setStatusCode(response.statusCode());
                                    return parsed;
                                }
                            } catch (Exception ignored) {}

                            LoginResponse errorResponse = new LoginResponse();
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

    private LoginResponse createErrorResponse(String message) {
        return createErrorResponse(message, 500);
    }

    private LoginResponse createErrorResponse(String message, int statusCode) {
        LoginResponse errorResponse = new LoginResponse();
        errorResponse.setSuccess(false);
        errorResponse.setMessage(message);
        errorResponse.setStatusCode(statusCode);
        return errorResponse;
    }
}