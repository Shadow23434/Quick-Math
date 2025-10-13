package org.example.server;

import java.io.*;
import java.net.Socket;
import java.util.Set;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final GameServer server;
    private String username;
    private BufferedReader in;
    private PrintWriter out;
    private GameSession currentGame;
    private volatile boolean running;
    private volatile boolean authenticated = false;

    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
        this.running = true;
        try {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            System.err.println("Error creating client handler: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            String message;
            while (running && (message = in.readLine()) != null) {
                System.out.println("Received from client " + (username != null ? username : "unknown") + ": " + message);
                handleMessage(message);
            }
        } catch (IOException e) {
            System.err.println("Error in client handler: " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    private void handleMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        // For authenticated users, delegate game-related commands to GameServer
        if (authenticated && isGameCommand(message)) {
            server.handlePlayerMessage(this, message);
        } else {
            // Handle authentication and basic commands directly
            handleCommand(message);
        }
    }

    // Check if the message is a game-related command
    private boolean isGameCommand(String message) {
        String[] parts = message.split("\\|", 2);
        String command = parts[0];

        // List of commands that should be handled by the GameServer
        return command.equals("SUBMIT_ANSWER") ||
                command.equals("JOIN_QUEUE") ||
                command.equals("LEAVE_QUEUE") ||
                command.equals("GET_ONLINE_PLAYERS");
    }

    private void handleCommand(String command) {
        String[] parts = command.split("\\|");
        String commandType = parts[0];

        System.out.println("DEBUG: Command received: " + commandType + " from user: " +
                (username != null ? username : "unknown"));

        switch (commandType) {
            case "LOGIN":
                handleLogin(parts);
                break;
            case "REGISTER":
                handleRegister(parts);
                break;
            case "SEND_CHALLENGE":
                handleSendChallenge(parts);
                break;
            case "ACCEPT_CHALLENGE":
                handleAcceptChallenge(parts);
                break;
            case "DECLINE_CHALLENGE":
                handleDeclineChallenge(parts);
                break;
            case "GET_ONLINE_USERS":
                handleGetOnlineUsers();
                break;
            case "CHAT":
                handleChatMessage(parts);
                break;
            case "SUBMIT_ANSWER":
                handleSubmitAnswer(parts);
                break;
            case "GAME_MOVE":
                handleGameMove(parts);
                break;
            case "LEAVE_ROOM":
                handleLeaveRoom(parts);
                break;
            case "SURRENDER":
                handleSurrender(parts);
                break;
            default:
                sendMessage("UNKNOWN_COMMAND|" + commandType);
        }
    }

    private void handleLogin(String[] parts) {
        if (parts.length < 3) {
            sendMessage("LOGIN_FAILED|Invalid format");
            return;
        }

        String username = parts[1];
        String password = parts[2];

        try {
            User user = server.getUserDAO().login(username, password);
            if (user != null) {
                this.username = username;
                this.authenticated = true;
                server.addClient(username, this);

                sendMessage("LOGIN_SUCCESS");
                System.out.println("✓ User logged in: " + username);

                // Broadcast updated online players list to all clients
                server.broadcastOnlinePlayers();
            } else {
                sendMessage("LOGIN_FAILED|Invalid credentials");
            }
        } catch (Exception e) {
            sendMessage("LOGIN_FAILED|" + e.getMessage());
            System.err.println("✗ Login error: " + e.getMessage());
        }
    }

    private void handleRegister(String[] parts) {
        if (parts.length < 3) {
            sendMessage("REGISTER_FAILED|Invalid format");
            return;
        }

        String username = parts[1];
        String password = parts[2];

        try {
            boolean success = server.getUserDAO().register(username, password);
            if (success) {
                sendMessage("REGISTER_SUCCESS");
                System.out.println("✓ User registered: " + username);
            } else {
                sendMessage("REGISTER_FAILED|Username already exists");
            }
        } catch (Exception e) {
            sendMessage("REGISTER_FAILED|" + e.getMessage());
            System.err.println("✗ Registration error: " + e.getMessage());
        }
    }

    private void handleSendChallenge(String[] parts) {
        System.out.println("DEBUG: handleSendChallenge called with " + parts.length + " parts");
        for (int i = 0; i < parts.length; i++) {
            System.out.println("DEBUG: parts[" + i + "] = " + parts[i]);
        }

        if (parts.length < 3) {
            sendMessage("CHALLENGE_FAILED|Invalid command format");
            return;
        }

        String challenger = parts[1];  // The sender
        String target = parts[2];      // The target player

        if (username == null) {
            sendMessage("CHALLENGE_FAILED|Not logged in");
            return;
        }

        // Verify that the challenger matches the authenticated user
        if (!challenger.equals(username)) {
            sendMessage("CHALLENGE_FAILED|Invalid challenger - expected: " + username + ", got: " + challenger);
            return;
        }

        System.out.println("DEBUG: " + challenger + " challenging " + target);
        server.sendChallenge(challenger, target);
    }

    private void handleAcceptChallenge(String[] parts) {
        System.out.println("DEBUG: handleAcceptChallenge called with " + parts.length + " parts");
        for (int i = 0; i < parts.length; i++) {
            System.out.println("DEBUG: parts[" + i + "] = " + parts[i]);
        }

        if (parts.length < 3) {
            sendMessage("CHALLENGE_FAILED|Invalid command format");
            return;
        }

        String accepter = parts[1];    // Should match current user
        String challenger = parts[2];  // The original challenger

        if (username == null) {
            sendMessage("CHALLENGE_FAILED|Not logged in");
            return;
        }

        // Verify that the accepter matches the authenticated user
        if (!accepter.equals(username)) {
            sendMessage("CHALLENGE_FAILED|Invalid accepter - expected: " + username + ", got: " + accepter);
            return;
        }

        System.out.println("DEBUG: " + accepter + " accepting challenge from " + challenger);
        server.acceptChallenge(accepter, challenger);
    }

    private void handleDeclineChallenge(String[] parts) {
        System.out.println("DEBUG: handleDeclineChallenge called with " + parts.length + " parts");
        for (int i = 0; i < parts.length; i++) {
            System.out.println("DEBUG: parts[" + i + "] = " + parts[i]);
        }

        if (parts.length < 3) {
            sendMessage("CHALLENGE_FAILED|Invalid command format");
            return;
        }

        String decliner = parts[1];    // Should match current user
        String challenger = parts[2];  // The original challenger

        if (username == null) {
            sendMessage("CHALLENGE_FAILED|Not logged in");
            return;
        }

        // Verify that the decliner matches the authenticated user
        if (!decliner.equals(username)) {
            sendMessage("CHALLENGE_FAILED|Invalid decliner - expected: " + username + ", got: " + decliner);
            return;
        }

        System.out.println("DEBUG: " + decliner + " declining challenge from " + challenger);
        server.declineChallenge(challenger, decliner);
    }

    private void handleGetOnlineUsers() {
        Set<String> onlineUsers = server.getOnlineUsers();
        StringBuilder response = new StringBuilder("ONLINE_USERS");
        for (String user : onlineUsers) {
            if (!user.equals(username)) { // Don't include self
                response.append("|").append(user);
            }
        }
        sendMessage(response.toString());
    }

    private void handleChatMessage(String[] parts) {
        if (parts.length >= 4 && currentGame != null) {
            String gameId = parts[1];
            String sender = parts[2];
            String message = parts[3];

            // Broadcast to other player in the same game
            String opponent = currentGame.getOpponent(username);
            ClientHandler opponentHandler = server.getClientHandler(opponent);

            if (opponentHandler != null) {
                opponentHandler.sendMessage("CHAT|" + gameId + "|" + sender + "|" + message);
            }
        }
    }

    /**
     * Xử lý biểu thức kết quả gửi từ người chơi
     */
    private void handleSubmitAnswer(String[] parts) {
        if (currentGame == null) {
            sendMessage("ERROR|Not in a game");
            return;
        }

        if (parts.length < 2) {
            sendMessage("ERROR|Invalid answer format");
            return;
        }

        // Lấy biểu thức toán học từ lệnh
        String answer = parts[1];

        // Ghi log
        System.out.println(username + " submitting answer: " + answer);

        // Chuyển biểu thức đến game session để xử lý
        try {
            currentGame.submitAnswer(this, answer);
        } catch (Exception e) {
            System.err.println("Error processing answer from " + username + ": " + e.getMessage());
            sendMessage("ERROR|Failed to process answer: " + e.getMessage());
        }
    }

    private void handleGameMove(String[] parts) {
        if (parts.length >= 3 && currentGame != null) {
            String gameId = parts[1];
            String moveData = parts[2];

            // Forward move to opponent
            String opponent = currentGame.getOpponent(username);
            ClientHandler opponentHandler = server.getClientHandler(opponent);

            if (opponentHandler != null) {
                opponentHandler.sendMessage("GAME_MOVE|" + gameId + "|" + moveData);
            }
        }
    }

    private void handleLeaveRoom(String[] parts) {
        if (currentGame != null) {
            // Notify opponent that player has quit
            currentGame.playerQuit(this);

            // Clear game reference
            currentGame = null;

            // Update player status
            server.broadcastOnlinePlayers();
            sendMessage("LEFT_GAME");
        }
    }

    private void handleSurrender(String[] parts) {
        if (currentGame != null) {
            // Process the surrender in the game
            currentGame.playerQuit(this);

            // Clear game reference
            currentGame = null;

            // Update player status
            server.broadcastOnlinePlayers();
            sendMessage("SURRENDERED");
        }
    }

    public void setCurrentGame(GameSession gameSession) {
        this.currentGame = gameSession;
    }

    public GameSession getCurrentGame() {
        return this.currentGame;
    }

    public void sendMessage(String message) {
        if (out != null) {
            System.out.println("DEBUG: Sending to " +
                    (username != null ? username : "unknown") + ": " + message);
            out.println(message);
            out.flush();
        }
    }

    public void disconnect() {
        running = false;

        // If player is in a game, notify the opponent
        if (currentGame != null) {
            currentGame.playerQuit(this);
        }

        // Remove client from server
        if (username != null) {
            server.removeClient(username);
        }

        // Broadcast updated online players list after disconnect
        server.broadcastOnlinePlayers();

        try {
            if (socket != null) socket.close();
            if (in != null) in.close();
            if (out != null) out.close();
        } catch (IOException e) {
            System.err.println("Error closing client connection: " + e.getMessage());
        }
    }

    public String getUsername() {
        return username;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }
}