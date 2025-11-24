package com.mathspeed.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mathspeed.model.Quiz;
import com.mathspeed.client.SessionManager;
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
import com.mathspeed.model.Player;

public class LibraryService {
    private static final Logger logger = LoggerFactory.getLogger(LibraryService.class);
    private final HttpClient client;
    private final Gson gson;
    private final String LIBRARY_URL;

    public LibraryService() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.gson = GsonFactory.createGson();
        String api = Config.getApiUrl();
        if (api.endsWith("/")) api = api.substring(0, api.length() - 1);
        this.LIBRARY_URL = api + "/library";
        logger.info("LibraryService using base URL: {}", this.LIBRARY_URL);
    }

    public CompletableFuture<List<Quiz>> getAllQuizzes() {
        String url = LIBRARY_URL + "/all";
        return fetchQuizList(url);
    }

    public CompletableFuture<List<Quiz>> getOwnQuizzes(String playerId) {
        String url = LIBRARY_URL + "/own" + "?id=" + urlEncode(playerId);
        return fetchQuizList(url);
    }

    private CompletableFuture<List<Quiz>> fetchQuizList(String url) {
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
                            return Collections.<Quiz>emptyList();
                        }
                        int status = response.statusCode();
                        if (status >= 200 && status < 300) {
                            String body = response.body();
                            try {
                                JsonElement root = JsonParser.parseString(body);
                                JsonArray array = findFirstArray(root);
                                if (array != null) {
                                    Type listType = TypeToken.getParameterized(List.class, Quiz.class).getType();
                                    List<Quiz> list = gson.fromJson(array, listType);
                                    return list != null ? list : Collections.<Quiz>emptyList();
                                } else if (root.isJsonArray()) {
                                    Type listType = TypeToken.getParameterized(List.class, Quiz.class).getType();
                                    List<Quiz> list = gson.fromJson(root, listType);
                                    return list != null ? list : Collections.<Quiz>emptyList();
                                } else {
                                    logger.error("No JSON array found when parsing quiz list from {}. Body: {}", url, body);
                                    return Collections.<Quiz>emptyList();
                                }
                            } catch (Exception e) {
                                logger.error("Failed to parse quiz list from {}: {}. Body: {}", url, e.toString(), body);
                                return Collections.<Quiz>emptyList();
                            }
                        } else {
                            logger.error("Unexpected HTTP status {} when fetching {}", status, url);
                            return Collections.<Quiz>emptyList();
                        }
                    });
        } catch (Exception e) {
            logger.error("Invalid URL for fetching quizzes: {}", url, e);
            CompletableFuture<List<Quiz>> failed = new CompletableFuture<>();
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
