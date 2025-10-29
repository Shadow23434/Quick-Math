package com.gamesession;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class GameClientUI extends JFrame {
    private final GameClient client;
    private final JPanel mainPanel; // Panel chính để chứa các panel con
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JPanel loginPanel;
    private JPanel gamePanel;
    private JLabel targetLabel;
    private JLabel numbersLabel;
    private JTextField answerField;
    private JButton submitButton;
    private JLabel timerLabel;
    private JLabel statusLabel;
    private JTextArea gameLogArea;
    private Timer countdownTimer;
    private int secondsLeft = 30;

    private String currentTarget = "";
    private String[] currentNumbers = {};
    private int currentRound = 0;
    private boolean roundActive = false;

    public GameClientUI(String title) {
        super(title);
        System.out.println("Khởi tạo GameClientUI: " + title);

        // Sử dụng CardLayout để chuyển đổi giữa các panel
        mainPanel = new JPanel(new CardLayout());
        this.client = new GameClient("localhost", 5000);

        initComponents();
        setupLayout();
        setupListeners();

        // Thêm mainPanel vào JFrame
        add(mainPanel);

        setSize(500, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                client.disconnect();
            }
        });

        System.out.println("GameClientUI đã tạo xong, hiển thị loginPanel");
    }

    private void initComponents() {
        System.out.println("Đang khởi tạo các thành phần...");

        // Login components
        loginPanel = new JPanel(new GridBagLayout());
        usernameField = new JTextField(15);
        passwordField = new JPasswordField(15);
        loginButton = new JButton("Login");

        // Game components
        gamePanel = new JPanel(new BorderLayout());
        JPanel gameTopPanel = new JPanel(new GridLayout(4, 1));
        targetLabel = new JLabel("Target: ");
        numbersLabel = new JLabel("Numbers: ");
        timerLabel = new JLabel("Time: 30s");

        JPanel inputPanel = new JPanel(new FlowLayout());
        answerField = new JTextField(20);
        submitButton = new JButton("Submit");
        submitButton.setEnabled(false);

        statusLabel = new JLabel("Not connected");
        gameLogArea = new JTextArea(10, 40); // Xác định rõ kích thước
        gameLogArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(gameLogArea);

        // Add components to panels
        gameTopPanel.add(targetLabel);
        gameTopPanel.add(numbersLabel);
        gameTopPanel.add(timerLabel);
        gameTopPanel.add(statusLabel);

        inputPanel.add(answerField);
        inputPanel.add(submitButton);

        gamePanel.add(gameTopPanel, BorderLayout.NORTH);
        gamePanel.add(inputPanel, BorderLayout.CENTER);
        gamePanel.add(scrollPane, BorderLayout.SOUTH);

        // Setup countdown timer
        countdownTimer = new Timer(1000, e -> updateTimer());

        System.out.println("Khởi tạo các thành phần hoàn tất");
    }

    private void setupLayout() {
        System.out.println("Đang thiết lập bố cục...");

        // Thiết lập loginPanel
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 0;
        gbc.gridy = 0;
        loginPanel.add(new JLabel("Username:"), gbc);

        gbc.gridx = 1;
        loginPanel.add(usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        loginPanel.add(new JLabel("Password:"), gbc);

        gbc.gridx = 1;
        loginPanel.add(passwordField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        loginPanel.add(loginButton, gbc);

        // Thêm panel vào mainPanel với CardLayout
        mainPanel.add(loginPanel, "login");
        mainPanel.add(gamePanel, "game");

        // Hiển thị login panel trước
        CardLayout cl = (CardLayout) mainPanel.getLayout();
        cl.show(mainPanel, "login");

        System.out.println("Thiết lập bố cục hoàn tất");
    }

    private void setupListeners() {
        System.out.println("Đang thiết lập các listener...");

        loginButton.addActionListener(e -> {
            System.out.println("Login button clicked");
            handleLogin();
        });

        submitButton.addActionListener(e -> {
            System.out.println("Submit button clicked");
            handleSubmit();
        });

        // Setup client message handler
        client.setMessageHandler(message -> {
            System.out.println("Nhận tin nhắn từ server: " + message);
            handleServerMessage(message);
        });

        System.out.println("Thiết lập các listener hoàn tất");
    }

    private void handleLogin() {
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());

        System.out.println("Xử lý đăng nhập cho: " + username);

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter username and password");
            return;
        }

        if (!client.isConnected()) {
            System.out.println("Đang kết nối đến server...");
            if (!client.connect()) {
                JOptionPane.showMessageDialog(this, "Failed to connect to server");
                return;
            }
            System.out.println("Đã kết nối đến server");
        }

        client.login(username, password);
    }

    private void handleSubmit() {
        if (!roundActive) {
            log("No active round");
            return;
        }

        String answer = answerField.getText().trim();
        if (answer.isEmpty()) {
            log("Please enter an answer");
            return;
        }

        client.submitAnswer(answer);
        submitButton.setEnabled(false);
        answerField.setEnabled(false);
        statusLabel.setText("Answer submitted: " + answer);
    }

    private void handleServerMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            log("Received: " + message);

            String[] parts = message.split("\\|");
            String command = parts[0];

            switch (command) {
                case "LOGIN_SUCCESS":
                    loginSuccessful();
                    break;

                case "LOGIN_FAILED":
                    loginFailed(parts.length > 1 ? parts[1] : "Unknown error");
                    break;

                case "WAITING_ROOM":
                    handleWaitingRoom(parts);
                    break;

                case "COUNTDOWN":
                    handleCountdown(parts);
                    break;

                case "GAME_READY":
                    handleGameReady();
                    break;

                case "ROUND_START":
                    handleRoundStart(parts);
                    break;

                case "ANSWER_SUBMITTED":
                    handleAnswerSubmitted(parts);
                    break;

                case "ROUND_RESULT":
                    handleRoundResult(parts);
                    break;

                case "ROUND_TIMEOUT":
                    handleRoundTimeout();
                    break;

                case "GAME_END":
                    handleGameEnd(parts);
                    break;
            }
        });
    }

    private void loginSuccessful() {
        log("Login successful");

        // Chuyển từ login panel sang game panel
        CardLayout cl = (CardLayout) mainPanel.getLayout();
        cl.show(mainPanel, "game");

        statusLabel.setText("Logged in as: " + client.getUsername());

        // Tự động gửi JOIN_QUEUE
        System.out.println("Tự động gửi JOIN_QUEUE");
        client.sendMessage("JOIN_QUEUE");
    }

    private void loginFailed(String reason) {
        log("Login failed: " + reason);
        JOptionPane.showMessageDialog(this, "Login failed: " + reason);
    }

    private void handleWaitingRoom(String[] parts) {
        if (parts.length > 1) {
            int countdown = Integer.parseInt(parts[1]);
            statusLabel.setText("Entering game in " + countdown + " seconds");
        }
    }

    private void handleCountdown(String[] parts) {
        if (parts.length > 1) {
            int countdown = Integer.parseInt(parts[1]);
            statusLabel.setText("Game starting in " + countdown);
        }
    }

    private void handleGameReady() {
        statusLabel.setText("Game ready!");
    }

    private void handleRoundStart(String[] parts) {
        if (parts.length < 4) return;

        currentRound = Integer.parseInt(parts[1]);
        currentTarget = parts[2];
        // Parse numbers from format [1, 2, 3, 4, 5]
        String numbersStr = parts[3].replaceAll("\\[|\\]|\\s", "");
        currentNumbers = numbersStr.split(",");

        targetLabel.setText("Target: " + currentTarget);
        numbersLabel.setText("Numbers: " + String.join(", ", currentNumbers));

        // Reset UI for new round
        answerField.setText("");
        answerField.setEnabled(true);
        submitButton.setEnabled(true);
        statusLabel.setText("Round " + currentRound + " started");

        // Start countdown
        secondsLeft = 30;
        timerLabel.setText("Time: " + secondsLeft + "s");
        roundActive = true;
        countdownTimer.start();
    }

    private void handleAnswerSubmitted(String[] parts) {
        if (parts.length > 1) {
            double time = Double.parseDouble(parts[1]);
            statusLabel.setText("Answer submitted in " + time + " seconds");
        } else {
            statusLabel.setText("Answer submitted");
        }
    }

    private void handleRoundResult(String[] parts) {
        if (parts.length < 6) return;

        String result = parts[1];
        int yourScore = Integer.parseInt(parts[2]);
        int opponentScore = Integer.parseInt(parts[3]);
        String yourAnswer = parts[4];
        String opponentAnswer = parts[5];

        StringBuilder resultMsg = new StringBuilder();
        resultMsg.append("Round ").append(currentRound).append(" result:\n");
        resultMsg.append("Your answer: ").append(yourAnswer).append(" (").append(result).append(")\n");
        resultMsg.append("Opponent answer: ").append(opponentAnswer).append("\n");
        resultMsg.append("Score: ").append(yourScore).append(" - ").append(opponentScore);

        log(resultMsg.toString());
        statusLabel.setText(result.equals("correct") ? "Correct!" : "Wrong!");

        // Stop timer
        roundActive = false;
        countdownTimer.stop();
    }

    private void handleRoundTimeout() {
        log("Round timed out!");
        statusLabel.setText("Time's up!");
        roundActive = false;
        countdownTimer.stop();
    }

    private void handleGameEnd(String[] parts) {
        if (parts.length < 4) return;

        String result = parts[1];
        int yourScore = Integer.parseInt(parts[2]);
        int opponentScore = Integer.parseInt(parts[3]);

        StringBuilder resultMsg = new StringBuilder();
        resultMsg.append("Game ended: ");

        if (result.equals("WIN")) {
            resultMsg.append("You win! ");
        } else if (result.equals("LOSE")) {
            resultMsg.append("You lose. ");
        } else if (result.equals("DRAW")) {
            resultMsg.append("It's a draw! ");
        } else if (result.equals("WIN_TIME")) {
            resultMsg.append("You win by time! ");
        } else if (result.equals("LOSE_TIME")) {
            resultMsg.append("You lose by time. ");
        }

        resultMsg.append("Final score: ").append(yourScore).append(" - ").append(opponentScore);

        log(resultMsg.toString());
        JOptionPane.showMessageDialog(this, resultMsg.toString());

        // Reset game state
        roundActive = false;
        countdownTimer.stop();
        statusLabel.setText("Game ended");
    }

    private void updateTimer() {
        secondsLeft--;
        timerLabel.setText("Time: " + secondsLeft + "s");

        if (secondsLeft <= 0) {
            countdownTimer.stop();
            timerLabel.setText("Time's up!");
            // Server will handle timeout
        }
    }

    private void log(String message) {
        gameLogArea.append(message + "\n");
        gameLogArea.setCaretPosition(gameLogArea.getDocument().getLength());
        System.out.println("LOG: " + message);
    }
}