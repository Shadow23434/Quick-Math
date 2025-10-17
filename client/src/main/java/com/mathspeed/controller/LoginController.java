package com.mathspeed.controller;

import com.mathspeed.util.ReloadManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class LoginController {
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordHiddenField;
    @FXML private TextField passwordVisibleField;
    @FXML private Button toggleShowPassword;
    @FXML private Button clearPasswordButton;
    @FXML private Button clearEmailButton;
    @FXML private Label passwordStrengthLabel;
    @FXML private Label passwordHintLabel;
    @FXML private CheckBox rememberMeCheckBox;
    @FXML private Label checkIconLabel;
    @FXML private Hyperlink forgotPasswordLink;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;
    @FXML private Button registerButton;
    @FXML private Label serverStatusLabel;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Button reloadButton;

    private boolean isPasswordVisible = false;
    private FontIcon eyeIcon;
    private FontIcon eyeSlashIcon;
    private FontIcon checkIcon;

    @FXML
    public void initialize() {
        logger.info("LoginController initialized");

        // Initialize network manager
        // networkManager = NetworkManager.getInstance();

        // Load saved credentials if remember me was checked
        loadSavedCredentials();

        setupIcons();
        setupPasswordField();
        setupEmailField();
        setupEnterKeyLogin();
        setupReloadShortcut();
        setupCustomCheckboxIcon();
    }

    private void setupIcons() {
        // Setup FontAwesome icons for password toggle
        eyeIcon = new FontIcon(FontAwesomeSolid.EYE);
        eyeIcon.setIconSize(18);
        eyeIcon.getStyleClass().add("icon-view");

        eyeSlashIcon = new FontIcon(FontAwesomeSolid.EYE_SLASH);
        eyeSlashIcon.setIconSize(18);
        eyeSlashIcon.getStyleClass().add("icon-view");

        // Set initial icon
        if (toggleShowPassword != null) {
            toggleShowPassword.setGraphic(eyeIcon);
        }

        // Setup clear email icon
        if (clearEmailButton != null) {
            FontIcon clearIcon = new FontIcon(FontAwesomeSolid.TIMES);
            clearIcon.setIconSize(16);
            clearIcon.getStyleClass().add("icon-view");
            clearEmailButton.setGraphic(clearIcon);
        }

        // Setup clear password icon
        if (clearPasswordButton != null) {
            FontIcon clearIcon = new FontIcon(FontAwesomeSolid.TIMES);
            clearIcon.setIconSize(16);
            clearIcon.getStyleClass().add("icon-view");
            clearPasswordButton.setGraphic(clearIcon);
        }

        // Setup reload icon
        if (reloadButton != null) {
            FontIcon reloadIcon = new FontIcon(FontAwesomeSolid.SYNC_ALT);
            reloadIcon.setIconSize(20);
            reloadIcon.getStyleClass().add("icon-view");
            reloadButton.setGraphic(reloadIcon);
            reloadButton.setText("");
        }
    }

    private void setupReloadShortcut() {
        if (reloadButton != null && reloadButton.getScene() != null) {
            reloadButton.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.F5) {
                    handleReload();
                    event.consume();
                }
            });
        }
    }

    private void setupCustomCheckboxIcon() {
        checkIcon = new FontIcon(FontAwesomeSolid.CHECK);
        checkIcon.setIconSize(10);
        checkIcon.setIconColor(javafx.scene.paint.Color.WHITE);

        rememberMeCheckBox.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                checkIconLabel.setGraphic(checkIcon);
                checkIconLabel.setVisible(true);
            } else {
                checkIconLabel.setGraphic(null);
                checkIconLabel.setVisible(false);
            }
        });

        if (rememberMeCheckBox.isSelected()) {
            checkIconLabel.setGraphic(checkIcon);
            checkIconLabel.setVisible(true);
            logger.info("Initial state: checkbox selected, icon added");
        }
    }

    @FXML
    private void handleReload() {
        ReloadManager.reloadCurrentScene();
    }

    private void setupPasswordField() {
        // Sync password hidden field and visible field
        passwordHiddenField.textProperty().addListener((obs, oldVal, newVal) -> {
            passwordVisibleField.setText(newVal);
            updateClearPasswordButtonVisibility();
            updatePasswordFieldStyle(newVal);
            checkPasswordStrength(newVal);
        });

        passwordVisibleField.textProperty().addListener((obs, oldVal, newVal) -> {
            passwordHiddenField.setText(newVal);
            updatePasswordFieldStyle(newVal);
        });

        // Focus handling
        passwordHiddenField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            updateClearPasswordButtonVisibility();
        });

        passwordVisibleField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            updateClearPasswordButtonVisibility();
        });
    }

    private void setupEmailField() {
        // Text change listener
        usernameField.textProperty().addListener((obs, oldVal, newVal) -> {
            updateClearEmailButtonVisibility();
        });

        // Focus handling
        usernameField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            updateClearEmailButtonVisibility();
        });
    }

    private void setupEnterKeyLogin() {
        usernameField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                passwordHiddenField.requestFocus();
            }
        });

        passwordHiddenField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleLogin();
            }
        });

        passwordVisibleField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleLogin();
            }
        });
    }

    @FXML
    private void handleTogglePassword() {
        isPasswordVisible = !isPasswordVisible;

        if (isPasswordVisible) {
            // Show password
            passwordHiddenField.setVisible(false);
            passwordHiddenField.setManaged(false);
            passwordVisibleField.setVisible(true);
            passwordVisibleField.setManaged(true);
            toggleShowPassword.setGraphic(eyeSlashIcon);
            passwordVisibleField.requestFocus();
            passwordVisibleField.positionCaret(passwordVisibleField.getText().length());
        } else {
            // Hide password
            passwordVisibleField.setVisible(false);
            passwordVisibleField.setManaged(false);
            passwordHiddenField.setVisible(true);
            passwordHiddenField.setManaged(true);
            toggleShowPassword.setGraphic(eyeIcon);
            passwordHiddenField.requestFocus();
            passwordHiddenField.positionCaret(passwordHiddenField.getText().length());
        }
    }

    @FXML
    private void handleClearPassword() {
        passwordHiddenField.clear();
        passwordVisibleField.clear();
        updateClearPasswordButtonVisibility();

        // Focus lại vào field
        if (isPasswordVisible) {
            passwordVisibleField.requestFocus();
        } else {
            passwordHiddenField.requestFocus();
        }
    }

    @FXML
    private void handleClearEmail() {
        usernameField.clear();
        usernameField.requestFocus();
    }

    private void updateClearEmailButtonVisibility() {
        if (clearEmailButton != null) {
            boolean hasText = !usernameField.getText().isEmpty();
            boolean isFocused = usernameField.isFocused();

            clearEmailButton.setVisible(hasText && isFocused);
            clearEmailButton.setManaged(hasText && isFocused);
        }
    }

    private void updateClearPasswordButtonVisibility() {
        if (clearPasswordButton != null) {
            boolean hasText = !passwordHiddenField.getText().isEmpty();
            boolean isFocused = passwordHiddenField.isFocused() || passwordVisibleField.isFocused();

            clearPasswordButton.setVisible(hasText && isFocused);
            clearPasswordButton.setManaged(hasText && isFocused);
        }
    }

    private void updatePasswordFieldStyle(String text) {
        if (text != null && !text.isEmpty()) {
            if (!passwordHiddenField.getStyleClass().contains("has-text")) {
                passwordHiddenField.getStyleClass().add("has-text");
            }
            if (!passwordVisibleField.getStyleClass().contains("has-text")) {
                passwordVisibleField.getStyleClass().add("has-text");
            }
        } else {
            passwordHiddenField.getStyleClass().remove("has-text");
            passwordVisibleField.getStyleClass().remove("has-text");
        }
    }

    private void checkPasswordStrength(String password) {
        if (password.isEmpty()) {
            passwordStrengthLabel.setVisible(false);
            passwordStrengthLabel.setManaged(false);
            passwordHintLabel.setVisible(false);
            passwordHintLabel.setManaged(false);
            return;
        }

        int strength = calculatePasswordStrength(password);

        passwordStrengthLabel.setVisible(true);
        passwordStrengthLabel.setManaged(true);

        // Remove all old style classes
        passwordStrengthLabel.getStyleClass().removeAll(
                "password-strength-weak",
                "password-strength-medium",
                "password-strength-strong"
        );

        if (strength < 3) {
            passwordStrengthLabel.setText("Weak");
            passwordStrengthLabel.getStyleClass().add("password-strength-weak");
        } else if (strength < 5) {
            passwordStrengthLabel.setText("Medium");
            passwordStrengthLabel.getStyleClass().add("password-strength-medium");
        } else {
            passwordStrengthLabel.setText("Strong");
            passwordStrengthLabel.getStyleClass().add("password-strength-strong");
        }
    }

    private int calculatePasswordStrength(String password) {
        int strength = 0;

        if (password.length() >= 8) strength++;
        if (password.length() >= 12) strength++;
        if (password.matches(".*[a-z].*")) strength++;
        if (password.matches(".*[A-Z].*")) strength++;
        if (password.matches(".*\\d.*")) strength++;
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) strength++;

        return strength;
    }

    @FXML
    private void handleForgotPassword() {
        // Implement forgot password logic
        System.out.println("Forgot password clicked");
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordHiddenField.getText();

        // Clear previous errors
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        // Validation
        if (username.isEmpty()) {
            showError("Please enter your email");
            usernameField.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            showError("Please enter your password");
            passwordHiddenField.requestFocus();
            return;
        }

        // Show loading
        setLoading(true);

        // Login logic
        System.out.println("Login attempt: " + username);
        navigateToLobby(username);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void setLoading(boolean loading) {
        loginButton.setDisable(loading);
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
    }

    @FXML
    private void handleRegister() {
        System.out.println("Register button clicked");
        // TODO: Open register dialog or navigate to register screen
    }

    private void saveCredentials(String username, String password) {
        // TODO: Save to preferences file
        // Avoid logging the raw password; log only its length for debugging
        int pwLen = password == null ? 0 : password.length();
        logger.info("Saving credentials for user: {} (password length={})", username, pwLen);
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
            java.net.URL lobbyCss = getClass().getResource("/css/lobby.css");
            if (lobbyCss != null) {
                scene.getStylesheets().add(lobbyCss.toExternalForm());
            } else {
                logger.warn("lobby.css not found on classpath");
            }

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
