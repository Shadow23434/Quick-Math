package com.mathspeed.client;

import com.google.gson.*;
import com.mathspeed.controller.GameplayController;
import com.mathspeed.model.*;
import com.mathspeed.network.NetworkGameplay;
import com.google.gson.stream.JsonReader;
import com.mathspeed.util.GsonJavaTime;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TestClient (improved)
 *
 * - Safer parsing of LOGIN_SUCCESS payload (extract balanced JSON, lenient parse fallback, regex fallback).
 * - All calls that touch GameplayController / UI are executed on JavaFX Application Thread.
 * - Parses question_count from MATCH_START_INFO and attempts to call controller.setTotalRounds(q).
 * - Keeps delivering messages to controller via deliverToController(...) as before.
 */
public class TestClient extends Application {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8888;

    private static Socket socket;
    private static BufferedReader in;
    private static PrintWriter out;
    private static GameplayController gameController;
    private static Stage primaryStage;
    private final Gson gson = GsonJavaTime.create();

    // local identity set from server LOGIN_SUCCESS message
    private Player localPlayer = new Player();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        primaryStage.setTitle("Math Speed Test Client");
        primaryStage.setOnCloseRequest(e -> cleanup());
        // Start console and socket in background
        new Thread(this::runConsoleAndSocket, "TestClient-Main").start();
    }

    /**
     * Run console + socket reader loop.
     * This method has been hardened for:
     *  - safer LOGIN_SUCCESS parsing
     *  - ensuring UI calls happen on FX thread
     *  - parsing question_count from MATCH_START_INFO and setting total rounds on controller
     */
    private void runConsoleAndSocket() {
        System.out.println("TestClient: connecting to " + SERVER_HOST + ":" + SERVER_PORT);
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            System.out.println("Connected to server.");

            // reader thread
            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        final String l = line;
                        System.out.println("\n<<< SERVER: " + l);

                        try {
                            // 1) Handle LOGIN_SUCCESS|{...} specially: extract id/username/display_name/avatar_url and set local identity
                            if (l.startsWith("LOGIN_SUCCESS|") || l.startsWith("LOGIN_SUCCESS |")) {
                                int idx = l.indexOf('|');
                                if (idx >= 0 && idx + 1 < l.length()) {
                                    String payload = l.substring(idx + 1).trim();
                                    try {
                                        // try to extract a balanced JSON object portion
                                        String jsonPart = extractJsonObject(payload);
                                        JsonObject jo = null;
                                        if (jsonPart != null) {
                                            try {
                                                JsonElement je = parseJsonLenient(jsonPart);
                                                if (je != null && je.isJsonObject()) jo = je.getAsJsonObject();
                                            } catch (Exception ex) {
                                                System.err.println("LOGIN_SUCCESS: lenient parse failed, jsonPart=" + jsonPart);
                                                ex.printStackTrace();
                                            }
                                        }

                                        // fallback: try to extract common fields with regex if JSON couldn't be parsed
                                        if (jo == null) {
                                            jo = new JsonObject();
                                            Pattern pUser = Pattern.compile("\"username\"\\s*:\\s*\"([^\"]+)\"");
                                            Pattern pId = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
                                            Pattern pDisplay = Pattern.compile("\"display_name\"\\s*:\\s*\"([^\"]+)\"");
                                            Pattern pAvatar = Pattern.compile("\"avatar_url\"\\s*:\\s*\"([^\"]+)\"");
                                            Matcher m;
                                            m = pId.matcher(payload);
                                            if (m.find()) jo.addProperty("id", m.group(1));
                                            m = pUser.matcher(payload);
                                            if (m.find()) jo.addProperty("username", m.group(1));
                                            m = pDisplay.matcher(payload);
                                            if (m.find()) jo.addProperty("display_name", m.group(1));
                                            m = pAvatar.matcher(payload);
                                            if (m.find()) jo.addProperty("avatar_url", m.group(1));

                                            // if regex found nothing, set jo = null to indicate failure
                                            if (!jo.has("id") && !jo.has("username") && !jo.has("display_name") && !jo.has("avatar_url")) {
                                                jo = null;
                                            }
                                        }

                                        if (jo == null) {
                                            System.err.println("LOGIN_SUCCESS: could not extract valid JSON. Raw payload:");
                                            System.err.println(payload);
                                        } else {
                                            // populate localPlayer
                                            try {
                                                if (jo.has("id") && !jo.get("id").isJsonNull()) localPlayer.setId(jo.get("id").getAsString());
                                            } catch (Exception ignored) {}
                                            try {
                                                if (jo.has("username") && !jo.get("username").isJsonNull()) localPlayer.setUsername(jo.get("username").getAsString());
                                            } catch (Exception ignored) {}
                                            try {
                                                if (jo.has("display_name") && !jo.get("display_name").isJsonNull()) localPlayer.setDisplayName(jo.get("display_name").getAsString());
                                            } catch (Exception ignored) {}
                                            try {
                                                if (jo.has("avatar_url") && !jo.get("avatar_url").isJsonNull()) localPlayer.setAvatarUrl(jo.get("avatar_url").getAsString());
                                            } catch (Exception ignored) {}

                                            if (localPlayer.getDisplayName() == null || localPlayer.getDisplayName().isEmpty()) {
                                                localPlayer.setDisplayName(localPlayer.getUsername());
                                            }

                                            System.out.println("Parsed LOGIN_SUCCESS: id=" + localPlayer.getId()
                                                    + " username=" + localPlayer.getUsername()
                                                    + " display_name=" + localPlayer.getDisplayName()
                                                    + " avatar=" + localPlayer.getAvatarUrl());

                                            // Inject identity into controller on FX thread (if controller is already created)
                                            if (gameController != null) {
                                                final String u = localPlayer.getUsername();
                                                final String d = localPlayer.getDisplayName();
                                                final String avatar = localPlayer.getAvatarUrl();
                                                Platform.runLater(() -> {
                                                    try {
                                                        if (u != null && !u.isEmpty()) gameController.setPlayerUsername(u);
                                                        if (avatar != null && !avatar.isEmpty()) {
                                                            try { gameController.setPlayerAvatarUrl(avatar); } catch (Throwable ignored) {}
                                                        }
                                                        // try public setter then fallback to reflection
                                                        try {
                                                            Method setDisp = gameController.getClass().getMethod("setPlayerDisplayName", String.class);
                                                            setDisp.invoke(gameController, d);
                                                        } catch (NoSuchMethodException nsme) {
                                                            try {
                                                                Field fld = gameController.getClass().getDeclaredField("playerDisplayName");
                                                                fld.setAccessible(true);
                                                                fld.set(gameController, d);
                                                            } catch (Exception ignored) {}
                                                            try {
                                                                Method mu = gameController.getClass().getDeclaredMethod("updatePlayerNames");
                                                                mu.setAccessible(true);
                                                                mu.invoke(gameController);
                                                            } catch (Exception ignored) {}
                                                        }
                                                    } catch (Exception ex) {
                                                        System.err.println("Failed to inject username/display_name into controller (FX thread):");
                                                        ex.printStackTrace();
                                                    }
                                                });
                                            }
                                        }
                                    } catch (Throwable ex) {
                                        System.err.println("Unexpected error handling LOGIN_SUCCESS payload:");
                                        ex.printStackTrace();
                                        System.err.println("Raw payload:");
                                        System.err.println(payload);
                                    }
                                }
                                // continue to next line (we still allow deliverToController to show any feedback if desired)
                            }

                            // 2) If UI active, deliver immediately (deliverToController runs Platform.runLater inside itself)
                            if (gameController != null) {
                                deliverToController(l);
                                // continue loop
                                System.out.print(">>> ");
                                System.out.flush();
                                continue;
                            }

                            // 3) Auto switch to gameplay UI when MATCH_START_INFO appears (controller not created yet)
                            if (l.contains("\"type\":\"MATCH_START_INFO\"") || l.contains("\"type\": \"MATCH_START_INFO\"")) {
                                // Parse question_count early (if present) so we can call setTotalRounds after UI ready
                                int questionCount = 0;
                                try {
                                    JsonElement je = JsonParser.parseString(l.trim());
                                    if (je != null && je.isJsonObject()) {
                                        JsonObject jo = je.getAsJsonObject();
                                        if (jo.has("question_count") && !jo.get("question_count").isJsonNull()) {
                                            try {
                                                questionCount = jo.get("question_count").getAsInt();
                                            } catch (Exception ex) {
                                                try { questionCount = Integer.parseInt(jo.get("question_count").getAsString()); } catch (Exception ignored) {}
                                            }
                                        }
                                    }
                                } catch (Exception ex) {
                                    // ignore parsing errors here; we'll still switch UI and deliver raw message
                                }
                                if(gameController != null) {
                                    System.out.println("Setting total rounds to " + questionCount + " before switch.");
                                    gameController.setTotalRounds(questionCount);
                                }

                                // Ensure switching UI happens on FX thread
                                Platform.runLater(() -> {
                                    try {
                                        switchToGameplay();
                                    } catch (Exception ex) {
                                        ex.printStackTrace();
                                    }
                                });

                                // give UI some time then deliver
                                int tries = 0;
                                while (gameController == null && tries++ < 40) {
                                    Thread.sleep(50);
                                }

                                if (gameController != null) {
                                    // If we parsed question_count, set total rounds on FX thread (try public method, else fallback to field)
                                    final int qc = questionCount;
                                    if (qc > 0) {
                                        Platform.runLater(() -> {
                                            try {
                                                try {
                                                    Method m = gameController.getClass().getMethod("setTotalRounds", int.class);
                                                    m.invoke(gameController, qc);
                                                } catch (NoSuchMethodException nsme) {
                                                    // fallback: try set field totalRounds if present
                                                    try {
                                                        Field f = gameController.getClass().getDeclaredField("totalRounds");
                                                        f.setAccessible(true);
                                                        f.setInt(gameController, qc);
                                                        // ensure UI refresh
                                                        try {
                                                            Method ud = gameController.getClass().getDeclaredMethod("updateDisplay");
                                                            ud.setAccessible(true);
                                                            ud.invoke(gameController);
                                                        } catch (Exception ignored) {}
                                                    } catch (Exception ignored) {}
                                                }
                                            } catch (Exception ex) {
                                                System.err.println("Failed to set total rounds on controller:");
                                                ex.printStackTrace();
                                            }
                                        });
                                    }

                                    // Ensure controller knows local identity if we already learned it from LOGIN_SUCCESS
                                    if (localPlayer.getUsername() != null && !localPlayer.getUsername().isEmpty()) {
                                        final String u = localPlayer.getUsername();
                                        final String d = localPlayer.getDisplayName() != null ? localPlayer.getDisplayName() : u;
                                        final String avatar = localPlayer.getAvatarUrl();
                                        Platform.runLater(() -> {
                                            try {
                                                gameController.setPlayerUsername(u);
                                                if (avatar != null && !avatar.isEmpty()) {
                                                    try { gameController.setPlayerAvatarUrl(avatar); } catch (Throwable ignored) {}
                                                }
                                                // update display name via public API if available, fallback to reflection
                                                try {
                                                    Method setDisp = gameController.getClass().getMethod("setPlayerDisplayName", String.class);
                                                    setDisp.invoke(gameController, d);
                                                } catch (NoSuchMethodException nsme) {
                                                    try {
                                                        Field fld = gameController.getClass().getDeclaredField("playerDisplayName");
                                                        fld.setAccessible(true);
                                                        fld.set(gameController, d);
                                                    } catch (Exception ignored) {}
                                                    try {
                                                        Method mu = gameController.getClass().getDeclaredMethod("updatePlayerNames");
                                                        mu.setAccessible(true);
                                                        mu.invoke(gameController);
                                                    } catch (Exception ignored) {}
                                                }
                                            } catch (Exception ex) {
                                                System.err.println("Failed to inject saved username/display into controller after switch:");
                                                ex.printStackTrace();
                                            }
                                        });
                                    }

                                    // finally deliver the original MATCH_START_INFO message to controller for normal processing
                                    deliverToController(l);
                                } else {
                                    // if controller still null after waiting, just deliver raw message via Platform.runLater to be safe
                                    Platform.runLater(() -> {
                                        try { deliverToController(l); } catch (Exception ignored) {}
                                    });
                                }

                                System.out.print(">>> ");
                                System.out.flush();
                                continue;
                            }

                            // 4) default: if controller is still null and message is not MATCH_START_INFO, just print and keep waiting
                            System.out.print(">>> ");
                            System.out.flush();
                        } catch (Throwable inner) {
                            System.err.println("Error handling incoming server line:");
                            inner.printStackTrace();
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Reader thread ended: " + e.getMessage());
                }
            }, "TestClient-Reader");
            reader.setDaemon(true);
            reader.start();

            // console input loop
            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    System.out.print(">>> ");
                    String cmd = scanner.nextLine();
                    if (cmd == null) break;
                    cmd = cmd.trim();
                    if (cmd.equalsIgnoreCase("quit") || cmd.equalsIgnoreCase("exit")) {
                        cleanup();
                        Platform.exit();
                        break;
                    }
                    if (cmd.isEmpty()) continue;
                    // send raw to server
                    out.println(cmd);
                    Thread.sleep(50);
                }
            }
        } catch (UnknownHostException e) {
            System.err.println("Unknown host");
        } catch (ConnectException e) {
            System.err.println("Connection refused - make sure server is running on port " + SERVER_PORT);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Switch to gameplay UI. Ensures it runs on FX thread.
     */
    private void switchToGameplay() throws Exception {
        // run on FX thread (Platform.runLater caller already manages it), but defend here as well
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> {
                try {
                    switchToGameplay();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            return;
        }

        System.out.println("Switching to gameplay UI...");
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/gameplay.fxml"));
        Scene s = new Scene(loader.load(), 800, 600);
        primaryStage.setScene(s);
        primaryStage.show();
        gameController = loader.getController();
        primaryStage.sizeToScene();           // kích cửa sổ về kích thước scene hiện tại
        primaryStage.centerOnScreen();

        // If we already have username from LOGIN_SUCCESS, use it; otherwise keep a placeholder
        if (localPlayer.getUsername() != null && !localPlayer.getUsername().isEmpty()) {
            final String u = localPlayer.getUsername();
            final String d = localPlayer.getDisplayName() != null ? localPlayer.getDisplayName() : u;
            final String avatar = localPlayer.getAvatarUrl();

            try {
                gameController.setPlayerUsername(u);
                if (avatar != null && !avatar.isEmpty()) {
                    try { gameController.setPlayerAvatarUrl(avatar); } catch (Exception ignored) {}
                }
                // prefer public setter if exists
                try {
                    Method setDisp = gameController.getClass().getMethod("setPlayerDisplayName", String.class);
                    setDisp.invoke(gameController, d);
                } catch (NoSuchMethodException nsme) {
                    try {
                        Field fld = gameController.getClass().getDeclaredField("playerDisplayName");
                        fld.setAccessible(true);
                        fld.set(gameController, d);
                    } catch (Exception ignored) {}
                    try {
                        Method mu = gameController.getClass().getDeclaredMethod("updatePlayerNames");
                        mu.setAccessible(true);
                        mu.invoke(gameController);
                    } catch (Exception ignored) {}
                }
            } catch (Exception ex) {
                System.err.println("Failed to set initial identity on controller during switch:");
                ex.printStackTrace();
            }
        } else {
            gameController.setPlayerUsername("TestPlayer");
        }

        // create a wrapper client that uses this TestClient socket/out
        TestGameplayClientWrapper wrapper = new TestGameplayClientWrapper();
        // give wrapper to controller
        gameController.setGameClient(wrapper);

        System.out.println("Gameplay UI ready and wrapper client installed.");
    }

    /**
     * Deliver an incoming raw server message into the GameplayController.
     * This will parse JSON messages and invoke the appropriate handler method on the controller
     * (using reflection if methods are non-public). All invocations happen on the JavaFX thread.
     */
    private void deliverToController(String message) {
        if (gameController == null || message == null) return;
        Platform.runLater(() -> {
            try {
                System.out.println("Delivering to controller: " + message);

                // JSON message?
                String trimmed = message.trim();
                if (trimmed.startsWith("{")) {
                    JsonElement je = JsonParser.parseString(trimmed);
                    if (je != null && je.isJsonObject()) {
                        JsonObject jo = je.getAsJsonObject();
                        String type = jo.has("type") ? jo.get("type").getAsString() : "";

                        switch (type.toUpperCase()) {
                            case "MATCH_START_INFO": {
                                MatchStartInfo msi = gson.fromJson(trimmed, MatchStartInfo.class);
                                invokeControllerMethod("handleMatchStart", new Class[]{MatchStartInfo.class}, new Object[]{msi});
                                break;
                            }
                            case "NEW_ROUND": {
                                NewRound nr = gson.fromJson(trimmed, NewRound.class);
                                invokeControllerMethod("handleNewRoundWithCountdown", new Class[]{NewRound.class}, new Object[]{nr});
                                break;
                            }
                            case "ANSWER_RESULT": {
                                AnswerResult ar = gson.fromJson(trimmed, AnswerResult.class);
                                invokeControllerMethod("handleAnswerResult", new Class[]{AnswerResult.class}, new Object[]{ar});
                                break;
                            }
                            case "ROUND_RESULT": {
                                RoundResult rr = gson.fromJson(trimmed, RoundResult.class);
                                invokeControllerMethod("handleRoundResult", new Class[]{RoundResult.class}, new Object[]{rr});
                                break;
                            }
                            case "GAME_END":
                            case "GAME_OVER": {
                                // Parse JSON object safely (we already verified it's an object)
                                JsonObject jot = je.getAsJsonObject();

                                // Build RoundResult model from server payload
                                RoundResult rr = buildRoundResultFromJson(jot);

                                // Invoke controller method handleGameEnd(RoundResult)
                                invokeControllerMethod("handleGameEnd", new Class[]{RoundResult.class}, new Object[]{rr});

                                // Also pass raw (clean) JSON string to showMatchResult so existing UI that expects JSON still works
                                try {
                                    invokeControllerMethod("showMatchResult", new Class[]{String.class}, new Object[]{jot.toString()});
                                } catch (Exception ignored) {
                                    // if controller doesn't have showMatchResult, ignore
                                }
                                break;
                            }
                            default: {
                                // Unknown JSON type -> show as feedback
                                invokeControllerMethod("showTemporaryFeedback", new Class[]{String.class,int.class,String.class}, new Object[]{trimmed,3,""});
                                break;
                            }
                        }
                        return;
                    }
                }

                // non-JSON messages (pipe-delimited)
                if (trimmed.startsWith("LOGIN_SUCCESS|") || trimmed.startsWith("LOGIN_SUCCESS |")) {
                    // already handled in reader, but also surface as feedback
                    String payload = trimmed.substring(trimmed.indexOf('|') + 1).trim();
                    invokeControllerMethod("showTemporaryFeedback", new Class[]{String.class,int.class,String.class}, new Object[]{"Login OK: " + payload,3,""});
                } else if (trimmed.startsWith("INFO|")) {
                    String info = trimmed.substring(Math.min(trimmed.length(), 5));
                    invokeControllerMethod("showTemporaryFeedback", new Class[]{String.class,int.class,String.class}, new Object[]{info,3,""});
                } else if (trimmed.startsWith("ERROR|")) {
                    String err = trimmed.substring(Math.min(trimmed.length(), 6));
                    invokeControllerMethod("showTemporaryFeedback", new Class[]{String.class,int.class,String.class}, new Object[]{"Lỗi: " + err,5,""});
                } else if (trimmed.equals("FORFEIT_ACK")) {
                    invokeControllerMethod("showTemporaryFeedback", new Class[]{String.class,int.class,String.class}, new Object[]{"Đã thoát trận đấu",3,""});
                } else {
                    invokeControllerMethod("showTemporaryFeedback", new Class[]{String.class,int.class,String.class}, new Object[]{trimmed,2,""});
                }
            } catch (Throwable ex) {
                System.err.println("deliverToController error:");
                ex.printStackTrace();
            }
        });
    }

    /**
     * Reflection helper: try to find method (public or declared), set accessible if necessary and invoke.
     */
    private void invokeControllerMethod(String name, Class<?>[] paramTypes, Object[] args) {
        try {
            Method m;
            try {
                m = gameController.getClass().getMethod(name, paramTypes);
            } catch (NoSuchMethodException e) {
                m = gameController.getClass().getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
            }
            m.invoke(gameController, args);
        } catch (NoSuchMethodException nsme) {
            // method not found on controller - just log
            System.out.println("Controller does not implement " + name);
        } catch (Exception e) {
            System.err.println("invokeControllerMethod error for " + name + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void cleanup() {
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (in != null) in.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        System.out.println("Disconnected.");
    }

    /**
     * Extract a balanced JSON object substring starting at the first '{'.
     * Returns null if no balanced object found.
     */
    private String extractJsonObject(String s) {
        if (s == null) return null;
        int start = s.indexOf('{');
        if (start < 0) return null;
        boolean inString = false;
        boolean escape = false;
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return s.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Parse JSON with a lenient JsonReader so some malformed JSON can still be accepted.
     */
    private JsonElement parseJsonLenient(String json) throws IOException {
        if (json == null) return null;
        JsonReader jr = new JsonReader(new StringReader(json));
        jr.setLenient(true);
        return JsonParser.parseReader(jr);
    }

    /**
     * Wrapper that uses TestClient's socket/out for send operations.
     * It does NOT open its own socket; connect() is a no-op.
     */
    private class TestGameplayClientWrapper extends NetworkGameplay {
        public TestGameplayClientWrapper() {
            super("localhost", 8888, msg -> {
                // raw consumer - not used as we forward server lines to controller directly
                System.out.println("Wrapper onMessage: " + msg);
            });
        }

        @Override
        public void connect() { System.out.println("Wrapper connect(): using existing TestClient socket"); }

        @Override
        public boolean isConnected() {
            try {
                return socket != null && socket.isConnected() && !socket.isClosed();
            } catch (Exception ex) { return false; }
        }

        @Override
        public synchronized void sendRaw(String message) {
            if (out != null) {
                out.println(message);
                System.out.println(">>> GAME: " + message);
            } else {
                System.err.println("Wrapper sendRaw: no connection");
            }
        }

        @Override
        public void disconnect() {
            cleanup();
        }

        /**
         * Build a RoundResult (your model) from server GAME_OVER / GAME_END JsonObject.
         */
    }
    private RoundResult buildRoundResultFromJson(JsonObject jo) {
        RoundResult rr = new RoundResult();

        try {
            // round_index: not provided directly in GAME_OVER root; set -1 or last round index if available
            rr.round_index = -1;

            // round_number: try question_count or compute from round_history
            int roundNumber = 0;
            if (jo.has("question_count") && !jo.get("question_count").isJsonNull()) {
                try { roundNumber = jo.get("question_count").getAsInt(); } catch (Exception ignored) {}
            }

            // Parse round_history to find highest round index if question_count not present
            JsonObject roundHistory = null;
            if (jo.has("round_history") && jo.get("round_history").isJsonObject()) {
                roundHistory = jo.getAsJsonObject("round_history");
                int maxIdx = -1;
                for (var entry : roundHistory.entrySet()) {
                    if (entry.getValue().isJsonArray()) {
                        for (JsonElement e : entry.getValue().getAsJsonArray()) {
                            if (e.isJsonObject() && e.getAsJsonObject().has("round_index")) {
                                try {
                                    int idx = e.getAsJsonObject().get("round_index").getAsInt();
                                    if (idx > maxIdx) maxIdx = idx;
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }
                if (roundNumber == 0 && maxIdx >= 0) roundNumber = maxIdx + 1;
            }
            rr.round_number = roundNumber;

            // round_winner if present
            rr.round_winner = (jo.has("winner") && !jo.get("winner").isJsonNull()) ? jo.get("winner").getAsString() : null;

            // Scores map
            Map<String, Long> scoresMap = new LinkedHashMap<>();
            if (jo.has("scores") && jo.get("scores").isJsonObject()) {
                for (var e : jo.getAsJsonObject("scores").entrySet()) {
                    try { scoresMap.put(e.getKey(), e.getValue().getAsLong()); } catch (Exception ignored) {}
                }
            }

            // Total play time map
            Map<String, Long> timesMap = new HashMap<>();
            if (jo.has("total_play_time_ms") && jo.get("total_play_time_ms").isJsonObject()) {
                for (var e : jo.getAsJsonObject("total_play_time_ms").entrySet()) {
                    try { timesMap.put(e.getKey(), e.getValue().getAsLong()); } catch (Exception ignored) {}
                }
            }

            // Player info map (id -> username/display_name) from "players" array if available
            Map<String, String> idToName = new LinkedHashMap<>();
            if (jo.has("players") && jo.get("players").isJsonArray()) {
                for (JsonElement pel : jo.getAsJsonArray("players")) {
                    if (!pel.isJsonObject()) continue;
                    JsonObject p = pel.getAsJsonObject();
                    String id = null, username = null, display = null;
                    try { if (p.has("id") && !p.get("id").isJsonNull()) id = p.get("id").getAsString(); } catch (Exception ignored) {}
                    try { if (p.has("username") && !p.get("username").isJsonNull()) username = p.get("username").getAsString(); } catch (Exception ignored) {}
                    try { if (p.has("display_name") && !p.get("display_name").isJsonNull()) display = p.get("display_name").getAsString(); } catch (Exception ignored) {}
                    if (id != null) {
                        String name = (display != null && !display.isEmpty()) ? display : (username != null ? username : id);
                        idToName.put(id, name);
                    }
                }
            }

            // If players not provided, infer IDs from scoresMap ordering
            if (idToName.isEmpty() && !scoresMap.isEmpty()) {
                int i = 0;
                for (var entry : scoresMap.entrySet()) {
                    String id = entry.getKey();
                    idToName.put(id, "Người chơi " + (i == 0 ? "A" : "B"));
                    i++;
                    if (idToName.size() >= 2) break;
                }
            }

            // Round history object for per-player per-round info
            Map<String, JsonArray> rhMap = new HashMap<>();
            if (roundHistory != null) {
                for (var entry : roundHistory.entrySet()) {
                    if (entry.getValue().isJsonArray()) rhMap.put(entry.getKey(), entry.getValue().getAsJsonArray());
                }
            }

            // Build players list for RoundResult
            rr.players = new ArrayList<>();
            // Use keys from idToName first; if empty, use union of scoresMap keys and rhMap keys
            Set<String> ids = new LinkedHashSet<>();
            ids.addAll(idToName.keySet());
            ids.addAll(scoresMap.keySet());
            ids.addAll(rhMap.keySet());

            for (String id : ids) {
                com.mathspeed.model.RoundResult.PlayerResult pr = new com.mathspeed.model.RoundResult.PlayerResult();
                pr.id = id;
                pr.username = idToName.getOrDefault(id, id);

                // total score
                long s = scoresMap.getOrDefault(id, 0L);
                pr.total_score = (int) s;

                // total play time
                pr.total_play_time_ms = timesMap.getOrDefault(id, 0L);

                // Determine the last round entry for this player (to fill correct + round_play_time_ms)
                JsonArray arr = rhMap.get(id);
                if (arr != null && arr.size() > 0) {
                    // find the entry with highest round_index (in case not ordered)
                    JsonObject last = null;
                    int maxIdx = Integer.MIN_VALUE;
                    for (JsonElement e : arr) {
                        if (!e.isJsonObject()) continue;
                        JsonObject joEntry = e.getAsJsonObject();
                        int idx = -1;
                        try { if (joEntry.has("round_index") && !joEntry.get("round_index").isJsonNull()) idx = joEntry.get("round_index").getAsInt(); } catch (Exception ignored) {}
                        if (idx > maxIdx || last == null) { last = joEntry; maxIdx = idx; }
                    }
                    if (last != null) {
                        try { pr.correct = last.has("correct") && !last.get("correct").isJsonNull() && last.get("correct").getAsBoolean(); } catch (Exception ignored) {}
                        try { pr.round_play_time_ms = last.has("round_play_time_ms") && !last.get("round_play_time_ms").isJsonNull() ? last.get("round_play_time_ms").getAsLong() : 0L; } catch (Exception ignored) {}
                    } else {
                        pr.correct = false;
                        pr.round_play_time_ms = 0L;
                    }
                } else {
                    // no round entries -> fallback: set correct=false and round_play_time_ms=0
                    pr.correct = false;
                    pr.round_play_time_ms = 0L;
                }

                rr.players.add(pr);
            }
        } catch (Exception ex) {
            System.err.println("buildRoundResultFromJson: error while mapping JO to RoundResult: " + ex.getMessage());
            ex.printStackTrace();
        }

        return rr;
    }
}