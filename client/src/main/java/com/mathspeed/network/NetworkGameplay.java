package com.mathspeed.network;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mathspeed.model.AnswerResult;
import com.mathspeed.model.MatchStartInfo;
import com.mathspeed.model.NewRound;
import com.mathspeed.model.RoundResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * NetworkGameplay
 *
 * Responsibilities:
 *  - manage TCP socket + reader thread
 *  - parse JSON messages (by "type") and dispatch to typed handlers
 *  - fallback to onMessage for raw/unhandled messages (backwards compatibility)
 *
 *  Provides convenient send* methods for commands (LOGIN, CHALLENGE, ACCEPT, READY, ANSWER, FORFEIT ...)
 *
 * Note: typed handlers may be invoked on reader thread — caller should marshal to FX thread as needed.
 */
public class NetworkGameplay {

    private final String host;
    private final int port;
    private final Consumer<String> onMessage;            // raw / general messages
    private Consumer<MatchStartInfo> onMatchStart;       // typed handler for MATCH_START_INFO

    // additional typed handlers (set via setters)
    private Consumer<NewRound> onNewRound;
    private Consumer<AnswerResult> onAnswerResult;
    private Consumer<RoundResult> onRoundResult;
    private Consumer<RoundResult> onGameEnd;
    private Consumer<TimePong> onTimePong;
    private Consumer<ServerNow> onServerNow;
    private Consumer<String> onPlayerListUpdate; // e.g. "PLAYER_LIST_UPDATE|..."

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread readerThread;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final Gson gson = new Gson();

    public NetworkGameplay(String host, int port, Consumer<String> onMessage) {
        this(host, port, onMessage, null);
    }

    public NetworkGameplay(String host, int port, Consumer<String> onMessage, Consumer<MatchStartInfo> onMatchStart) {
        this.host = Objects.requireNonNull(host);
        this.port = port;
        this.onMessage = (onMessage != null) ? onMessage : s -> { };
        this.onMatchStart = onMatchStart;
    }

    /**
     * Connects to the server and starts a background reader for incoming messages.
     * @throws IOException if connection fails
     */
    public void connect() throws IOException {
        if (connected.get()) return;
        socket = new Socket(host, port);
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        connected.set(true);

        readerThread = new Thread(this::readLoop, "NetworkGameplay-Reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readLoop() {
        try {
            String line;
            while (connected.get() && (line = in.readLine()) != null) {
                processMessage(line);
            }
        } catch (IOException ignored) {
            // connection closed or error; handled by disconnect()
        } finally {
            disconnect();
        }
    }

    /**
     * Core message dispatch.
     * - Logs raw
     * - If JSON with "type", tries to parse into known models and calls typed handlers
     * - Always forwards raw string to onMessage for backward compatibility
     */
    private void processMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        String trimmed = message.trim();

        // Debug: always print raw incoming message (helps troubleshooting field name mismatches)
        System.out.println("RAW <== " + trimmed);

        // Quick non-JSON prefix handling (server sometimes sends pipe-delimited messages)
        try {
            if (!trimmed.startsWith("{")) {
                // handle common non-JSON patterns explicitly
                if (trimmed.startsWith("PLAYER_LIST_UPDATE|")) {
                    if (onPlayerListUpdate != null) {
                        try { onPlayerListUpdate.accept(trimmed); } catch (Exception ex) { /* swallow */ }
                    } else {
                        // fallback to raw consumer
                        try { onMessage.accept(trimmed); } catch (Exception ignored) {}
                    }
                    return;
                }

                if (trimmed.startsWith("INFO|") || trimmed.startsWith("ERROR|") || trimmed.equals("FORFEIT_ACK")) {
                    // let raw handler deal with INFO/ERROR/FORFEIT_ACK formats
                    try { onMessage.accept(trimmed); } catch (Exception ignored) {}
                    return;
                }

                // Some servers use CHALLENGE_REQUEST|username|param and CHALLENGE_ACCEPTED|...
                if (trimmed.startsWith("CHALLENGE_REQUEST|") || trimmed.startsWith("CHALLENGE_ACCEPTED|") ||
                        trimmed.startsWith("ACCEPT|") || trimmed.startsWith("DECLINE|")) {
                    // No typed handler by default here; forward raw for LobbyController/TestClient to handle.
                    try { onMessage.accept(trimmed); } catch (Exception ignored) {}
                    return;
                }

                // not a JSON message we special-case — forward to raw handler
                try { onMessage.accept(trimmed); } catch (Exception ignored) {}
                return;
            }
        } catch (Exception ex) {
            // If anything goes wrong in non-JSON quick path, continue to try JSON parsing below
            System.err.println("processMessage early-path error: " + ex.getMessage());
        }

        // JSON path: try to parse and dispatch typed handlers, then always forward raw to onMessage
        try {
            if (trimmed.startsWith("{") && trimmed.contains("\"type\"")) {
                JsonElement je;
                try {
                    je = JsonParser.parseString(trimmed);
                } catch (JsonSyntaxException jse) {
                    // Malformed JSON — forward raw and exit
                    System.err.println("processMessage: invalid JSON: " + jse.getMessage());
                    try { onMessage.accept(trimmed); } catch (Exception ignored) {}
                    return;
                }

                if (je != null && je.isJsonObject()) {
                    JsonObject jo = je.getAsJsonObject();
                    String type = jo.has("type") ? jo.get("type").getAsString() : "";
                    if (type == null) type = "";

                    switch (type.toUpperCase()) {
                        case "MATCH_START_INFO": {
                            try {
                                MatchStartInfo msi = gson.fromJson(trimmed, MatchStartInfo.class);
                                if (msi != null && onMatchStart != null) onMatchStart.accept(msi);
                            } catch (JsonSyntaxException ex) {
                                System.err.println("processMessage: MATCH_START_INFO parse error: " + ex.getMessage());
                            }
                            break;
                        }
                        case "NEW_ROUND": {
                            try {
                                NewRound nr = gson.fromJson(trimmed, NewRound.class);
                                if (nr != null && onNewRound != null) onNewRound.accept(nr);
                            } catch (JsonSyntaxException ex) {
                                System.err.println("processMessage: NEW_ROUND parse error: " + ex.getMessage());
                            }
                            break;
                        }
                        case "ANSWER_RESULT": {
                            try {
                                AnswerResult ar = gson.fromJson(trimmed, AnswerResult.class);
                                if (ar != null && onAnswerResult != null) onAnswerResult.accept(ar);
                            } catch (JsonSyntaxException ex) {
                                System.err.println("processMessage: ANSWER_RESULT parse error: " + ex.getMessage());
                            }
                            break;
                        }
                        case "ROUND_RESULT": {
                            try {
                                RoundResult rr = gson.fromJson(trimmed, RoundResult.class);
                                if (rr != null && onRoundResult != null) onRoundResult.accept(rr);
                            } catch (JsonSyntaxException ex) {
                                System.err.println("processMessage: ROUND_RESULT parse error: " + ex.getMessage());
                            }
                            break;
                        }
                        case "GAME_END":
                        case "GAME_OVER": {
                            try {
                                RoundResult end = gson.fromJson(trimmed, RoundResult.class);
                                if (end != null && onGameEnd != null) onGameEnd.accept(end);
                            } catch (JsonSyntaxException ex) {
                                System.err.println("processMessage: GAME_END parse error: " + ex.getMessage());
                            }
                            break;
                        }
                        case "TIME_PONG":
                        case "time_pong": {
                            try {
                                TimePong tp = gson.fromJson(trimmed, TimePong.class);
                                if (tp != null && onTimePong != null) onTimePong.accept(tp);
                            } catch (JsonSyntaxException ex) {
                                System.err.println("processMessage: TIME_PONG parse error: " + ex.getMessage());
                            }
                            break;
                        }
                        case "SERVER_NOW": {
                            try {
                                ServerNow sn = gson.fromJson(trimmed, ServerNow.class);
                                if (sn != null && onServerNow != null) onServerNow.accept(sn);
                            } catch (JsonSyntaxException ex) {
                                System.err.println("processMessage: SERVER_NOW parse error: " + ex.getMessage());
                            }
                            break;
                        }
                        case "PLAYER_LIST_UPDATE": {
                            // sometimes server may send JSON with structured player list
                            if (onPlayerListUpdate != null) {
                                try { onPlayerListUpdate.accept(trimmed); } catch (Exception ignored) {}
                            }
                            break;
                        }
                        default:
                            // Unknown JSON type — fall back to raw handler below
                            break;
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("processMessage unexpected error: " + ex.getMessage());
        } finally {
            // Always forward raw (backwards compatibility)
            try {
                onMessage.accept(trimmed);
            } catch (Exception ex) {
                // swallow handler exceptions
            }
        }
    }

    // ---------------- send convenience methods ----------------

    public synchronized void sendRaw(String message) {
        if (!isConnected() || out == null) {
            System.err.println("Cannot send message - not connected");
            return;
        }
        try {
            out.println(message);
            if (out.checkError()) {
                System.err.println("Error sending message: " + message);
                disconnect();
            }
        } catch (Exception e) {
            System.err.println("Exception sending message: " + e.getMessage());
            disconnect();
        }
    }

    public void sendLogin(String username) {
        sendRaw("LOGIN " + username);
    }

    public void sendChallenge(String opponentUsername, int rounds, int target) {
        // Protocol depends on server; example:
        sendRaw("CHALLENGE " + opponentUsername + " " + target + " " + rounds);
    }

    public void sendAccept(String challengerUsername) {
        sendRaw("ACCEPT " + challengerUsername);
    }

    public void sendReady() {
        sendRaw("READY");
    }

    public void sendSubmitAnswer(String expr) {
        if (expr == null) return;
        sendRaw("ANSWER " + expr);
    }

    public void sendForfeit() {
        sendRaw("FORFEIT");
    }

    public void sendTimePing(long clientTs) {
        sendRaw("TIME_PING " + clientTs);
    }

    public void sendExit() {
        sendRaw("LOGOUT");
        disconnect();
    }

    public synchronized void disconnect() {
        if (!connected.compareAndSet(true, false)) return;
        try {
            if (readerThread != null) readerThread.interrupt();
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Error during disconnect: " + e.getMessage());
        } finally {
            socket = null;
            out = null;
            in = null;
            readerThread = null;
        }
    }

    public boolean isConnected() { return connected.get(); }

    // --- setters for typed handlers ---

    public void setMatchStartHandler(Consumer<MatchStartInfo> handler) { this.onMatchStart = handler; }
    public void setNewRoundHandler(Consumer<NewRound> handler) { this.onNewRound = handler; }
    public void setAnswerResultHandler(Consumer<AnswerResult> handler) { this.onAnswerResult = handler; }
    public void setRoundResultHandler(Consumer<RoundResult> handler) { this.onRoundResult = handler; }
    public void setGameEndHandler(Consumer<RoundResult> handler) { this.onGameEnd = handler; }
    public void setTimePongHandler(Consumer<TimePong> handler) { this.onTimePong = handler; }
    public void setServerNowHandler(Consumer<ServerNow> handler) { this.onServerNow = handler; }
    public void setPlayerListUpdateHandler(Consumer<String> handler) { this.onPlayerListUpdate = handler; }

    // helper time message classes
    public static class TimePong { public String type; public Long client_send; public Long server_time; }
    public static class ServerNow { public String type; public Long server_time; }
}