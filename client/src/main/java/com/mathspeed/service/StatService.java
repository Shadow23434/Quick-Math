package com.mathspeed.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mathspeed.client.SessionManager;
import com.mathspeed.model.Stats;
import com.mathspeed.util.Config;
import com.mathspeed.util.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class StatService {
    private static final Logger logger = LoggerFactory.getLogger(StatService.class);
    private final HttpClient client;
    private final Gson gson;
    private final String STAT_URL;

    public StatService() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.gson = GsonFactory.createGson();
        String api = Config.getApiUrl();
        if (api.endsWith("/")) api = api.substring(0, api.length() - 1);
        this.STAT_URL = api + "/stats";
        logger.info("StatService using base URL: {}", this.STAT_URL);
    }

    public CompletableFuture<Stats> getStats(String userId) {
        String url = STAT_URL + "?id=" + urlEncode(userId);
        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET();

            String token = SessionManager.getInstance().getAuthToken();
            if (token != null && !token.isBlank()) {
                rb.header("Authorization", "Bearer " + token);
            }

            HttpRequest req = rb.build();

            return client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .handle((HttpResponse<String> response, Throwable throwable) -> {
                        if (throwable != null) {
                            logger.error("HTTP request failed for {}: {}", url, throwable.getMessage());
                            return null;
                        }
                        int status = response.statusCode();
                        String body = response.body();
                        if (status >= 200 && status < 300) {
                            try {
                                JsonElement root = JsonParser.parseString(body);
                                if (root.isJsonObject()) {
                                    JsonObject obj = root.getAsJsonObject();
                                    // Expecting shape: { ok: true, status: 200, stats: { ... } }
                                    JsonElement statsElem = obj.get("stats");
                                    if (statsElem != null && statsElem.isJsonObject()) {
                                        Stats stats = gson.fromJson(statsElem, Stats.class);
                                        return stats;
                                    } else {
                                        logger.error("No 'stats' object in response from {}. Body: {}", url, body);
                                        return null;
                                    }
                                } else {
                                    logger.error("Unexpected JSON root when fetching stats from {}. Body: {}", url, body);
                                    return null;
                                }
                            } catch (Exception e) {
                                logger.error("Failed to parse stats from {}: {}. Body: {}", url, e.toString(), body);
                                return null;
                            }
                        } else {
                            logger.error("Unexpected HTTP status {} when fetching {}. Body: {}", status, url, body);
                            return null;
                        }
                    });
        } catch (Exception e) {
            logger.error("Invalid URL for fetching stats: {}", url, e);
            CompletableFuture<Stats> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    private String urlEncode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
