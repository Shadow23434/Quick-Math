package org.example.server;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class GameServer {
    private static final int PORT = 5000;
    private ServerSocket serverSocket;
    private final ExecutorService clientPool;
    private final Map<String, ClientHandler> clients;
    private final Map<String, GameSession> gameSessions;
    private final Queue<ClientHandler> waitingPlayers;
    private final UserDAO userDAO;
    private final Map<String, Set<String>> pendingChallenges;
    private volatile boolean running;

    public GameServer() {
        this(new UserDAO());
    }

    public GameServer(UserDAO userDAO) {
        this.userDAO = userDAO;
        this.clientPool = Executors.newCachedThreadPool();
        this.clients = new ConcurrentHashMap<>();
        this.gameSessions = new ConcurrentHashMap<>();
        this.waitingPlayers = new ConcurrentLinkedQueue<>();
        this.pendingChallenges = new ConcurrentHashMap<>();
        this.running = true;

        //set al users offline on server start
        userDAO.setAllUsersOffline();
    }

    public UserDAO getUserDAO() {
        return userDAO;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Game Server started on port " + PORT);

            // Start matchmaking thread
            new Thread(this::matchPlayers).start();

            // Accept client connections
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                    clientPool.execute(clientHandler);
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error accepting client connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Could not listen on port " + PORT);
        } finally {
            shutdown();
        }
    }

    private void matchPlayers() {
        while (running) {
            if (waitingPlayers.size() >= 2) {
                ClientHandler player1 = waitingPlayers.poll();
                ClientHandler player2 = waitingPlayers.poll();
                if (player1 != null && player2 != null) {
                    createGame(player1, player2);
                }
            }
            try {
                Thread.sleep(1000); // Check for matches every second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void createGame(ClientHandler player1, ClientHandler player2) {
        String gameId = UUID.randomUUID().toString();
        GameSession gameSession = new GameSession(player1, player2);
        gameSessions.put(gameId, gameSession);

        // Notify players
        player1.setCurrentGame(gameSession);
        player2.setCurrentGame(gameSession);
        player1.sendMessage("GAME_START|" + gameId + "|" + player2.getUsername());
        player2.sendMessage("GAME_START|" + gameId + "|" + player1.getUsername());

        // Broadcast updated player status to all clients
        broadcastOnlinePlayers();
    }

    /**
     * Xử lý tin nhắn từ client bao gồm biểu thức kết quả
     * @param player ClientHandler của người chơi
     * @param message Tin nhắn từ người chơi
     */
    public void handlePlayerMessage(ClientHandler player, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        // Parse message format: COMMAND|DATA
        String[] parts = message.split("\\|", 2);
        if (parts.length < 1) {
            return;
        }

        String command = parts[0];
        String data = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case "SUBMIT_ANSWER":
                handleSubmitAnswer(player, data);
                break;
            case "JOIN_QUEUE":
                addToWaitingQueue(player);
                player.sendMessage("JOINED_QUEUE");
                broadcastOnlinePlayers();
                break;
            case "LEAVE_QUEUE":
                waitingPlayers.remove(player);
                player.sendMessage("LEFT_QUEUE");
                broadcastOnlinePlayers();
                break;
            case "CHALLENGE":
                if (!data.isEmpty()) {
                    sendChallenge(player.getUsername(), data);
                }
                break;
            case "ACCEPT_CHALLENGE":
                if (!data.isEmpty()) {
                    acceptChallenge(player.getUsername(), data);
                }
                break;
            case "DECLINE_CHALLENGE":
                if (!data.isEmpty()) {
                    declineChallenge(data, player.getUsername());
                }
                break;
            case "GET_ONLINE_PLAYERS":
                sendOnlinePlayersToClient(player);
                break;
            default:
                System.out.println("Unknown command from " + player.getUsername() + ": " + command);
                break;
        }
    }

    /**
     * Xử lý gửi danh sách người chơi trực tuyến cho một client
     * @param player ClientHandler của người chơi
     */
    private void sendOnlinePlayersToClient(ClientHandler player) {
        StringBuilder playersList = new StringBuilder("ONLINE_PLAYERS");
        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            String username = entry.getKey();
            ClientHandler handler = entry.getValue();
            String status = (handler.getCurrentGame() != null) ? "BUSY" : "ONLINE";
            playersList.append("|").append(username).append(":").append(status);
        }

        player.sendMessage(playersList.toString());
    }

    /**
     * Xử lý biểu thức kết quả từ người chơi và chuyển tiếp đến GameSession
     * @param player ClientHandler của người chơi
     * @param answer Biểu thức kết quả
     */
    private void handleSubmitAnswer(ClientHandler player, String answer) {
        if (answer == null || answer.isEmpty()) {
            player.sendMessage("ERROR|Empty answer");
            return;
        }

        // Lấy GameSession hiện tại của người chơi
        GameSession currentGame = player.getCurrentGame();
        if (currentGame == null) {
            player.sendMessage("ERROR|Not in a game");
            return;
        }

        // Ghi log để debug
        System.out.println(player.getUsername() + " submitted answer: " + answer);

        try {
            // Chuyển tiếp biểu thức đến GameSession
            currentGame.submitAnswer(player, answer);
        } catch (Exception e) {
            System.err.println("Error processing answer from " + player.getUsername() + ": " + e.getMessage());
            player.sendMessage("ERROR|Failed to process answer: " + e.getMessage());
        }
    }

    public void addClient(String username, ClientHandler handler) {
        clients.put(username, handler);
    }

    public void removeClient(String username) {
        clients.remove(username);
        // Remove from waiting queue if present
        waitingPlayers.removeIf(client -> client.getUsername().equals(username));

        // Remove all pending challenges for this user
        pendingChallenges.remove(username);
        pendingChallenges.values().forEach(challenges -> challenges.remove(username));
        pendingChallenges.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        userDAO.logout(username);
    }

    public void addToWaitingQueue(ClientHandler client) {
        waitingPlayers.offer(client);
    }

    public void removeGameSession(String gameId) {
        gameSessions.remove(gameId);
    }

    // Add this method to broadcast online players to all clients
    public void broadcastOnlinePlayers() {
        StringBuilder playersList = new StringBuilder("ONLINE_PLAYERS");
        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            String username = entry.getKey();
            ClientHandler handler = entry.getValue();
            String status = (handler.getCurrentGame() != null) ? "BUSY" : "ONLINE";
            playersList.append("|").append(username).append(":").append(status);
        }

        String message = playersList.toString();
        for (ClientHandler client : clients.values()) {
            try {
                client.sendMessage(message);
            } catch (Exception e) {
                System.err.println("Error broadcasting to client: " + e.getMessage());
            }
        }
    }

    public void sendChallenge(String challenger, String target) {
        ClientHandler challengerHandler = clients.get(challenger);
        ClientHandler targetHandler = clients.get(target);

        System.out.println("DEBUG: Challenge from " + challenger + " to " + target);
        System.out.println("DEBUG: Challenger handler exists: " + (challengerHandler != null));
        System.out.println("DEBUG: Target handler exists: " + (targetHandler != null));

        if (challengerHandler == null || targetHandler == null) {
            if (challengerHandler != null) {
                challengerHandler.sendMessage("CHALLENGE_FAILED|Player not online");
            }
            return;
        }

        // Check if target is already in a game
        if (isPlayerInGame(target)) {
            challengerHandler.sendMessage("CHALLENGE_FAILED|Player is already in a game");
            return;
        }

        // Check if challenger is already in a game
        if (isPlayerInGame(challenger)) {
            challengerHandler.sendMessage("CHALLENGE_FAILED|You are already in a game");
            return;
        }

        // Store pending challenge
        pendingChallenges.computeIfAbsent(challenger, k -> new HashSet<>()).add(target);

        // Send challenge notification to TARGET (not challenger)
        targetHandler.sendMessage("CHALLENGE_RECEIVED|" + challenger);

        // Send confirmation to challenger
        challengerHandler.sendMessage("CHALLENGE_SENT|" + target);

        System.out.println("Challenge sent from " + challenger + " to " + target);
        System.out.println("CHALLENGE_RECEIVED message sent to: " + targetHandler.getUsername());
        System.out.println("CHALLENGE_SENT message sent to: " + challengerHandler.getUsername());
    }

    public void acceptChallenge(String accepter, String challenger) {
        ClientHandler challengerHandler = clients.get(challenger);
        ClientHandler accepterHandler = clients.get(accepter);

        System.out.println("DEBUG: acceptChallenge called - accepter: " + accepter + ", challenger: " + challenger);
        System.out.println("DEBUG: Challenger handler exists: " + (challengerHandler != null));
        System.out.println("DEBUG: Accepter handler exists: " + (accepterHandler != null));

        if (challengerHandler == null || accepterHandler == null) {
            if (accepterHandler != null) {
                accepterHandler.sendMessage("CHALLENGE_FAILED|Challenger no longer online");
            }
            return;
        }

        // Debug pending challenges
        System.out.println("DEBUG: Current pending challenges: " + pendingChallenges);
        System.out.println("DEBUG: Looking for challenger: " + challenger);
        System.out.println("DEBUG: Looking for accepter in challenger's list: " + accepter);

        // Verify pending challenge exists - challenger is the KEY, accepter is in the SET
        Set<String> challenges = pendingChallenges.get(challenger);
        System.out.println("DEBUG: Challenges for " + challenger + ": " + challenges);

        if (challenges == null || !challenges.contains(accepter)) {
            accepterHandler.sendMessage("CHALLENGE_FAILED|No pending challenge found");
            System.out.println("DEBUG: No pending challenge found for " + challenger + " -> " + accepter);
            return;
        }

        // Remove pending challenge
        challenges.remove(accepter);
        if (challenges.isEmpty()) {
            pendingChallenges.remove(challenger);
        }

        // Notify both players
        challengerHandler.sendMessage("CHALLENGE_ACCEPTED|" + accepter);
        accepterHandler.sendMessage("CHALLENGE_ACCEPTED|" + challenger);

        // Start game preparation with 5-second delay
        startChallengeGameWithDelay(challenger, accepter);

        System.out.println(accepter + " accepted challenge from " + challenger);
    }

    public void declineChallenge(String challenger, String decliner) {
        ClientHandler challengerHandler = clients.get(challenger);
        ClientHandler declinerHandler = clients.get(decliner);

        // Remove pending challenge
        Set<String> challenges = pendingChallenges.get(challenger);
        if (challenges != null) {
            challenges.remove(decliner);
            if (challenges.isEmpty()) {
                pendingChallenges.remove(challenger);
            }
        }

        // Notify challenger that challenge was declined
        if (challengerHandler != null) {
            challengerHandler.sendMessage("CHALLENGE_DECLINED|" + decliner);
        }

        if (declinerHandler != null) {
            declinerHandler.sendMessage("CHALLENGE_DECLINED|" + challenger);
        }

        System.out.println(decliner + " declined challenge from " + challenger);
    }

    private void startChallengeGameWithDelay(String player1, String player2) {
        ClientHandler handler1 = clients.get(player1);
        ClientHandler handler2 = clients.get(player2);

        if (handler1 != null && handler2 != null) {
            // Notify both players that game is starting
            handler1.sendMessage("GAME_STARTING|" + player2);
            handler2.sendMessage("GAME_STARTING|" + player1);

            // Schedule game start after 5 seconds
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        // Create game session
                        String gameId = UUID.randomUUID().toString();
                        GameSession gameSession = new GameSession(handler1, handler2);
                        gameSessions.put(gameId, gameSession);

                        // Set current game for both players
                        handler1.setCurrentGame(gameSession);
                        handler2.setCurrentGame(gameSession);

                        // Notify both players that game has started
                        handler1.sendMessage("GAME_STARTED|" + gameId + "|" + player2);
                        handler2.sendMessage("GAME_STARTED|" + gameId + "|" + player1);

                        // Broadcast updated player status to all clients
                        broadcastOnlinePlayers();

                        System.out.println("Challenge game started between " + player1 + " and " + player2);
                    } catch (Exception e) {
                        System.err.println("Error starting challenge game: " + e.getMessage());
                    }
                }
            }, 5000); // 5 seconds delay
        }
    }

    private boolean isPlayerInGame(String username) {
        return clients.get(username) != null &&
                clients.get(username).getCurrentGame() != null;
    }

    public Set<String> getOnlineUsers() {
        return new HashSet<>(clients.keySet());
    }

    public void shutdown() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server socket: " + e.getMessage());
        }

        clientPool.shutdown();
        try {
            if (!clientPool.awaitTermination(60, TimeUnit.SECONDS)) {
                clientPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            clientPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Close all client connections
        for (ClientHandler client : clients.values()) {
            client.disconnect();
        }

        clients.clear();
        gameSessions.clear();
        waitingPlayers.clear();
        pendingChallenges.clear();
    }

    // Add this method to your GameServer class
    public ClientHandler getClientHandler(String username) {
        return clients.get(username);
    }

    public void endGameSession(String gameId) {
        GameSession gameSession = gameSessions.remove(gameId);
        if (gameSession != null) {
            // Broadcast updated player status
            broadcastOnlinePlayers();
            System.out.println("Game session ended: " + gameId);
        }
    }

    public static void main(String[] args) {
        GameServer server = new GameServer();
        server.start();
    }
}