package com.mathspeed.network;

import com.mathspeed.server.service.*;
import com.mathspeed.dao.PlayerDAO;
import com.mathspeed.model.Player;
import com.mathspeed.protocol.MessageType;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ClientHandler: updated to notify GameSession when this client disconnects so
 * ongoing match can finish immediately and opponent receives game_over.
 *
 * Added:
 * - FORFEIT / CANCEL command handling which calls GameSession.handleForfeit(this)
 *   (forfeit while still connected).
 *
 * Changes:
 * - Accept incoming tokens that are MessageType names (e.g. "PING") in addition to legacy plain commands.
 * - When a MessageType name is received, we handle it using the enum; otherwise we fall back to the original string-based switch.
 * - When acknowledging a client forfeit, reply with MessageType.FORFEIT_ACK instead of a hard-coded string.
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ClientRegistry clientRegistry;
    private final Matchmaker matchmaker;
    private final ChallengeManager challengeManager;
    private final PlayerDAO playerDAO;

    private BufferedWriter out;
    private BufferedReader in;

    private volatile Player player = null;
    private final AtomicReference<GameSession> currentGame = new AtomicReference<>(null);
    private volatile boolean running = true;
    private volatile long lastHeartbeat = System.currentTimeMillis();
    private final int DEFAULT_TOTAL_ROUNDS = 10;

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
        System.out.println("ClientHandler started for " + remote);

        try {
            socket.setSoTimeout(0); // disable timeout, dùng heartbeat
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            String line;
            while (running && (line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                refreshHeartbeat(); // <-- update mỗi lần nhận dữ liệu
                String[] parts = line.split(" ", 3);
                String cmdToken = parts[0].toUpperCase();

                // First try to parse as MessageType name, if clients send MessageType-based commands.
                MessageType incomingType = null;
                try {
                    incomingType = MessageType.valueOf(cmdToken);
                } catch (IllegalArgumentException ignored) {
                    // not a MessageType name; we'll fall back to legacy string commands
                }

                if (incomingType != null) {
                    // Handle a small set of MessageType-based commands directly
                    switch (incomingType) {
                        case PING:
                            sendType(MessageType.PONG, null);
                            break;
                        case DISCONNECT:
                        case LOGOUT:
                            sendType(MessageType.DISCONNECT, null);
                            running = false;
                            break;
                        case FORFEIT_REQUEST: // client sent "FORFEIT_REQUEST" as MessageType
                        case FORFEIT_ACK:     // treat FORFEIT_ACK from client same as FORFEIT_REQUEST (lenient)
                            handleForfeitCommand();
                            break;
                        // You can add more MessageType-driven incoming handling here if clients adopt enum names
                        default:
                            // if enum name doesn't map to a command we act upon, fall back to legacy processing
                            handleLegacyCommand(parts, cmdToken);
                            break;
                    }
                } else {
                    // Legacy plain-text command processing (backwards-compat)
                    handleLegacyCommand(parts, cmdToken);
                }
            }
        } catch (SocketException se) {
            System.out.println("Socket exception for " + remote + ": " + se.getMessage());
        } catch (IOException ioe) {
            System.err.println("I/O error for client " + remote + ": " + ioe.getMessage());
        } finally {
            cleanup();
            System.out.println("ClientHandler stopped for " + remote);
        }
    }

    /**
     * Handle legacy/plain-text commands (keeps original behavior).
     */
    private void handleLegacyCommand(String[] parts, String cmdToken) {
        switch (cmdToken) {
            case "REGISTER": handleRegister(parts); break;
            case "LOGIN": handleLogin(parts); break;
            case "JOIN_QUEUE": handleJoinQueue(); break;
            case "LEAVE_QUEUE": handleLeaveQueue(); break;
            case "CHALLENGE": handleChallenge(parts); break;
            case "ACCEPT": handleAccept(parts); break;
            case "DECLINE": handleDecline(parts); break;
            case "SUBMIT_ANSWER": handleSubmitAnswer(parts); break;
            case "ANSWER": handleSubmitAnswer(parts); break; // alias that also supports "ANSWER <expression...>"
            case "READY": handleReady(); break;
            case "REQUEST_MATCH_INFO": handleRequestMatchInfo(); break;
            case "FORFEIT": handleForfeitCommand(); break; // user-initiated forfeit (stay connected)
            case "CANCEL": handleForfeitCommand(); break; // alias
            case "PING": sendType(MessageType.PONG, null); break;
            case "QUIT": sendType(MessageType.DISCONNECT, null); running = false; break;
            default: sendType(MessageType.ERROR, "Unknown command"); break;
        }
    }

    // -----------------------
    // Command handlers (same as before)
    // -----------------------
    private void handleRegister(String[] parts) {
        if (parts.length < 3) {
            sendType(MessageType.ERROR, "REGISTER usage: REGISTER <username> <password> [gender]");
            return;
        }
        String username = parts[1].trim();
        String password = parts[2].trim();
        String gender = "male";
        if (parts.length >= 4) {
            gender = parts[3].trim().toLowerCase();
            if (!gender.equals("male") && !gender.equals("female") && !gender.equals("other")) {
                sendType(MessageType.ERROR, "Gender must be male, female, or other");
                return;
            }
        }
        try {
            Player newPlayer = new Player();
            newPlayer.setId(UUID.randomUUID().toString());
            newPlayer.setUsername(username);
            newPlayer.setPasswordHash(playerDAO.hashPassword(password));
            newPlayer.setDisplayName(username);
            newPlayer.setGender(gender);
            newPlayer.setAvatarUrl(null);

            boolean success = playerDAO.insertPlayer(newPlayer);
            if (success) sendType(MessageType.REGISTER_SUCCESS, "Account created successfully");
            else sendType(MessageType.ERROR, "Username already exists");
        } catch (Exception e) {
            sendType(MessageType.ERROR, "Registration failed");
            e.printStackTrace();
        }
    }

    private void handleLogin(String[] parts) {
        if (parts.length < 3) {
            sendType(MessageType.LOGIN_FAILED, "Usage: LOGIN <username> <password>");
            return;
        }
        if (player != null) {
            sendType(MessageType.ERROR, "Already logged in");
            return;
        }

        String username = parts[1].trim();
        String password = parts[2].trim();

        Player p;
        try {
            p = playerDAO.findPlayer(username, password);
        } catch (Exception e) {
            sendType(MessageType.LOGIN_FAILED, "Auth failure");
            e.printStackTrace();
            return;
        }
        if (p == null) {
            sendType(MessageType.LOGIN_FAILED, "Invalid credentials");
            return;
        }

        this.player = p;

        boolean registered = clientRegistry.registerClient(username, this);
        if (!registered) {
            this.player = null;
            sendType(MessageType.LOGIN_FAILED, "User already online");
            return;
        }

        try { playerDAO.updateLastLogin(username); } catch (Exception ignored) {}

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
            sendType(MessageType.ERROR, "CHALLENGE usage: CHALLENGE <target> [rounds]");
            return;
        }

        String target = parts[1].trim();
        if (target.equalsIgnoreCase(player.getUsername())) {
            sendType(MessageType.ERROR, "Cannot challenge yourself");
            return;
        }

        int totalRounds = DEFAULT_TOTAL_ROUNDS;
        if (parts.length >= 3) {
            try {
                totalRounds = Integer.parseInt(parts[2].trim());
                if (totalRounds < 1) totalRounds = DEFAULT_TOTAL_ROUNDS;
            } catch (NumberFormatException e) {
                sendType(MessageType.ERROR, "Invalid number of rounds, using default: " + DEFAULT_TOTAL_ROUNDS);
            }
        }

        System.out.println("Received challenge from " + player.getUsername() + " to " + target + " for " + totalRounds + " rounds.");

        challengeManager.sendChallenge(player.getUsername(), target, totalRounds);
    }

    private void handleAccept(String[] parts) {
        if (!ensureLoggedIn()) return;
        if (parts.length < 2) {
            sendType(MessageType.ERROR, "ACCEPT usage: ACCEPT <challenger>");
            return;
        }
        String challenger = parts[1].trim();
        challengeManager.acceptChallenge(player.getUsername(), challenger);
    }

    private void handleDecline(String[] parts) {
        if (!ensureLoggedIn()) return;
        if (parts.length < 2) {
            sendType(MessageType.ERROR, "DECLINE usage: DECLINE <challenger>");
            return;
        }
        String challenger = parts[1].trim();
        challengeManager.declineChallenge(challenger, player.getUsername());
    }

    /**
     * Hỗ trợ cả: SUBMIT_ANSWER <expression...>  và ANSWER <expression...>
     * Robust reconstruction and validation (keeps expression-only to pass to evaluator).
     */
    private void handleSubmitAnswer(String[] parts) {
        if (!ensureLoggedIn()) return;

        // Reconstruct expression robustly (preserve spaces when possible)
        String rawExpr = null;
        if (parts.length >= 3 && parts[2] != null && !parts[2].isEmpty()) {
            rawExpr = (parts[1] + " " + parts[2]).trim();
        } else if (parts.length == 2) {
            rawExpr = parts[1].trim();
        } else {
            String token = parts[0];
            String up = token.toUpperCase(java.util.Locale.ROOT);
            final String CMD1 = "SUBMIT_ANSWER";
            final String CMD2 = "ANSWER";
            if (up.startsWith(CMD1)) {
                rawExpr = token.substring(CMD1.length()).trim();
            } else if (up.startsWith(CMD2)) {
                rawExpr = token.substring(CMD2.length()).trim();
            } else {
                sendType(MessageType.ERROR, "Usage: SUBMIT_ANSWER <expression>");
                return;
            }
            if (rawExpr.startsWith(":") || rawExpr.startsWith("=")) rawExpr = rawExpr.substring(1).trim();
        }

        if (rawExpr == null || rawExpr.isEmpty()) {
            sendType(MessageType.ERROR, "Usage: SUBMIT_ANSWER <expression>");
            return;
        }

        String expr = rawExpr;
        String upExpr = expr.toUpperCase(java.util.Locale.ROOT);
        while (upExpr.startsWith("SUBMIT_ANSWER") || upExpr.startsWith("ANSWER")) {
            if (upExpr.startsWith("SUBMIT_ANSWER")) expr = expr.substring("SUBMIT_ANSWER".length()).trim();
            else expr = expr.substring("ANSWER".length()).trim();
            upExpr = expr.toUpperCase(java.util.Locale.ROOT);
        }

        expr = expr.replaceFirst("^[^0-9\\-\\(]+", "").trim();

        if (expr.isEmpty()) {
            sendType(MessageType.ERROR, "Empty expression");
            return;
        }

        if (!expr.matches(".*\\d.*")) {
            sendType(MessageType.ERROR, "Expression must contain digits");
            return;
        }
        if (!expr.matches("[0-9+\\-*/()\\s]+")) {
            sendType(MessageType.ERROR, "Expression contains invalid characters");
            return;
        }

        GameSession session = currentGame.get();
        if (session == null) {
            sendType(MessageType.ERROR, "No active game session");
            return;
        }

        try {
            session.submitAnswer(this, expr);
        } catch (Exception e) {
            e.printStackTrace();
            sendType(MessageType.ERROR, "Failed to submit answer: " + e.getClass().getSimpleName());
        }
    }

    // -----------------------
    // READY / REQUEST_MATCH_INFO handlers
    // -----------------------
    private void handleReady() {
        if (!ensureLoggedIn()) return;
        GameSession session = currentGame.get();
        if (session == null) {
            sendType(MessageType.ERROR, "No active match to set READY");
            return;
        }
        try {
            session.handleReady(this);
            sendType(MessageType.INFO, "READY_RECEIVED");
        } catch (Exception e) {
            sendType(MessageType.ERROR, "Failed to set READY");
            e.printStackTrace();
        }
    }

    private void handleRequestMatchInfo() {
        if (!ensureLoggedIn()) return;
        GameSession session = currentGame.get();
        if (session == null) {
            sendType(MessageType.ERROR, "No active match");
            return;
        }
        try {
            session.handleRequestMatchInfo(this);
            sendType(MessageType.INFO, "MATCH_INFO_SENT");
        } catch (Exception e) {
            sendType(MessageType.ERROR, "Failed to send match info");
            e.printStackTrace();
        }
    }

    /**
     * Handle client-initiated forfeit (Hủy) while staying connected.
     */
    private void handleForfeitCommand() {
        if (!ensureLoggedIn()) return;
        GameSession session = currentGame.get();
        if (session == null) {
            sendType(MessageType.ERROR, "No active match to forfeit");
            return;
        }
        try {
            // delegate to GameSession which runs logic on its scheduler thread
            session.handleForfeit(this);
            // use explicit MessageType acknowledgement instead of ad-hoc string
            sendType(MessageType.FORFEIT_ACK, null);
        } catch (Exception e) {
            sendType(MessageType.ERROR, "Failed to forfeit match");
            e.printStackTrace();
        }
    }

    public GameSession getGameSession() { return currentGame.get(); }
    public void setGameSession(GameSession session) { currentGame.set(session); }
    public void clearGameSession() { currentGame.set(null); }

    public void setCurrentGame(GameSession session) { currentGame.set(session); }
    public GameSession getCurrentGame() { return currentGame.get(); }
    public void clearCurrentGame() { currentGame.set(null); }

    public AtomicReference<GameSession> getCurrentGameRef() { return currentGame; }


    public Player getPlayer() { return player; }
    public String getUsername() { return player != null ? player.getUsername() : null; }

    public void refreshHeartbeat() {
        lastHeartbeat = System.currentTimeMillis();
    }

    public boolean isAlive(long timeoutMillis) {
        return running && socket != null && !socket.isClosed() &&
                (System.currentTimeMillis() - lastHeartbeat <= timeoutMillis);
    }

    private boolean ensureLoggedIn() {
        if (player == null) {
            sendType(MessageType.ERROR, "Not authenticated. Send: LOGIN <username> <password>");
            return false;
        }
        return true;
    }

    public synchronized void sendType(MessageType type, String payload) {
        if (out == null) return;
        try {
            String msg = type.name() + (payload != null && !payload.isEmpty() ? "|" + payload : "");
            out.write(msg);
            out.newLine();
            out.flush();
        } catch (IOException e) {
            System.err.println("Failed to send to " + getUsername() + ": " + e.getMessage());
            disconnect();
        }
    }

    public void sendMessage(String message) { sendRaw(message); }

    private synchronized void sendRaw(String message) {
        if (out == null) return;
        try {
            out.write(message);
            out.newLine();
            out.flush();
        } catch (IOException e) {
            System.err.println("Failed to send to " + getUsername() + ": " + e.getMessage());
            disconnect();
        }
    }

    public void disconnect() {
        running = false;

        GameSession session = currentGame.getAndSet(null);
        if (session != null) {
            try {
                session.handlePlayerDisconnect(this);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        try { socket.close(); } catch (IOException ignored) {}
    }

    private void cleanup() {
        // notify session defensively
        GameSession session = currentGame.getAndSet(null);
        if (session != null) {
            try { session.handlePlayerDisconnect(this); } catch (Exception ignored) {}
        }

        if (player != null) {
            try { clientRegistry.removeClient(player.getUsername()); } catch (Exception ignored) {}
        }
        try { matchmaker.leaveQueue(this); } catch (Exception ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (!socket.isClosed()) socket.close(); } catch (IOException ignored) {}
    }
}