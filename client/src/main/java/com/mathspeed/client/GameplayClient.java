package com.mathspeed.client;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.mathspeed.model.MatchStartInfo;

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

public class GameplayClient {

    private final String host;
    private final int port;
    private final Consumer<String> onMessage;
    private final Consumer<MatchStartInfo> onMatchStart;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread readerThread;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final Gson gson = new Gson();

    public GameplayClient(String host, int port, Consumer<String> onMessage) {
        this(host, port, onMessage, null);
    }

    public GameplayClient(String host, int port, Consumer<String> onMessage, Consumer<MatchStartInfo> onMatchStart) {
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

        readerThread = new Thread(this::readLoop, "GameplayClient-Reader");
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

    private void processMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        String trimmed = message.trim();

        // Check if it's a JSON message
        if (trimmed.startsWith("{") && trimmed.contains("\"type\"")) {
            try {
                // Try to parse as MatchStartInfo first
                if (trimmed.contains("\"type\":\"MATCH_START_INFO\"")) {
                    MatchStartInfo matchInfo = gson.fromJson(trimmed, MatchStartInfo.class);
                    handleMatchStartInfo(matchInfo);
                    return;
                }

                // Add other JSON message types here as needed

            } catch (JsonSyntaxException e) {
                System.err.println("Failed to parse JSON message: " + e.getMessage());
                System.err.println("Raw message: " + trimmed);
            }
        }

        // For non-JSON messages or unhandled JSON, pass to general handler
        onMessage.accept(message);
    }

    private void handleMatchStartInfo(MatchStartInfo matchInfo) {
        if (matchInfo == null) {
            System.err.println("Received null MatchStartInfo");
            return;
        }

        System.out.println("Match Start Info Received:");
        System.out.println("   Seed: " + matchInfo.seed);
        System.out.println("   Start Time: " + matchInfo.start_time);
        System.out.println("   Server Time: " + matchInfo.server_time);
        System.out.println("   Countdown: " + matchInfo.countdown_ms + "ms");
        System.out.println("   Questions: " + matchInfo.question_count);
        System.out.println("   Time per question: " + matchInfo.per_question_seconds + "s");

        if (matchInfo.players != null) {
            System.out.println("   Players:");
            for (int i = 0; i < matchInfo.players.size(); i++) {
                var player = matchInfo.players.get(i);
                System.out.println("     " + (i + 1) + ". " + player.getDisplayName() +
                                 " (" + player.getUsername() + ") - ID: " + player.getId());
            }
        }

        // Call specific match start handler if provided
        if (onMatchStart != null) {
            onMatchStart.accept(matchInfo);
        }

        // Also pass to general message handler for backward compatibility
        onMessage.accept(gson.toJson(matchInfo));
    }

    /**
     * Send a submit answer message to server using the required format:
     * SUBMIT-ANSWER <expression>
     * Example: SUBMIT_ANSWER 2+3*4
     */
    public void sendSubmitAnswer(String expression) {
        if (expression == null) return;
        sendRaw("SUBMIT_ANSWER " + expression);
    }

    /**
     * Send exit message to server and close connection.
     * Uses MessageType-like string: LOGOUT
     */
    public void sendExit() {
        sendRaw("LOGOUT");
        // give the server a moment to process then close locally
        disconnect();
    }

    /**
     * Send a raw message to the server.
     * @param message the message to send
     */
    public synchronized void sendRaw(String message) {
        if (!connected.get() || out == null) {
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

    /**
     * Disconnect from the server and clean up resources.
     */
    public synchronized void disconnect() {
        if (!connected.compareAndSet(true, false)) {
            return; // already disconnected
        }

        try {
            if (readerThread != null) {
                readerThread.interrupt();
            }
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error during disconnect: " + e.getMessage());
        } finally {
            socket = null;
            out = null;
            in = null;
            readerThread = null;
        }

        System.out.println("🔌 Disconnected from server");
    }

    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Set a specific handler for MatchStartInfo messages.
     * This allows for type-safe handling of match start events.
     */
    public void setMatchStartHandler(Consumer<MatchStartInfo> handler) {
        // Note: This would require making onMatchStart non-final,
        // or creating a new constructor/builder pattern
    }
}