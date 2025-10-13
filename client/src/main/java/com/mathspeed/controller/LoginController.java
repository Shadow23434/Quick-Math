package com.mathspeed.controller;

import com.mathspeed.network.NetworkManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class LoginController {
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private CheckBox rememberMeCheckBox;
    @FXML private Label errorLabel;
    @FXML private Label serverStatusLabel;
    @FXML private Button loginButton;
    @FXML private Button registerButton;
    @FXML private ProgressIndicator loadingIndicator;

    private NetworkManager networkManager;

    @FXML
    public void initialize() {
        logger.info("LoginController initialized");

        // Initialize network manager
        networkManager = NetworkManager.getInstance();

        // Check server connection
        checkServerConnection();

        // Add enter key listener for password field
        passwordField.setOnAction(event -> handleLogin());

        // Load saved credentials if remember me was checked
        loadSavedCredentials();
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        // Validate input
        if (username.isEmpty() || password.isEmpty()) {
            showError("Vui lòng nhập đầy đủ thông tin!");
            return;
        }

        // Disable buttons during login
        setLoading(true);
        hideError();

        // TODO: Connect to server and authenticate
        // For now, simulate login
        new Thread(() -> {
            try {
                Thread.sleep(1500); // Simulate network delay

                Platform.runLater(() -> {
                    // Simulate successful login
                    if (username.equals("player1") && password.equals("password123")) {
                        logger.info("Login successful for user: {}", username);

                        // Save credentials if remember me is checked
                        if (rememberMeCheckBox.isSelected()) {
                            saveCredentials(username, password);
                        }

                        // Navigate to lobby
                        navigateToLobby(username);
                    } else {
                        showError("Tên đăng nhập hoặc mật khẩu không đúng!");
                        setLoading(false);
                    }
                });
            } catch (InterruptedException e) {
                logger.error("Login interrupted", e);
                Platform.runLater(() -> {
                    showError("Lỗi kết nối đến server!");
                    setLoading(false);
                });
            }
        }).start();
    }

    @FXML
    private void handleRegister() {
        logger.info("Register button clicked");

        // TODO: Open register dialog or navigate to register screen
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Đăng ký");
        alert.setHeaderText("Chức năng đăng ký");
        alert.setContentText("Tính năng đăng ký sẽ được cài đặt sau!");
        alert.showAndWait();
    }

    private void checkServerConnection() {
        // TODO: Actually check server connection
        // For now, simulate connection
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                Platform.runLater(() -> {
                    serverStatusLabel.setText("Đã kết nối");
                    serverStatusLabel.getStyleClass().remove("status-connecting");
                    serverStatusLabel.getStyleClass().add("status-connected");
                });
            } catch (InterruptedException e) {
                logger.error("Connection check interrupted", e);
            }
        }).start();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);

        // Shake animation effect
        shakeNode(errorLabel);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void setLoading(boolean loading) {
        loginButton.setDisable(loading);
        registerButton.setDisable(loading);
        usernameField.setDisable(loading);
        passwordField.setDisable(loading);
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
    }

    private void shakeNode(javafx.scene.Node node) {
        javafx.animation.TranslateTransition transition =
                new javafx.animation.TranslateTransition(javafx.util.Duration.millis(100), node);
        transition.setFromX(0);
        transition.setByX(10);
        transition.setCycleCount(4);
        transition.setAutoReverse(true);
        transition.play();
    }

    private void saveCredentials(String username, String password) {
        // TODO: Save to preferences file
        logger.info("Saving credentials for user: {}", username);
    }

    private void loadSavedCredentials() {
        // TODO: Load from preferences file
        // For testing, you can uncomment these:
        // usernameField.setText("player1");
        // passwordField.setText("password123");
    }

    private void navigateToLobby(String username) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/lobby.fxml"));
            Parent root = loader.load();

            // Get the lobby controller and pass username
            LobbyController lobbyController = loader.getController();
            lobbyController.setUsername(username);

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/lobby.css").toExternalForm());

            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Math Speed Game - Lobby");

            logger.info("Navigated to lobby for user: {}", username);
        } catch (IOException e) {
            logger.error("Failed to load lobby screen", e);
            showError("Không thể tải màn hình lobby!");
            setLoading(false);
        }
    }
}