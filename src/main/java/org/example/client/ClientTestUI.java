//package org.example.client;
//
//import javax.swing.*;
//import java.awt.*;
//import java.awt.event.ActionEvent;
//import java.awt.event.ActionListener;
//import java.io.*;
//import java.net.Socket;
//
//public class ClientTestUI extends JFrame {
//    private Socket socket;
//    private PrintWriter out;
//    private BufferedReader in;
//    private String username;
//    private boolean isLoggedIn = false;
//
//    // UI Components
//    private JTextField usernameField;
//    private JPasswordField passwordField;
//    private JButton loginButton;
//    private JButton logoutButton;
//    private JList<String> onlinePlayersList;
//    private DefaultListModel<String> playersListModel;
//    private JButton challengeButton;
//    private JTextArea messageArea;
//    private JLabel statusLabel;
//    private JPanel gamePanel;
//    private JLabel gameStatusLabel;
//    private JButton playWithComputerButton;
//    private JButton playWithPlayerButton;
//    private JPanel playersPanel;
//
//
//    public ClientTestUI(String title, int xPos, int yPos) {
//        super(title);
//        initializeUI();
//        setLocation(xPos, yPos);
//        setSize(500, 600);
//        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        connectToServer();
//        startMessageListener();
//    }
//
//    private void initializeUI() {
//        setLayout(new BorderLayout());
//
//        // Login Panel
//        JPanel loginPanel = new JPanel(new GridBagLayout());
//        loginPanel.setBorder(BorderFactory.createTitledBorder("Login"));
//        GridBagConstraints gbc = new GridBagConstraints();
//
//        gbc.gridx = 0; gbc.gridy = 0;
//        loginPanel.add(new JLabel("Username:"), gbc);
//        gbc.gridx = 1;
//        usernameField = new JTextField(15);
//        loginPanel.add(usernameField, gbc);
//
//        gbc.gridx = 0; gbc.gridy = 1;
//        loginPanel.add(new JLabel("Password:"), gbc);
//        gbc.gridx = 1;
//        passwordField = new JPasswordField(15);
//        loginPanel.add(passwordField, gbc);
//
//        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
//        JPanel buttonPanel = new JPanel();
//        loginButton = new JButton("Login");
//        logoutButton = new JButton("Logout");
//        logoutButton.setEnabled(false);
//        buttonPanel.add(loginButton);
//        buttonPanel.add(logoutButton);
//        loginPanel.add(buttonPanel, gbc);
//
//        // Game Mode Panel
//        JPanel gameModePanel = new JPanel(new GridLayout(1, 2, 10, 0));
//        gameModePanel.setBorder(BorderFactory.createTitledBorder("Game Mode"));
//
//        playWithComputerButton = new JButton("Play with Computer");
//        playWithPlayerButton = new JButton("Play with Player");
//
//        playWithComputerButton.setEnabled(false);
//        playWithPlayerButton.setEnabled(false);
//
//        gameModePanel.add(playWithComputerButton);
//        gameModePanel.add(playWithPlayerButton);
//
//        // Create a container for login and game mode
//        JPanel topPanel = new JPanel(new BorderLayout());
//        topPanel.add(loginPanel, BorderLayout.NORTH);
//        topPanel.add(gameModePanel, BorderLayout.CENTER);
//        add(topPanel, BorderLayout.NORTH);
//
//        // Online Players Panel - ASSIGN TO INSTANCE VARIABLE
//        playersPanel = new JPanel(new BorderLayout());
//        playersPanel.setBorder(BorderFactory.createTitledBorder("Online Players"));
//        playersListModel = new DefaultListModel<>();
//        onlinePlayersList = new JList<>(playersListModel);
//        onlinePlayersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
//        playersPanel.add(new JScrollPane(onlinePlayersList), BorderLayout.CENTER);
//
//        challengeButton = new JButton("Send Challenge");
//        challengeButton.setEnabled(false);
//        playersPanel.add(challengeButton, BorderLayout.SOUTH);
//
//        playersPanel.setVisible(false);
//        add(playersPanel, BorderLayout.WEST);
//
//        // Rest of your existing code...
//        JPanel messagePanel = new JPanel(new BorderLayout());
//        messagePanel.setBorder(BorderFactory.createTitledBorder("Messages"));
//        messageArea = new JTextArea(10, 30);
//        messageArea.setEditable(false);
//        messageArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
//        messagePanel.add(new JScrollPane(messageArea), BorderLayout.CENTER);
//
//        JPanel bottomPanel = new JPanel(new BorderLayout());
//        statusLabel = new JLabel("Status: Disconnected");
//        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
//        bottomPanel.add(statusLabel, BorderLayout.NORTH);
//
//        gamePanel = new JPanel(new BorderLayout());
//        gamePanel.setBorder(BorderFactory.createTitledBorder("Game"));
//        gameStatusLabel = new JLabel("No active game");
//        gamePanel.add(gameStatusLabel, BorderLayout.CENTER);
//        bottomPanel.add(gamePanel, BorderLayout.CENTER);
//
//        messagePanel.add(bottomPanel, BorderLayout.SOUTH);
//        add(messagePanel, BorderLayout.CENTER);
//
//        // Event Listeners
//        loginButton.addActionListener(e -> login());
//        logoutButton.addActionListener(e -> logout());
//        challengeButton.addActionListener(e -> sendChallenge());
//
//        ActionListener loginAction = e -> login();
//        usernameField.addActionListener(loginAction);
//        passwordField.addActionListener(loginAction);
//
//        playWithComputerButton.addActionListener(e -> startComputerGame());
//        playWithPlayerButton.addActionListener(e -> showPlayerSelection());
//    }
//
//    private void connectToServer() {
//        try {
//            socket = new Socket("localhost", 5000);
//            out = new PrintWriter(socket.getOutputStream(), true);
//            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//            statusLabel.setText("Status: Connected to server");
//            appendMessage("Connected to server");
//        } catch (IOException e) {
//            statusLabel.setText("Status: Connection failed");
//            appendMessage("Failed to connect to server: " + e.getMessage());
//            JOptionPane.showMessageDialog(this, "Could not connect to server", "Connection Error", JOptionPane.ERROR_MESSAGE);
//        }
//    }
//
//    private void startMessageListener() {
//        Thread listenerThread = new Thread(() -> {
//            try {
//                String message;
//                while ((message = in.readLine()) != null) {
//                    handleServerMessage(message);
//                }
//            } catch (IOException e) {
//                if (!socket.isClosed()) {
//                    SwingUtilities.invokeLater(() -> {
//                        statusLabel.setText("Status: Connection lost");
//                        appendMessage("Connection to server lost");
//                    });
//                }
//            }
//        });
//        listenerThread.setDaemon(true);
//        listenerThread.start();
//    }
//
//    private void startComputerGame() {
//        sendCommand("START_COMPUTER_GAME|" + username);
//        appendMessage("Starting game with computer...");
//        gameStatusLabel.setText("Starting game with computer...");
//    }
//
//    private void showPlayerSelection() {
//        playersPanel.setVisible(true);
//        challengeButton.setEnabled(true);
//        appendMessage("Select a player to challenge");
//    }
//
//    private void handleChallengeReceived(String challenger) {
//        int response = JOptionPane.showConfirmDialog(
//                this,
//                challenger + " has challenged you to a game! Do you accept?",
//                "Challenge Received",
//                JOptionPane.YES_NO_OPTION,
//                JOptionPane.QUESTION_MESSAGE
//        );
//
//        if (response == JOptionPane.YES_OPTION) {
//            // Fix parameter order: accepter first, then challenger
//            sendCommand("ACCEPT_CHALLENGE|" + username + "|" + challenger);
//            appendMessage("Accepted challenge from " + challenger);
//        } else {
//            // Fix parameter order: decliner first, then challenger
//            sendCommand("DECLINE_CHALLENGE|" + username + "|" + challenger);
//            appendMessage("Declined challenge from " + challenger);
//        }
//    }
//
//    private void updateOnlinePlayersList(String message) {
//        playersListModel.clear();
//        String[] parts = message.split("\\|");
//
//        for (int i = 1; i < parts.length; i++) {
//            if (!parts[i].isEmpty()) {
//                String[] playerInfo = parts[i].split(":");
//                if (playerInfo.length == 2) {
//                    String playerName = playerInfo[0];
//                    String status = playerInfo[1];
//
//                    // Only show ONLINE players and exclude yourself
//                    if ("ONLINE".equals(status) && !playerName.equals(username)) {
//                        playersListModel.addElement(playerName + " (" + status + ")");
//                    }
//                }
//            }
//        }
//    }
//
//    private void handleServerMessage(String message) {
//        SwingUtilities.invokeLater(() -> {
//            appendMessage("Server: " + message);
//
//            if (message.startsWith("LOGIN_SUCCESS")) {
//                isLoggedIn = true;
//                loginButton.setEnabled(false);
//                logoutButton.setEnabled(true);
//                playWithComputerButton.setEnabled(true);
//                playWithPlayerButton.setEnabled(true);
//                statusLabel.setText("Status: Logged in as " + username);
//                usernameField.setEditable(false);
//                passwordField.setEditable(false);
//
//            } else if (message.startsWith("LOGIN_FAILED")) {
//                JOptionPane.showMessageDialog(this, "Login failed: " + message.substring(13), "Login Error", JOptionPane.ERROR_MESSAGE);
//
//            } else if (message.startsWith("ONLINE_PLAYERS")) {
//                updateOnlinePlayersList(message);
//
//            } else if (message.startsWith("CHALLENGE_RECEIVED")) {
//                String[] parts = message.split("\\|");
//                if (parts.length >= 2) {
//                    String challenger = parts[1];
//                    appendMessage("Received challenge from " + challenger);
//                    handleChallengeReceived(challenger);
//                }
//
//            } else if (message.startsWith("CHALLENGE_SENT")) {
//                String[] parts = message.split("\\|");
//                if (parts.length >= 2) {
//                    String target = parts[1];
//                    appendMessage("Challenge sent to " + target);
//                    gameStatusLabel.setText("Waiting for " + target + " to respond...");
//                }
//
//            } else if (message.startsWith("CHALLENGE_ACCEPTED")) {
//                String[] parts = message.split("\\|");
//                if (parts.length >= 2) {
//                    String accepter = parts[1];
//                    appendMessage("Challenge accepted by " + accepter);
//                    gameStatusLabel.setText("Challenge accepted! Game starting soon...");
//                }
//
//            } else if (message.startsWith("CHALLENGE_DECLINED")) {
//                String[] parts = message.split("\\|");
//                if (parts.length >= 2) {
//                    String decliner = parts[1];
//                    appendMessage("Challenge declined by " + decliner);
//                    gameStatusLabel.setText("Challenge declined");
//                }
//
//            } else if (message.startsWith("GAME_STARTING")) {
//                String[] parts = message.split("\\|");
//                if (parts.length >= 2) {
//                    String opponent = parts[1];
//                    gameStatusLabel.setText("Game starting against " + opponent + " in 5 seconds...");
//                }
//
//            } else if (message.startsWith("GAME_STARTED")) {
//                String[] parts = message.split("\\|");
//                if (parts.length >= 3) {
//                    String gameId = parts[1];
//                    String opponent = parts[2];
//                    gameStatusLabel.setText("Game started! Playing against " + opponent);
//                    appendMessage("Game started with ID: " + gameId);
//                }
//
//            } else if (message.startsWith("GAME_END")) {
//                String[] parts = message.split("\\|");
//                if (parts.length >= 4) {
//                    String result = parts[1];
//                    String myScore = parts[2];
//                    String opponentScore = parts[3];
//                    gameStatusLabel.setText("Game ended - " + result + " (You: " + myScore + ", Opponent: " + opponentScore + ")");
//                }
//            }
//        });
//    }
//
//
//
//    private void login() {
//        String user = usernameField.getText().trim();
//        String pass = new String(passwordField.getPassword());
//
//        if (user.isEmpty() || pass.isEmpty()) {
//            JOptionPane.showMessageDialog(this, "Please enter username and password", "Input Error", JOptionPane.WARNING_MESSAGE);
//            return;
//        }
//
//        username = user;
//        sendCommand("LOGIN|" + user + "|" + pass);
//    }
//
//    private void logout() {
//        if (isLoggedIn) {
//            sendCommand("LOGOUT|" + username);
//            isLoggedIn = false;
//            loginButton.setEnabled(true);
//            logoutButton.setEnabled(false);
//            playWithComputerButton.setEnabled(false);
//            playWithPlayerButton.setEnabled(false);
//            challengeButton.setEnabled(false);
//            playersPanel.setVisible(false);
//            statusLabel.setText("Status: Logged out");
//            usernameField.setEditable(true);
//            passwordField.setEditable(true);
//            playersListModel.clear();
//            gameStatusLabel.setText("No active game");
//            username = null;
//        }
//    }
//
//    private void sendChallenge() {
//        String selectedPlayer = onlinePlayersList.getSelectedValue();
//        if (selectedPlayer == null) {
//            JOptionPane.showMessageDialog(this, "Please select a player to challenge", "No Selection", JOptionPane.WARNING_MESSAGE);
//            return;
//        }
//
//        // Extract username from "username (status)" format
//        String targetPlayer = selectedPlayer.split(" \\(")[0];
//        // Fix the parameter order: challenger first, then target
//        sendCommand("SEND_CHALLENGE|" + username + "|" + targetPlayer);
//        appendMessage("Sending challenge to " + targetPlayer);
//    }
//
//    private void sendCommand(String command) {
//        if (out != null) {
//            out.println(command);
//            appendMessage("Sent: " + command);
//        }
//    }
//
//    private void appendMessage(String message) {
//        messageArea.append("[" + java.time.LocalTime.now().toString().substring(0, 8) + "] " + message + "\n");
//        messageArea.setCaretPosition(messageArea.getDocument().getLength());
//    }
//
//    @Override
//    public void dispose() {
//        try {
//            if (isLoggedIn) {
//                logout();
//            }
//            if (socket != null && !socket.isClosed()) {
//                socket.close();
//            }
//        } catch (IOException e) {
//            System.err.println("Error closing socket: " + e.getMessage());
//        }
//        super.dispose();
//    }
//
//    public static void main(String[] args) {
//        SwingUtilities.invokeLater(() -> {
//
//            // Create two client windows
//            ClientTestUI client1 = new ClientTestUI("Client 1 - QCA Game", 100, 100);
//            ClientTestUI client2 = new ClientTestUI("Client 2 - QCA Game", 650, 100);
//
//            client1.setVisible(true);
//            client2.setVisible(true);
//
//            System.out.println("Two test clients launched");
//        });
//    }
//
//
//}