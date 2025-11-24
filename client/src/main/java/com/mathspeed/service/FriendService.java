package com.mathspeed.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mathspeed.client.SessionManager;
import com.mathspeed.model.Player;
import com.mathspeed.util.Config;
import com.mathspeed.util.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class FriendService {
    private static final Logger logger = LoggerFactory.getLogger(FriendService.class);
    private final HttpClient client;
    private final Gson gson;
    private final String FRIEND_URL;

    public FriendService() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.gson = GsonFactory.createGson();
        String api = Config.getApiUrl();
        if (api.endsWith("/")) api = api.substring(0, api.length() - 1);
        this.FRIEND_URL = api + "/friends";
        logger.info("FriendService using base URL: {}", this.FRIEND_URL);
    }

    public CompletableFuture<List<Player>> getAllFriends(String userId) {
        String url = FRIEND_URL + "/all?id=" + urlEncode(userId);
        return fetchFriendList(url);
    }

    public CompletableFuture<List<Player>> getOnlineFriends(String userId) {
        String url = FRIEND_URL + "/online?id=" + urlEncode(userId);
        return fetchFriendList(url);
    }

    public CompletableFuture<List<Player>> searchFriends(String keyword, String userId) {
        String url = FRIEND_URL + "/search?keyword=" + urlEncode(keyword) + "&id=" + urlEncode(userId);
        return fetchFriendList(url);
    }

    private CompletableFuture<List<Player>> fetchFriendList(String url) {
        try {
            java.net.http.HttpRequest.Builder rb = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET();

            String token = SessionManager.getInstance().getAuthToken();
            if (token != null && !token.isBlank()) {
                rb.header("Authorization", "Bearer " + token);
            }

            java.net.http.HttpRequest req = rb.build();

            return client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .handle((java.net.http.HttpResponse<String> response, Throwable throwable) -> {
                        if (throwable != null) {
                            logger.error("HTTP request failed for {}: {}", url, throwable.getMessage());
                            return Collections.<Player>emptyList();
                        }
                        int status = response.statusCode();
                        if (status >= 200 && status < 300) {
                            String body = response.body();
                            try {
                                // Try to parse flexible shapes: array at root or wrapped object containing an array.
                                JsonElement root = JsonParser.parseString(body);
                                JsonArray array = findFirstArray(root);
                                if (array != null) {
                                    Type listType = TypeToken.getParameterized(List.class, Player.class).getType();
                                    List<Player> list = gson.fromJson(array, listType);
                                    return list != null ? list : Collections.<Player>emptyList();
                                } else if (root.isJsonArray()) {
                                    Type listType = TypeToken.getParameterized(List.class, Player.class).getType();
                                    List<Player> list = gson.fromJson(root, listType);
                                    return list != null ? list : Collections.<Player>emptyList();
                                } else {
                                    logger.error("No JSON array found when parsing friend list from {}. Body: {}", url, body);
                                    return Collections.<Player>emptyList();
                                }
                            } catch (Exception e) {
                                logger.error("Failed to parse friend list from {}: {}. Body: {}", url, e.toString(), body);
                                return Collections.<Player>emptyList();
                            }
                        } else {
                            logger.error("Unexpected HTTP status {} when fetching {}", status, url);
                            return Collections.<Player>emptyList();
                        }
                    });
        } catch (Exception e) {
            logger.error("Invalid URL for fetching friends: {}", url, e);
            CompletableFuture<List<Player>> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    private JsonArray findFirstArray(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonArray()) return element.getAsJsonArray();
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                JsonArray a = findFirstArray(entry.getValue());
                if (a != null) return a;
            }
        }
        return null;
    }

    private String urlEncode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
