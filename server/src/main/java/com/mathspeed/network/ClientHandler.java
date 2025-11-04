package com.mathspeed.network;

import com.mathspeed.server.service.ChallengeManager;
import com.mathspeed.server.service.ClientRegistry;
import com.mathspeed.server.service.Matchmaker;
import com.mathspeed.server.service.GameSession;
import com.mathspeed.dao.PlayerDAO;
import com.mathspeed.model.Player;
import com.mathspeed.protocol.MessageType;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ClientHandler: xử lý 1 kết nối client trên 1 worker thread.
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ClientRegistry clientRegistry;
    private final Matchmaker matchmaker;
    private final ChallengeManager challengeManager;
    private final PlayerDAO playerDAO;

    // persistent writer for outbound messages (thread-safe usage via synchronized sendType/sendMessage)
    private BufferedWriter out;
    private BufferedReader in;

    // current authenticated username (null nếu chưa login)
    private volatile String username = null;
    // current authenticated user id (UUID string) - set on successful login
    private volatile String userId = null;

    // current game/session reference (may be null)
    private final AtomicReference<GameSession> currentGame = new AtomicReference<>(null);

    // flag to stop loop
    private volatile boolean running = true;

    public ClientHandler(Socket socket,
                         ClientRegistry clientRegistry,
                         Matchmaker matchmaker,
                         ChallengeManager challengeManager,
                         PlayerDAO playerDAO) {
        this.socket = socket;
        this.clientRegistry = clientRegistry;
        this.matchmaker = matchmaker;
        this.challengeManager = challengeManager;
        this.playerDAO = playerDAO;
    }

    @Override
    public void run() {
        String remote = socket.getRemoteSocketAddress() != null ? socket.getRemoteSocketAddress().toString() : "unknown";
        System.out.println("ClientHandler started for " + remote + " on " + Thread.currentThread().getName());

        try {
            socket.setSoTimeout(120_000); // default read timeout; can be adjusted
        } catch (IOException ignored) {}

        try (InputStream is = socket.getInputStream();
             InputStreamReader isr = new InputStreamReader(is);
             BufferedReader br = new BufferedReader(isr);
             OutputStream os = socket.getOutputStream();
             OutputStreamWriter osw = new OutputStreamWriter(os);
        ) {
            this.in = br;
            this.out = new BufferedWriter(osw);

            String line;
            while (running && (line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // split by whitespace for command + args
                String[] parts = line.split(" ", 3);
                String cmd = parts[0].toUpperCase();

                try {
                    switch (cmd) {
                        case "LOGIN":
                            handleLogin(parts);
                            break;
                        case "JOIN_QUEUE":
                            handleJoinQueue();
                            break;
                        case "LEAVE_QUEUE":
                            handleLeaveQueue();
                            break;
                        case "CHALLENGE":
                            handleChallenge(parts);
                            break;
                        case "ACCEPT":
                            handleAccept(parts);
                            break;
                        case "DECLINE":
                            handleDecline(parts);
                            break;
                        case "SUBMIT_ANSWER":
                            handleSubmitAnswer(parts);
                            break;
                        case "PING":
                            sendType(MessageType.PONG, null);
                            break;
                        case "QUIT":
                            sendType(MessageType.DISCONNECT, null);
                            running = false; // will exit loop and cleanup
                            break;
                        default:
                            // Unknown command
                            sendType(MessageType.ERROR, "Unknown command");
                            break;
                    }
                } catch (Exception e) {
                    // Protect handler thread: log and send error to client
                    System.err.println("Error processing command from " + remote + ": " + e.getMessage());
                    e.printStackTrace();
                    try { sendType(MessageType.ERROR, "Internal server error"); } catch (Exception ignored) {}
                }
            }
        } catch (SocketException se) {
            // Socket closed/timeout
            System.out.println("Socket exception for " + remote + ": " + se.getMessage());
        } catch (IOException ioe) {
            System.err.println("I/O error for client " + remote + ": " + ioe.getMessage());
        } finally {
            cleanup();
            System.out.println("ClientHandler stopped for " + remote);
        }
    }

    // -----------------------
    // Command handlers
    // -----------------------

    private void handleLogin(String[] parts) {
        if (parts.length < 3) {
            sendType(MessageType.LOGIN_FAILED, "Usage: LOGIN <username> <password>");
            return;
        }
        if (username != null) {
            sendType(MessageType.ERROR, "Already logged in");
            return;
        }
        String user = parts[1].trim();
        String pass = parts[2].trim();

        // authenticate via PlayerDAO.login(username, password) -> Player or null
        Player p;
        try {
            p = playerDAO.login(user, pass);
        } catch (Exception e) {
            sendType(MessageType.LOGIN_FAILED, "Auth failure");
            return;
        }
        if (p == null) {
            sendType(MessageType.LOGIN_FAILED, "Invalid credentials");
            return;
        }

        // set username and userId from Player object
        this.username = p.getUsername();
        this.userId = p.getId(); // IMPORTANT: store userId for DB operations

        // try register into registry (registry likely tracks by username; adjust if registry uses id)
        boolean registered = clientRegistry.registerClient(username, this);
        if (!registered) {
            // rollback local auth state
            this.username = null;
            this.userId = null;
            sendType(MessageType.LOGIN_FAILED, "User already online");
            return;
        }

        // update last login (best-effort)
        try {
            playerDAO.updateLastLogin(this.username);
        } catch (Exception e) {
            // non-fatal: log it
            System.err.println("updateLastLogin failed for " + username + ": " + e.getMessage());
        }

        // broadcast updated online players
        clientRegistry.broadcastOnlinePlayers();

        sendType(MessageType.LOGIN_SUCCESS, null);
    }

    private void handleJoinQueue() {
        if (!ensureLoggedIn()) return;
        matchmaker.joinQueue(this);
        sendType(MessageType.QUEUE_JOINED, null);
    }

    private void handleLeaveQueue() {
        if (!ensureLoggedIn()) return;
        matchmaker.leaveQueue(this);
        sendType(MessageType.QUEUE_LEFT, null);
    }

    private void handleChallenge(String[] parts) {
        if (!ensureLoggedIn()) return;
        if (parts.length < 2) {
            sendType(MessageType.ERROR, "CHALLENGE usage: CHALLENGE <target>");
            return;
        }
        String target = parts[1].trim();
        if (target.isEmpty()) {
            sendType(MessageType.ERROR, "Invalid target");
            return;
        }
        // Use ChallengeManager to send challenge
        challengeManager.sendChallenge(username, target);
        sendType(MessageType.CHALLENGE_SENT, target);
    }

    private void handleAccept(String[] parts) {
        if (!ensureLoggedIn()) return;
        if (parts.length < 2) {
            sendType(MessageType.ERROR, "ACCEPT usage: ACCEPT <challenger>");
            return;
        }
        String challenger = parts[1].trim();
        if (challenger.isEmpty()) {
            sendType(MessageType.ERROR, "Invalid challenger");
            return;
        }
        challengeManager.acceptChallenge(username, challenger);
    }

    private void handleDecline(String[] parts) {
        if (!ensureLoggedIn()) return;
        if (parts.length < 2) {
            sendType(MessageType.ERROR, "DECLINE usage: DECLINE <challenger>");
            return;
        }
        String challenger = parts[1].trim();
        if (challenger.isEmpty()) {
            sendType(MessageType.ERROR, "Invalid challenger");
            return;
        }
        challengeManager.declineChallenge(challenger, username);
    }

    private void handleSubmitAnswer(String[] parts) {
        if (!ensureLoggedIn()) return;
        // Expected: SUBMIT_ANSWER <questionId> <answer>
        if (parts.length < 3) {
            sendType(MessageType.ERROR, "SUBMIT_ANSWER usage: SUBMIT_ANSWER <questionId> <answer>");
            return;
        }
        String questionId = parts[1].trim();
        String answer = parts[2].trim();

        GameSession gs = currentGame.get();
        if (gs == null) {
            sendType(MessageType.ERROR, "Not in a game");
            return;
        }
        // Prefer using userId when submitting (GameSession expects username or id mapping — ensure it accepts userId)
        boolean accepted = gs.submitAnswer(getUserIdOrUsername(), questionId, answer);
        if (!accepted) {
            sendType(MessageType.ERROR, "Answer not accepted (maybe timeout or wrong question)");
        }
    }

    // -----------------------
    // Utilities
    // -----------------------

    public String getUsername() {
        return username;
    }

    /**
     * Return the authenticated user's UUID string if available; otherwise fallback to username.
     */
    public String getUserId(){
        return userId;
    }

    /**
     * Helper: return userId if present, otherwise username.
     */
    public String getUserIdOrUsername() {
        return userId != null ? userId : username;
    }

    private boolean ensureLoggedIn() {
        if (username == null) {
            sendType(MessageType.ERROR, "Not authenticated. Send: LOGIN <username> <password>");
            return false;
        }
        return true;
    }

    /**
     * Send a message to client as "TYPE" or "TYPE|payload". Thread-safe.
     */
    public synchronized void sendType(MessageType type, String payload) {
        if (out == null) return;
        StringBuilder sb = new StringBuilder(type.name());
        if (payload != null && !payload.isEmpty()) {
            sb.append("|").append(payload);
        }
        String message = sb.toString();
        try {
            out.write(message);
            if (!message.endsWith("\n")) out.write("\n");
            out.flush();
        } catch (IOException e) {
            System.err.println("Failed to send to " + (username != null ? username : "unknown") + ": " + e.getMessage());
            // sending failed: consider disconnecting this client
            try { disconnect(); } catch (Exception ignored) {}
        }
    }

    /**
     * Backwards-compatible helper (keeps previous API)
     */
    public void sendMessage(String message) {
        // If caller sends raw text, try to pass through unchanged
        sendRaw(message);
    }

    private synchronized void sendRaw(String message) {
        if (out == null) return;
        try {
            out.write(message);
            if (!message.endsWith("\n")) out.write("\n");
            out.flush();
        } catch (IOException e) {
            System.err.println("Failed to send to " + (username != null ? username : "unknown") + ": " + e.getMessage());
            try { disconnect(); } catch (Exception ignored) {}
        }
    }

    /**
     * Disconnect client explicitly (close socket and trigger cleanup).
     */
    public void disconnect() {
        running = false;
        try { socket.close(); } catch (IOException ignored) {}
    }

    /**
     * Return current game reference (may be null).
     * Server-side GameSessionManager will set this when session created/ended.
     */
    public GameSession getCurrentGame() {
        return currentGame.get();
    }

    /**
     * Set current game reference (called by GameSessionManager when session created/ended).
     */
    public void setCurrentGame(GameSession game) {
        currentGame.set(game);
    }

    /**
     * Cleanup resources and unregister client.
     */
    private void cleanup() {
        // unregister from registry and persist logout
        if (username != null) {
            try {
                clientRegistry.removeClient(username);
            } catch (Exception e) {
                System.err.println("Error removing client " + username + ": " + e.getMessage());
            }
            try {
                playerDAO.logout(username);
            } catch (Exception e) {
                System.err.println("Error logging out " + username + ": " + e.getMessage());
            }
        }

        // notify matchmaker about disconnect
        try {
            matchmaker.leaveQueue(this);
        } catch (Exception ignored) {}

        // Optionally notify challenge manager if it needs explicit disconnect handling
        try {
            // challengeManager.notifyDisconnect(username); // implement if needed
        } catch (Exception ignored) {}

        // close streams/socket if not already closed
        try {
            if (out != null) out.close();
        } catch (IOException ignored) {}
        try {
            if (in != null) in.close();
        } catch (IOException ignored) {}
        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}