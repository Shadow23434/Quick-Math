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
import java.net.SocketTimeoutException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Robust ClientHandler: safer resource management and non-fatal read timeouts.
 *
 * Changes applied to avoid "Unhandled exception from auto-closeable resource: java.io.IOException":
 * - Do NOT rely on try-with-resources for socket streams here (close explicitly in finally with try/catch).
 * - Catch SocketTimeoutException separately and treat it as non-fatal (log & continue).
 * - Catch and log IOExceptions thrown either during read loop or during close operations.
 * - Ensure cleanup() is idempotent and safe to call even if streams were already closed.
 *
 * Keep other command handling logic unchanged.
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
            // keep a reasonably large read timeout to avoid spurious timeouts during gameplay
            socket.setSoTimeout(120_000); // 120 seconds
        } catch (IOException ignored) {}

        InputStream is = null;
        InputStreamReader isr = null;
        BufferedReader br = null;
        OutputStream os = null;
        OutputStreamWriter osw = null;
        BufferedWriter bw = null;

        try {
            is = socket.getInputStream();
            isr = new InputStreamReader(is);
            br = new BufferedReader(isr);
            os = socket.getOutputStream();
            osw = new OutputStreamWriter(os);
            bw = new BufferedWriter(osw);

            this.in = br;
            this.out = bw;

            // Testing convenience: try to auto-assign "alice" or "bob" if client hasn't logged in.
            autoAssignTestUsernameIfNeeded();

            String line;
            // Read loop: treat SocketTimeoutException as non-fatal (continue waiting).
            while (running) {
                try {
                    line = br.readLine();
                    if (line == null) {
                        // client closed connection cleanly
                        break;
                    }
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
                            case "GUEST":
                                handleGuest(parts);
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
                } catch (SocketTimeoutException ste) {
                    // Read timed out waiting for client activity.
                    // This is NOT fatal by itself for our use-case: the client may be idle between questions.
                    // Log at debug level and continue waiting.
                    System.out.println("Read timeout for " + remote + " (ignoring one timeout and continuing).");
                    continue;
                } catch (SocketException se) {
                    // Socket closed or other socket-level error; break and cleanup
                    System.out.println("Socket exception for " + remote + ": " + se.getMessage());
                    break;
                } catch (IOException ioe) {
                    // Other I/O error -> log and break (will cleanup)
                    System.err.println("I/O error for client " + remote + ": " + ioe.getMessage());
                    ioe.printStackTrace();
                    break;
                }
            }
        } catch (Exception e) {
            // Catch any unexpected runtime exceptions to avoid thread death
            System.err.println("Unexpected exception in ClientHandler for " + remote + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Close resources explicitly, each in its own try/catch to avoid unhandled exceptions during close()
            try { if (bw != null) bw.close(); } catch (IOException e) { System.err.println("Error closing writer: " + e.getMessage()); }
            try { if (osw != null) osw.close(); } catch (IOException ignored) {}
            try { if (os != null) os.close(); } catch (IOException ignored) {}
            try { if (br != null) br.close(); } catch (IOException e) { System.err.println("Error closing reader: " + e.getMessage()); }
            try { if (isr != null) isr.close(); } catch (IOException ignored) {}
            try { if (is != null) is.close(); } catch (IOException ignored) {}
            try { if (!socket.isClosed()) socket.close(); } catch (IOException e) { System.err.println("Error closing socket: " + e.getMessage()); }

            cleanup(); // idempotent cleanup (unregister, DAO logout, leave queue)
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

    /**
     * Handle a GUEST login - create a temporary user id and register in registry without DB auth.
     *
     * Usage: GUEST <username>
     *
     * This is for testing only. Guest accounts are ephemeral and not persisted.
     */
    private void handleGuest(String[] parts) {
        if (parts.length < 2) {
            sendType(MessageType.LOGIN_FAILED, "Usage: GUEST <username>");
            return;
        }
        if (username != null) {
            sendType(MessageType.ERROR, "Already logged in");
            return;
        }
        String user = parts[1].trim();
        if (user.isEmpty()) {
            sendType(MessageType.LOGIN_FAILED, "Invalid username");
            return;
        }

        // create ephemeral id
        String ephemeralId = UUID.randomUUID().toString();
        this.username = user;
        this.userId = ephemeralId;

        // register in client registry
        boolean registered = clientRegistry.registerClient(username, this);
        if (!registered) {
            this.username = null;
            this.userId = null;
            sendType(MessageType.LOGIN_FAILED, "User already online");
            return;
        }

        // broadcast updated online players
        clientRegistry.broadcastOnlinePlayers();

        // notify client success (reuse LOGIN_SUCCESS)
        sendType(MessageType.LOGIN_SUCCESS, "GUEST");
    }

    /**
     * Attempt to auto-assign a test username ("alice" then "bob") if client hasn't authenticated.
     * This is a convenience for rapid local testing only.
     */
    private void autoAssignTestUsernameIfNeeded() {
        if (this.username != null) return; // already logged in

        String[] testNames = {"alice", "bob"};
        for (String candidate : testNames) {
            boolean registered = false;
            try {
                registered = clientRegistry.registerClient(candidate, this);
            } catch (Exception e) {
                // ignore and continue to next candidate
                registered = false;
            }
            if (registered) {
                this.username = candidate;
                this.userId = UUID.randomUUID().toString();
                // broadcast updated online players
                try { clientRegistry.broadcastOnlinePlayers(); } catch (Exception ignored) {}
                // notify client (use LOGIN_SUCCESS with payload AUTO to indicate auto-assigned)
                sendType(MessageType.LOGIN_SUCCESS, "AUTO");
                System.out.println("Auto-assigned username '" + candidate + "' for testing to connection " + socket.getRemoteSocketAddress());
                return;
            }
        }

        // if none available, do nothing; client can still send GUEST or LOGIN manually
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
            sendType(MessageType.ERROR, "Not authenticated. Send: LOGIN <username> <password> or GUEST <username>");
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
            // sending failed: disconnect to cleanup resources
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
                // Only attempt DAO logout/update if this was a persisted user
                // If you want to avoid PlayerDAO calls for guests, you can detect by payload of LOGIN_SUCCESS "GUEST"
                playerDAO.logout(username);
            } catch (Exception e) {
                // It's OK if this fails for guest or when DAO not configured
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

        // close streams/socket if not already closed (safe/no-throw)
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