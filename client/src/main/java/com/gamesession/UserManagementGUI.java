//package org.example.client;
//
//import javax.swing.*;
//import javax.swing.table.DefaultTableModel;
//import java.awt.*;
//import java.awt.event.ActionEvent;
//import java.awt.event.ActionListener;
//import java.io.*;
//import java.net.Socket;
//import java.util.HashMap;
//import java.util.Map;
//
//public class UserManagementGUI extends JFrame {
//    private JTable userTable;
//    private DefaultTableModel tableModel;
//    private JTextField usernameField;
//    private JPasswordField passwordField;
//    private JTextField newUsernameField;
//    private JPasswordField newPasswordField;
//    private JTextField serverAddressField;
//    private JTextField serverPortField;
//    private Map<String, UserConnection> activeConnections;
//    // Add these fields to your class
//    private JComboBox<String> gameModeCombo;
//    private JComboBox<String> difficultyCombo;
//    private JButton startGameButton;
//    private JList<String> availablePlayersList;
//    private DefaultListModel<String> playersListModel;
//    private JButton challengeButton;
//    private Timer gameStartTimer;
//
//    public UserManagementGUI() {
//        activeConnections = new HashMap<>();
//        initializeComponents();
//        setupLayout();
//        setupEventListeners();
//    }
//
//    private void initializeComponents() {
//        setTitle("QCA Game - User Management");
//        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        setSize(800, 600);
//        setLocationRelativeTo(null);
//
//        // Table for active users
//        String[] columnNames = {"Username", "Status", "Actions"};
//        tableModel = new DefaultTableModel(columnNames, 0) {
//            @Override
//            public boolean isCellEditable(int row, int column) {
//                return column == 2; // Only actions column is editable
//            }
//        };
//        userTable = new JTable(tableModel);
//        userTable.setRowHeight(30);
//
//        // Input fields
//        usernameField = new JTextField(15);
//        passwordField = new JPasswordField(15);
//        newUsernameField = new JTextField(15);
//        newPasswordField = new JPasswordField(15);
//        serverAddressField = new JTextField("localhost", 10);
//        serverPortField = new JTextField("5000", 5);
//        // Game mode components
//        String[] gameModes = {"Play with Computer", "Play in Pairs"};
//        gameModeCombo = new JComboBox<>(gameModes);
//
//        String[] difficulties = {"Easy", "Medium", "Hard"};
//        difficultyCombo = new JComboBox<>(difficulties);
//
//        startGameButton = new JButton("Start Game");
//
//        // Available players list for pair mode
//        playersListModel = new DefaultListModel<>();
//        availablePlayersList = new JList<>(playersListModel);
//        availablePlayersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
//        challengeButton = new JButton("Send Challenge");
//        challengeButton.setEnabled(false);
//
//        // Add listener for available players list selection
//        availablePlayersList.addListSelectionListener(e -> {
//            if (!e.getValueIsAdjusting()) {
//                challengeButton.setEnabled(availablePlayersList.getSelectedValue() != null &&
//                        "Play in Pairs".equals(gameModeCombo.getSelectedItem()));
//            }
//        });
//    }
//
//    private void setupLayout() {
//        setLayout(new BorderLayout());
//
//        // Top panel - Server connection
//        JPanel serverPanel = new JPanel(new FlowLayout());
//        serverPanel.setBorder(BorderFactory.createTitledBorder("Server Connection"));
//        serverPanel.add(new JLabel("Address:"));
//        serverPanel.add(serverAddressField);
//        serverPanel.add(new JLabel("Port:"));
//        serverPanel.add(serverPortField);
//
//        // Left panel - User actions
//        JPanel leftPanel = new JPanel(new GridBagLayout());
//        leftPanel.setBorder(BorderFactory.createTitledBorder("User Actions"));
//        GridBagConstraints gbc = new GridBagConstraints();
//        gbc.insets = new Insets(5, 5, 5, 5);
//
//        // Login section
//        gbc.gridx = 0; gbc.gridy = 0;
//        leftPanel.add(new JLabel("Username:"), gbc);
//        gbc.gridx = 1;
//        leftPanel.add(usernameField, gbc);
//
//        gbc.gridx = 0; gbc.gridy = 1;
//        leftPanel.add(new JLabel("Password:"), gbc);
//        gbc.gridx = 1;
//        leftPanel.add(passwordField, gbc);
//
//        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
//        JButton loginButton = new JButton("Login");
//        leftPanel.add(loginButton, gbc);
//
//        gbc.gridy = 3;
//        JButton registerButton = new JButton("Register");
//        leftPanel.add(registerButton, gbc);
//
//        // Update section
//        gbc.gridy = 4; gbc.gridwidth = 1;
//        leftPanel.add(new JLabel("New Username:"), gbc);
//        gbc.gridx = 1;
//        leftPanel.add(newUsernameField, gbc);
//
//        gbc.gridx = 0; gbc.gridy = 5;
//        leftPanel.add(new JLabel("New Password:"), gbc);
//        gbc.gridx = 1;
//        leftPanel.add(newPasswordField, gbc);
//
//        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
//        JButton updateButton = new JButton("Update Selected User");
//        leftPanel.add(updateButton, gbc);
//
//        gbc.gridy = 7;
//        JButton logoutButton = new JButton("Logout Selected User");
//        leftPanel.add(logoutButton, gbc);
//
//        gbc.gridy = 8;
//        JButton logoutAllButton = new JButton("Logout All Users");
//        leftPanel.add(logoutAllButton, gbc);
//
//        // Center panel - User table
//        JScrollPane scrollPane = new JScrollPane(userTable);
//        scrollPane.setBorder(BorderFactory.createTitledBorder("Active Users"));
//
//        // Add components to main frame
//        add(serverPanel, BorderLayout.NORTH);
//        add(leftPanel, BorderLayout.WEST);
//        add(scrollPane, BorderLayout.CENTER);
//
//        // Setup button actions
//        loginButton.addActionListener(e -> loginUser());
//        registerButton.addActionListener(e -> registerUser());
//        updateButton.addActionListener(e -> updateSelectedUser());
//        logoutButton.addActionListener(e -> logoutSelectedUser());
//        logoutAllButton.addActionListener(e -> logoutAllUsers());
//
//        // Game panel - Right side
//        JPanel gamePanel = new JPanel(new GridBagLayout());
//        gamePanel.setBorder(BorderFactory.createTitledBorder("Game Options"));
//        GridBagConstraints gameGbc = new GridBagConstraints();
//        gameGbc.insets = new Insets(5, 5, 5, 5);
//
//        // Game mode selection
//        gameGbc.gridx = 0; gameGbc.gridy = 0;
//        gamePanel.add(new JLabel("Game Mode:"), gameGbc);
//        gameGbc.gridx = 1;
//        gamePanel.add(gameModeCombo, gameGbc);
//
//        // Difficulty selection (for computer mode)
//        gameGbc.gridx = 0; gameGbc.gridy = 1;
//        gamePanel.add(new JLabel("Difficulty:"), gameGbc);
//        gameGbc.gridx = 1;
//        gamePanel.add(difficultyCombo, gameGbc);
//
//        // Available players list (for pair mode)
//        gameGbc.gridx = 0; gameGbc.gridy = 2;
//        gamePanel.add(new JLabel("Available Players:"), gameGbc);
//        gameGbc.gridx = 1; gameGbc.gridheight = 3;
//        JScrollPane playersScroll = new JScrollPane(availablePlayersList);
//        playersScroll.setPreferredSize(new Dimension(150, 100));
//        gamePanel.add(playersScroll, gameGbc);
//
//        // Start game button
//        gameGbc.gridx = 0; gameGbc.gridy = 5; gameGbc.gridwidth = 2; gameGbc.gridheight = 1;
//        JPanel buttonPanel = new JPanel(new FlowLayout());
//        buttonPanel.add(startGameButton);
//        buttonPanel.add(challengeButton);
//        gamePanel.add(buttonPanel, gameGbc);
//
//        // Update main layout
//        add(gamePanel, BorderLayout.EAST);
//
//        // Setup game mode change listener
//        gameModeCombo.addActionListener(e -> updateGameModeUI());
//        startGameButton.addActionListener(e -> startGame());
//
//        // Initial UI update
//        updateGameModeUI();
//
//        challengeButton.addActionListener(e -> sendChallenge());
//    }
//
//    private void setupEventListeners() {
//        // Add double-click listener for table
//        userTable.addMouseListener(new java.awt.event.MouseAdapter() {
//            @Override
//            public void mouseClicked(java.awt.event.MouseEvent evt) {
//                if (evt.getClickCount() == 2) {
//                    int row = userTable.getSelectedRow();
//                    if (row != -1) {
//                        String username = (String) tableModel.getValueAt(row, 0);
//                        showUserDetails(username);
//                    }
//                }
//            }
//        });
//    }
//
//    // Update updateGameModeUI() method
//    private void updateGameModeUI() {
//        String selectedMode = (String) gameModeCombo.getSelectedItem();
//        boolean isComputerMode = "Play with Computer".equals(selectedMode);
//        boolean isPairMode = "Play in Pairs".equals(selectedMode);
//
//        difficultyCombo.setEnabled(isComputerMode);
//        availablePlayersList.setEnabled(isPairMode);
//        startGameButton.setEnabled(isComputerMode);
//        challengeButton.setEnabled(isPairMode && availablePlayersList.getSelectedValue() != null);
//
//        if (isPairMode) {
//            updateAvailablePlayersList();
//        }
//    }
//
//    // Add new method to send challenge
//    private void sendChallenge() {
//        int selectedRow = userTable.getSelectedRow();
//        String selectedOpponent = availablePlayersList.getSelectedValue();
//
//        if (selectedRow == -1) {
//            JOptionPane.showMessageDialog(this, "Please select a player");
//            return;
//        }
//
//        if (selectedOpponent == null) {
//            JOptionPane.showMessageDialog(this, "Please select an opponent");
//            return;
//        }
//
//        String challenger = (String) tableModel.getValueAt(selectedRow, 0); // Add this line
//
//        if (challenger.equals(selectedOpponent)) {
//            JOptionPane.showMessageDialog(this, "Cannot challenge yourself");
//            return;
//        }
//
//        UserConnection connection = activeConnections.get(challenger);
//        if (connection == null) {
//            JOptionPane.showMessageDialog(this, "Player not connected");
//            return;
//        }
//        try {
//            String command = "SEND_CHALLENGE|" + selectedOpponent;
//            String response = connection.sendCommand(command);
//
//            if (response.startsWith("CHALLENGE_SENT")) {
//                JOptionPane.showMessageDialog(this,
//                        "Challenge sent to " + selectedOpponent + ". Waiting for response...");
//                challengeButton.setEnabled(false);
//            } else {
//                JOptionPane.showMessageDialog(this, "Failed to send challenge: " + response);
//            }
//        } catch (Exception e) {
//            JOptionPane.showMessageDialog(this, "Error sending challenge: " + e.getMessage());
//        }
//    }
//
//    // Add method to handle incoming challenges and responses
//    public void handleIncomingMessage(String message, String username) {
//        SwingUtilities.invokeLater(() -> {
//            String[] parts = message.split("\\|");
//            String messageType = parts[0];
//
//            switch (messageType) {
//                case "CHALLENGE_RECEIVED":
//                    handleChallengeReceived(parts[1], username);
//                    break;
//                case "CHALLENGE_ACCEPTED":
//                    handleChallengeAccepted(parts[1], username);
//                    break;
//                case "CHALLENGE_DECLINED":
//                    handleChallengeDeclined(parts[1], username);
//                    break;
//                case "GAME_STARTING":
//                    handleGameStarting(parts[1], username);
//                    break;
//                case "GAME_STARTED":
//                    // Fix: Extract gameId and opponent from the message
//                    String gameId = parts.length > 1 ? parts[1] : "unknown";
//                    String opponent = parts.length > 2 ? parts[2] : "unknown";
//                    handleGameStarted(gameId, opponent, username);
//                    break;
//            }
//        });
//    }
//
//    private void handleChallengeReceived(String challenger, String challenged) {
//        int option = JOptionPane.showConfirmDialog(this,
//                "You have received a challenge from " + challenger + ". Do you accept?",
//                "Challenge Received", JOptionPane.YES_NO_OPTION);
//
//        UserConnection connection = activeConnections.get(challenged);
//        if (connection == null) {
//            JOptionPane.showMessageDialog(this, "Player not connected");
//            return;
//        }
//
//        try {
//            String responseCommand = option == JOptionPane.YES_OPTION ?
//                    "CHALLENGE_RESPONSE|ACCEPT" : "CHALLENGE_RESPONSE|DECLINE";
//            String response = connection.sendCommand(responseCommand + "|" + challenger);
//
//            if (response.startsWith("RESPONSE_SENT")) {
//                String msg = option == JOptionPane.YES_OPTION ?
//                        "Challenge accepted. Waiting for game to start..." :
//                        "Challenge declined.";
//                JOptionPane.showMessageDialog(this, msg);
//            } else {
//                JOptionPane.showMessageDialog(this, "Failed to send response: " + response);
//            }
//        } catch (Exception e) {
//            JOptionPane.showMessageDialog(this, "Error sending response: " + e.getMessage());
//        }
//    }
//
//    private void handleChallengeAccepted(String opponent, String username) {
//        JOptionPane.showMessageDialog(this,
//                opponent + " accepted your challenge! Game will start in 5 seconds...");
//        challengeButton.setEnabled(true);
//    }
//
//    private void handleChallengeDeclined(String opponent, String username) {
//        JOptionPane.showMessageDialog(this,
//                opponent + " declined your challenge.");
//        challengeButton.setEnabled(true);
//    }
//
//    private void handleGameStarting(String opponent, String username) {
//        JOptionPane.showMessageDialog(this,
//                "Game with " + opponent + " starting in 5 seconds...");
//
//        // Update player status to "Starting Game"
//        for (int i = 0; i < tableModel.getRowCount(); i++) {
//            String tableUsername = (String) tableModel.getValueAt(i, 0);
//            if (tableUsername.equals(username)) {
//                tableModel.setValueAt("Starting Game", i, 1);
//                break;
//            }
//        }
//    }
//
//    // Add this method to UserManagementGUI.java
//    private void handleGameStarted(String gameId, String opponent, String username) {
//        SwingUtilities.invokeLater(() -> {
//            UserConnection connection = activeConnections.get(username);
//            if (connection != null) {
//                try {
//                    // Create and show game room UI
//                    GameRoomUI gameRoom = new GameRoomUI(username, opponent, gameId, connection.getSocket());
//                    gameRoom.setVisible(true);
//
//                    // Update player status
//                    for (int i = 0; i < tableModel.getRowCount(); i++) {
//                        String tableUsername = (String) tableModel.getValueAt(i, 0);
//                        if (tableUsername.equals(username)) {
//                            tableModel.setValueAt("In Game Room", i, 1);
//                            break;
//                        }
//                    }
//
//                    JOptionPane.showMessageDialog(this,
//                            "Game room opened for " + username + " vs " + opponent);
//
//                } catch (Exception e) {
//                    JOptionPane.showMessageDialog(this,
//                            "Error opening game room: " + e.getMessage());
//                }
//            }
//        });
//    }
//
//    private void updateAvailablePlayersList() {
//        playersListModel.clear();
//        for (String username : activeConnections.keySet()) {
//            playersListModel.addElement(username);
//        }
//    }
//
//    private void startGame() {
//        String selectedMode = (String) gameModeCombo.getSelectedItem();
//
//        if ("Play with Computer".equals(selectedMode)) {
//            startComputerGame();
//        } else {
//            startPairGame();
//        }
//    }
//
//    private void startComputerGame() {
//        int selectedRow = userTable.getSelectedRow();
//        if (selectedRow == -1) {
//            JOptionPane.showMessageDialog(this, "Please select a player to start the game");
//            return;
//        }
//
//        String username = (String) tableModel.getValueAt(selectedRow, 0);
//        String difficulty = (String) difficultyCombo.getSelectedItem();
//
//        UserConnection connection = activeConnections.get(username);
//        if (connection == null) {
//            JOptionPane.showMessageDialog(this, "Player not connected");
//            return;
//        }
//
//        try {
//            String command = "START_COMPUTER_GAME|" + difficulty.toLowerCase();
//            String response = connection.sendCommand(command);
//
//            if (response.startsWith("GAME_STARTED")) {
//                JOptionPane.showMessageDialog(this,
//                        "Computer game started for " + username + " (Difficulty: " + difficulty + ")");
//                // Update player status
//                tableModel.setValueAt("In Game", selectedRow, 1);
//            } else {
//                JOptionPane.showMessageDialog(this, "Failed to start game: " + response);
//            }
//        } catch (Exception e) {
//            JOptionPane.showMessageDialog(this, "Error starting game: " + e.getMessage());
//        }
//    }
//
//    private void startPairGame() {
//        int selectedRow = userTable.getSelectedRow();
//        String selectedOpponent = availablePlayersList.getSelectedValue();
//
//        if (selectedRow == -1) {
//            JOptionPane.showMessageDialog(this, "Please select a player");
//            return;
//        }
//
//        if (selectedOpponent == null) {
//            JOptionPane.showMessageDialog(this, "Please select an opponent");
//            return;
//        }
//
//        String player1 = (String) tableModel.getValueAt(selectedRow, 0);
//
//        if (player1.equals(selectedOpponent)) {
//            JOptionPane.showMessageDialog(this, "Cannot play against yourself");
//            return;
//        }
//
//        UserConnection connection1 = activeConnections.get(player1);
//        UserConnection connection2 = activeConnections.get(selectedOpponent);
//
//        if (connection1 == null || connection2 == null) {
//            JOptionPane.showMessageDialog(this, "One or both players not connected");
//            return;
//        }
//
//        try {
//            // Send game invitation to both players
//            String command1 = "START_PAIR_GAME|" + selectedOpponent;
//            String command2 = "START_PAIR_GAME|" + player1;
//
//            String response1 = connection1.sendCommand(command1);
//            String response2 = connection2.sendCommand(command2);
//
//            if (response1.startsWith("GAME_STARTED") && response2.startsWith("GAME_STARTED")) {
//                JOptionPane.showMessageDialog(this,
//                        "Pair game started between " + player1 + " and " + selectedOpponent);
//
//                // Update both players' status in table
//                for (int i = 0; i < tableModel.getRowCount(); i++) {
//                    String username = (String) tableModel.getValueAt(i, 0);
//                    if (username.equals(player1) || username.equals(selectedOpponent)) {
//                        tableModel.setValueAt("In Game", i, 1);
//                    }
//                }
//            } else {
//                JOptionPane.showMessageDialog(this, "Failed to start pair game");
//            }
//        } catch (Exception e) {
//            JOptionPane.showMessageDialog(this, "Error starting pair game: " + e.getMessage());
//        }
//    }
//
//    // Update the loginUser() method in UserManagementGUI
//    private void loginUser() {
//        String username = usernameField.getText().trim();
//        String password = new String(passwordField.getPassword());
//
//        if (username.isEmpty() || password.isEmpty()) {
//            JOptionPane.showMessageDialog(this, "Please enter username and password");
//            return;
//        }
//
//        if (activeConnections.containsKey(username)) {
//            JOptionPane.showMessageDialog(this, "User already logged in");
//            return;
//        }
//
//        try {
//            String serverAddress = serverAddressField.getText().trim();
//            int serverPort = Integer.parseInt(serverPortField.getText().trim());
//
//            UserConnection connection = new UserConnection(serverAddress, serverPort, username);
//            String command = "LOGIN|" + username + "|" + password;
//            System.out.println("Sending command: " + command);
//
//            String response = connection.sendCommand(command);
//            System.out.println("Server response: " + response);
//
//            if (response != null && response.startsWith("LOGIN_SUCCESS")) {
//                activeConnections.put(username, connection);
//                tableModel.addRow(new Object[]{username, "Online", "Actions"});
//
//                // Start listening for server messages
//                connection.startListening(this, username);
//
//                System.out.println("✓ User logged in: " + username);
//                JOptionPane.showMessageDialog(this, "Login successful for " + username);
//                clearLoginFields();
//                updateAvailablePlayersList();
//            } else {
//                connection.disconnect();
//                String errorMsg = response != null ? response : "No response from server";
//                JOptionPane.showMessageDialog(this, "Login failed: " + errorMsg);
//            }
//        } catch (Exception e) {
//            JOptionPane.showMessageDialog(this, "Connection error: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
//
//    private void registerUser() {
//        String username = usernameField.getText().trim();
//        String password = new String(passwordField.getPassword());
//
//        if (username.isEmpty() || password.isEmpty()) {
//            JOptionPane.showMessageDialog(this, "Please enter username and password");
//            return;
//        }
//
//        try {
//            String serverAddress = serverAddressField.getText().trim();
//            int serverPort = Integer.parseInt(serverPortField.getText().trim());
//
//            UserConnection connection = new UserConnection(serverAddress, serverPort, username);
//            String response = connection.sendCommand("REGISTER|" + username + "|" + password);
//            connection.disconnect();
//
//            if (response.startsWith("REGISTER_SUCCESS")) {
//                JOptionPane.showMessageDialog(this, "Registration successful for " + username);
//                clearLoginFields();
//            } else {
//                JOptionPane.showMessageDialog(this, "Registration failed: " + response);
//            }
//        } catch (Exception e) {
//            JOptionPane.showMessageDialog(this, "Connection error: " + e.getMessage());
//        }
//    }
//
//    private void updateSelectedUser() {
//        int selectedRow = userTable.getSelectedRow();
//        if (selectedRow == -1) {
//            JOptionPane.showMessageDialog(this, "Please select a user to update");
//            return;
//        }
//
//        String username = (String) tableModel.getValueAt(selectedRow, 0);
//        String newUsername = newUsernameField.getText().trim();
//        String newPassword = new String(newPasswordField.getPassword());
//
//        if (newUsername.isEmpty() && newPassword.isEmpty()) {
//            JOptionPane.showMessageDialog(this, "Please enter new username or password");
//            return;
//        }
//
//        UserConnection connection = activeConnections.get(username);
//        if (connection == null) {
//            JOptionPane.showMessageDialog(this, "User not connected");
//            return;
//        }
//
//        try {
//            StringBuilder command = new StringBuilder("UPDATE");
//            if (!newUsername.isEmpty()) {
//                command.append("|username|").append(newUsername);
//            }
//            if (!newPassword.isEmpty()) {
//                command.append("|password|").append(newPassword);
//            }
//
//            String response = connection.sendCommand(command.toString());
//
//            if (response.startsWith("UPDATE_SUCCESS")) {
//                if (!newUsername.isEmpty()) {
//                    // Update table and connection map
//                    activeConnections.remove(username);
//                    activeConnections.put(newUsername, connection);
//                    tableModel.setValueAt(newUsername, selectedRow, 0);
//                }
//                JOptionPane.showMessageDialog(this, "Update successful");
//                clearUpdateFields();
//            } else {
//                JOptionPane.showMessageDialog(this, "Update failed: " + response);
//            }
//        } catch (Exception e) {
//            JOptionPane.showMessageDialog(this, "Update error: " + e.getMessage());
//        }
//    }
//
//    private void logoutSelectedUser() {
//        int selectedRow = userTable.getSelectedRow();
//        if (selectedRow == -1) {
//            JOptionPane.showMessageDialog(this, "Please select a user to logout");
//            return;
//        }
//
//        String username = (String) tableModel.getValueAt(selectedRow, 0);
//        logoutUser(username, selectedRow);
//    }
//
//    private void logoutAllUsers() {
//        for (String username : activeConnections.keySet()) {
//            UserConnection connection = activeConnections.get(username);
//            connection.disconnect();
//        }
//        activeConnections.clear();
//        tableModel.setRowCount(0);
//        JOptionPane.showMessageDialog(this, "All users logged out");
//    }
//
//    private void logoutUser(String username, int row) {
//        UserConnection connection = activeConnections.get(username);
//        if (connection != null) {
//            connection.disconnect();
//            activeConnections.remove(username);
//            tableModel.removeRow(row);
//            updateAvailablePlayersList();
//            JOptionPane.showMessageDialog(this, username + " logged out successfully");
//        }
//    }
//
//    private void showUserDetails(String username) {
//        UserConnection connection = activeConnections.get(username);
//        if (connection != null) {
//            String details = "Username: " + username + "\n" +
//                           "Status: Online\n" +
//                           "Server: " + connection.getServerAddress() + ":" + connection.getServerPort();
//            JOptionPane.showMessageDialog(this, details, "User Details", JOptionPane.INFORMATION_MESSAGE);
//        }
//    }
//
//    private void clearLoginFields() {
//        usernameField.setText("");
//        passwordField.setText("");
//    }
//
//    private void clearUpdateFields() {
//        newUsernameField.setText("");
//        newPasswordField.setText("");
//    }
//
//    public static void main(String[] args) {
//        SwingUtilities.invokeLater(() -> {
//            try {
//                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//            new UserManagementGUI().setVisible(true);
//        });
//    }
//
//    // Add this method to UserManagementGUI class
//    public void updateOnlinePlayers(String message) {
//        // Parse ONLINE_PLAYERS|player1:ONLINE|player2:BUSY|player3:ONLINE...
//        String[] parts = message.split("\\|");
//
//        playersListModel.clear();
//        tableModel.setRowCount(0);
//
//        for (int i = 1; i < parts.length; i++) {
//            if (!parts[i].isEmpty()) {
//                String[] playerInfo = parts[i].split(":");
//                if (playerInfo.length == 2) {
//                    String username = playerInfo[0];
//                    String status = playerInfo[1];
//
//                    // Add to players list (only show ONLINE players for challenges)
//                    if ("ONLINE".equals(status)) {
//                        playersListModel.addElement(username);
//                    }
//
//                    // Add to main table
//                    tableModel.addRow(new Object[]{username, status, "Actions"});
//                }
//            }
//        }
//    }
//
//
//
//    // Inner class for managing user connections
//    private static class UserConnection {
//        private Socket socket;
//        private BufferedReader in;
//        private PrintWriter out;
//        private String serverAddress;
//        private int serverPort;
//
//        public UserConnection(String serverAddress, int serverPort, String username) throws IOException {
//            this.serverAddress = serverAddress;
//            this.serverPort = serverPort;
//            this.socket = new Socket(serverAddress, serverPort);
//            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//            this.out = new PrintWriter(socket.getOutputStream(), true);
//        }
//
//        public String sendCommand(String command) throws IOException {
//            out.println(command);
//            String response = in.readLine();
//            if (response == null) {
//                throw new IOException("Server closed connection");
//            }
//            return response;
//        }
//
//        // Update this method in the UserConnection inner class
//// Update this method in the UserConnection inner class
//        public void startListening(UserManagementGUI gui, String username) {
//            Thread listenerThread = new Thread(() -> {
//                try {
//                    String message;
//                    while ((message = in.readLine()) != null) {
//                        System.out.println("Received from server: " + message);
//
//                        final String finalMessage = message; // Make it effectively final
//
//                        if (message.startsWith("ONLINE_PLAYERS")) {
//                            // Update the available players list
//                            SwingUtilities.invokeLater(() -> gui.updateOnlinePlayers(finalMessage));
//                        } else {
//                            // Handle other messages like challenges, game invites, etc.
//                            gui.handleIncomingMessage(finalMessage, username);
//                        }
//                    }
//                } catch (IOException e) {
//                    if (!socket.isClosed()) {
//                        System.err.println("Connection lost: " + e.getMessage());
//                    }
//                }
//            });
//            listenerThread.setDaemon(true);
//            listenerThread.start();
//        }
//
//        public void disconnect() {
//            try {
//                if (out != null) out.println("QUIT");
//                if (socket != null) socket.close();
//                if (in != null) in.close();
//                if (out != null) out.close();
//            } catch (IOException e) {
//                System.err.println("Error disconnecting: " + e.getMessage());
//            }
//        }
//
//        public String getServerAddress() {
//            return serverAddress;
//        }
//
//        public int getServerPort() {
//            return serverPort;
//        }
//
//        public Socket getSocket() {
//            return socket;
//        }
//    }
//}