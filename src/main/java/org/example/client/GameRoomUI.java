//package org.example.client;
//
//import javax.swing.*;
//import java.awt.*;
//import java.awt.event.ActionEvent;
//import java.awt.event.ActionListener;
//import java.io.*;
//import java.net.Socket;
//import java.util.concurrent.CompletableFuture;
//
//public class GameRoomUI extends JFrame {
//    private String username;
//    private String opponent;
//    private String gameId;
//    private GameConnection connection;
//
//    // UI Components
//    private JLabel roomTitleLabel;
//    private JLabel player1Label;
//    private JLabel player2Label;
//    private JLabel statusLabel;
//    private JProgressBar countdownBar;
//    private JTextArea chatArea;
//    private JTextField chatInput;
//    private JButton sendChatButton;
//    private JButton leaveRoomButton;
//    private JPanel gameAreaPanel;
//    private Timer countdownTimer;
//    private int countdown = 5;
//
//    public GameRoomUI(String username, String opponent, String gameId, Socket socket) {
//        this.username = username;
//        this.opponent = opponent;
//        this.gameId = gameId;
//
//        try {
//            this.connection = new GameConnection(socket);
//        } catch (IOException e) {
//            JOptionPane.showMessageDialog(this, "Error setting up game connection: " + e.getMessage());
//            dispose();
//            return;
//        }
//
//        initializeComponents();
//        setupLayout();
//        setupEventListeners();
//        startMessageListener();
//        startCountdown();
//    }
//
//    private void initializeComponents() {
//        setTitle("QCA Game Room - " + gameId.substring(0, 8));
//        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
//        setSize(800, 600);
//        setLocationRelativeTo(null);
//        setResizable(false);
//
//        // Room info
//        roomTitleLabel = new JLabel("Game Room: " + gameId.substring(0, 8), SwingConstants.CENTER);
//        roomTitleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
//
//        player1Label = new JLabel("Player 1: " + username, SwingConstants.CENTER);
//        player1Label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
//        player1Label.setOpaque(true);
//        player1Label.setBackground(Color.LIGHT_GRAY);
//        player1Label.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
//
//        player2Label = new JLabel("Player 2: " + opponent, SwingConstants.CENTER);
//        player2Label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
//        player2Label.setOpaque(true);
//        player2Label.setBackground(Color.LIGHT_GRAY);
//        player2Label.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
//
//        statusLabel = new JLabel("Preparing game...", SwingConstants.CENTER);
//        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
//        statusLabel.setForeground(Color.BLUE);
//
//        // Countdown bar
//        countdownBar = new JProgressBar(0, 5);
//        countdownBar.setValue(5);
//        countdownBar.setStringPainted(true);
//        countdownBar.setString("Game starts in 5 seconds...");
//
//        // Chat components
//        chatArea = new JTextArea(8, 30);
//        chatArea.setEditable(false);
//        chatArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
//        chatArea.setBackground(Color.WHITE);
//
//        chatInput = new JTextField(20);
//        sendChatButton = new JButton("Send");
//        leaveRoomButton = new JButton("Leave Room");
//        leaveRoomButton.setBackground(Color.RED);
//        leaveRoomButton.setForeground(Color.WHITE);
//
//        // Game area (will be activated after countdown)
//        gameAreaPanel = new JPanel(new BorderLayout());
//        gameAreaPanel.setBorder(BorderFactory.createTitledBorder("Game Area"));
//        gameAreaPanel.add(new JLabel("Game will start shortly...", SwingConstants.CENTER));
//        gameAreaPanel.setPreferredSize(new Dimension(400, 300));
//    }
//
//    private void setupLayout() {
//        setLayout(new BorderLayout());
//
//        // Top panel - Room info and players
//        JPanel topPanel = new JPanel(new BorderLayout());
//        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
//
//        topPanel.add(roomTitleLabel, BorderLayout.NORTH);
//
//        JPanel playersPanel = new JPanel(new GridLayout(1, 2, 10, 0));
//        playersPanel.add(player1Label);
//        playersPanel.add(player2Label);
//        topPanel.add(playersPanel, BorderLayout.CENTER);
//
//        // Status panel
//        JPanel statusPanel = new JPanel(new BorderLayout());
//        statusPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
//        statusPanel.add(statusLabel, BorderLayout.NORTH);
//        statusPanel.add(countdownBar, BorderLayout.CENTER);
//
//        // Center panel - Game area
//        JPanel centerPanel = new JPanel(new BorderLayout());
//        centerPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
//        centerPanel.add(gameAreaPanel, BorderLayout.CENTER);
//
//        // Bottom panel - Chat and controls
//        JPanel bottomPanel = new JPanel(new BorderLayout());
//        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
//
//        // Chat panel
//        JPanel chatPanel = new JPanel(new BorderLayout());
//        chatPanel.setBorder(BorderFactory.createTitledBorder("Chat"));
//
//        JScrollPane chatScrollPane = new JScrollPane(chatArea);
//        chatScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
//        chatPanel.add(chatScrollPane, BorderLayout.CENTER);
//
//        JPanel chatInputPanel = new JPanel(new BorderLayout());
//        chatInputPanel.add(chatInput, BorderLayout.CENTER);
//        chatInputPanel.add(sendChatButton, BorderLayout.EAST);
//        chatPanel.add(chatInputPanel, BorderLayout.SOUTH);
//
//        // Controls panel
//        JPanel controlsPanel = new JPanel(new FlowLayout());
//        controlsPanel.add(leaveRoomButton);
//
//        bottomPanel.add(chatPanel, BorderLayout.CENTER);
//        bottomPanel.add(controlsPanel, BorderLayout.SOUTH);
//
//        // Add all panels to main frame
//        add(topPanel, BorderLayout.NORTH);
//        add(centerPanel, BorderLayout.CENTER);
//        add(bottomPanel, BorderLayout.SOUTH);
//    }
//
//    private void setupEventListeners() {
//        // Chat functionality
//        ActionListener sendChatAction = e -> sendChatMessage();
//        sendChatButton.addActionListener(sendChatAction);
//        chatInput.addActionListener(sendChatAction);
//
//        // Leave room
//        leaveRoomButton.addActionListener(e -> leaveRoom());
//
//        // Window closing
//        addWindowListener(new java.awt.event.WindowAdapter() {
//            @Override
//            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
//                int option = JOptionPane.showConfirmDialog(GameRoomUI.this,
//                    "Are you sure you want to leave the game room?",
//                    "Leave Room",
//                    JOptionPane.YES_NO_OPTION);
//                if (option == JOptionPane.YES_OPTION) {
//                    leaveRoom();
//                }
//            }
//        });
//    }
//
//    private void startCountdown() {
//        countdownTimer = new Timer(1000, new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                countdown--;
//                countdownBar.setValue(countdown);
//
//                if (countdown > 0) {
//                    countdownBar.setString("Game starts in " + countdown + " seconds...");
//                    statusLabel.setText("Get ready! Game starting soon...");
//                } else {
//                    countdownBar.setString("Game Started!");
//                    statusLabel.setText("Game in progress!");
//                    statusLabel.setForeground(Color.GREEN);
//                    countdownTimer.stop();
//                    startGame();
//                }
//            }
//        });
//        countdownTimer.start();
//
//        addChatMessage("SYSTEM", "Welcome to the game room!");
//        addChatMessage("SYSTEM", "Game will start in " + countdown + " seconds...");
//    }
//
//    private void startGame() {
//        // Replace game area with actual game interface
//        gameAreaPanel.removeAll();
//
//        JPanel gameInterface = createGameInterface();
//        gameAreaPanel.add(gameInterface, BorderLayout.CENTER);
//
//        // Highlight current player
//        updatePlayerHighlight();
//
//        addChatMessage("SYSTEM", "Game has started! Good luck!");
//
//        gameAreaPanel.revalidate();
//        gameAreaPanel.repaint();
//    }
//
//    private JPanel createGameInterface() {
//        JPanel gamePanel = new JPanel(new BorderLayout());
//
//        // Game board or interface (placeholder for your actual game)
//        JPanel boardPanel = new JPanel(new GridLayout(3, 3, 2, 2));
//        boardPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
//
//        // Create a simple grid for demonstration (replace with your actual game board)
//        for (int i = 0; i < 9; i++) {
//            JButton cellButton = new JButton("");
//            cellButton.setPreferredSize(new Dimension(80, 80));
//            cellButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
//            cellButton.addActionListener(e -> handleGameMove(cellButton));
//            boardPanel.add(cellButton);
//        }
//
//        gamePanel.add(boardPanel, BorderLayout.CENTER);
//
//        // Game controls
//        JPanel gameControlsPanel = new JPanel(new FlowLayout());
//        JButton surrenderButton = new JButton("Surrender");
//        surrenderButton.addActionListener(e -> surrender());
//        gameControlsPanel.add(surrenderButton);
//
//        gamePanel.add(gameControlsPanel, BorderLayout.SOUTH);
//
//        return gamePanel;
//    }
//
//    private void handleGameMove(JButton cellButton) {
//        // Placeholder for game move logic
//        // This would integrate with your actual game logic
//        try {
//            String move = "GAME_MOVE|" + gameId + "|" + System.nanoTime(); // Placeholder move data
//            connection.sendMessage(move);
//            addChatMessage("GAME", "Move made by " + username);
//        } catch (IOException e) {
//            addChatMessage("ERROR", "Failed to send move: " + e.getMessage());
//        }
//    }
//
//    private void updatePlayerHighlight() {
//        // Highlight current player (this would be managed by game logic)
//        player1Label.setBackground(username.equals("currentPlayer") ? Color.GREEN : Color.LIGHT_GRAY);
//        player2Label.setBackground(opponent.equals("currentPlayer") ? Color.GREEN : Color.LIGHT_GRAY);
//    }
//
//    private void sendChatMessage() {
//        String message = chatInput.getText().trim();
//        if (!message.isEmpty()) {
//            try {
//                connection.sendMessage("CHAT|" + gameId + "|" + username + "|" + message);
//                addChatMessage(username, message);
//                chatInput.setText("");
//            } catch (IOException e) {
//                addChatMessage("ERROR", "Failed to send message: " + e.getMessage());
//            }
//        }
//    }
//
//    private void addChatMessage(String sender, String message) {
//        SwingUtilities.invokeLater(() -> {
//            String timestamp = java.time.LocalTime.now().toString().substring(0, 8);
//            chatArea.append("[" + timestamp + "] " + sender + ": " + message + "\n");
//            chatArea.setCaretPosition(chatArea.getDocument().getLength());
//        });
//    }
//
//    private void surrender() {
//        int option = JOptionPane.showConfirmDialog(this,
//            "Are you sure you want to surrender?",
//            "Surrender",
//            JOptionPane.YES_NO_OPTION);
//
//        if (option == JOptionPane.YES_OPTION) {
//            try {
//                connection.sendMessage("SURRENDER|" + gameId + "|" + username);
//                addChatMessage("GAME", username + " has surrendered");
//                endGame(opponent + " wins by surrender!");
//            } catch (IOException e) {
//                addChatMessage("ERROR", "Failed to surrender: " + e.getMessage());
//            }
//        }
//    }
//
//    private void leaveRoom() {
//        try {
//            if (countdownTimer != null && countdownTimer.isRunning()) {
//                countdownTimer.stop();
//            }
//            connection.sendMessage("LEAVE_ROOM|" + gameId + "|" + username);
//            connection.disconnect();
//        } catch (IOException e) {
//            System.err.println("Error leaving room: " + e.getMessage());
//        }
//        dispose();
//    }
//
//    private void startMessageListener() {
//        CompletableFuture.runAsync(() -> {
//            try {
//                String message;
//                while ((message = connection.receiveMessage()) != null) {
//                    final String msg = message;
//                    SwingUtilities.invokeLater(() -> handleIncomingMessage(msg));
//                }
//            } catch (IOException e) {
//                SwingUtilities.invokeLater(() -> {
//                    addChatMessage("ERROR", "Connection lost: " + e.getMessage());
//                });
//            }
//        });
//    }
//
//    private void handleIncomingMessage(String message) {
//        String[] parts = message.split("\\|");
//        String messageType = parts[0];
//
//        switch (messageType) {
//            case "CHAT":
//                if (parts.length >= 4) {
//                    String sender = parts[2];
//                    String chatMessage = parts[3];
//                    if (!sender.equals(username)) { // Don't show own messages
//                        addChatMessage(sender, chatMessage);
//                    }
//                }
//                break;
//            case "GAME_MOVE":
//                addChatMessage("GAME", "Move received from " + opponent);
//                // Handle game move logic here
//                break;
//            case "PLAYER_LEFT":
//                addChatMessage("SYSTEM", parts[1] + " left the room");
//                endGame("Game ended - opponent left");
//                break;
//            case "GAME_END":
//                String result = parts.length > 1 ? parts[1] : "Game ended";
//                endGame(result);
//                break;
//        }
//    }
//
//    private void endGame(String result) {
//        if (countdownTimer != null && countdownTimer.isRunning()) {
//            countdownTimer.stop();
//        }
//
//        statusLabel.setText("Game Ended");
//        statusLabel.setForeground(Color.RED);
//        countdownBar.setString(result);
//
//        addChatMessage("SYSTEM", "GAME ENDED: " + result);
//
//        Timer closeTimer = new Timer(5000, e -> {
//            int option = JOptionPane.showConfirmDialog(this,
//                "Game ended. Close room?",
//                "Game Ended",
//                JOptionPane.YES_NO_OPTION);
//            if (option == JOptionPane.YES_OPTION) {
//                dispose();
//            }
//        });
//        closeTimer.setRepeats(false);
//        closeTimer.start();
//    }
//
//    // Inner class for game connection
//    private static class GameConnection {
//        private BufferedReader in;
//        private PrintWriter out;
//        private Socket socket;
//
//        public GameConnection(Socket socket) throws IOException {
//            this.socket = socket;
//            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//            this.out = new PrintWriter(socket.getOutputStream(), true);
//        }
//
//        public void sendMessage(String message) throws IOException {
//            out.println(message);
//        }
//
//        public String receiveMessage() throws IOException {
//            return in.readLine();
//        }
//
//        public void disconnect() {
//            try {
//                if (socket != null) socket.close();
//                if (in != null) in.close();
//                if (out != null) out.close();
//            } catch (IOException e) {
//                System.err.println("Error disconnecting: " + e.getMessage());
//            }
//        }
//    }
//}