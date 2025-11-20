package com.mathspeed.controller;

import com.mathspeed.client.SceneManager;
import com.mathspeed.client.WindowSizing;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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
    @FXML private Hyperlink registerButton;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private StackPane rootPane;
    @FXML private StackPane loadingOverlay;
    private Label loadingOverlayMessage;

    private boolean isPasswordVisible = false;
    private FontIcon eyeIcon;
    private FontIcon eyeSlashIcon;
    private FontIcon checkIcon;

    @FXML
    public void initialize() {
        loadSavedCredentials();
        setupIcons();
        setupPasswordField();
        setupEmailField();
        setupEnterKeyLogin();
        setupCustomCheckboxIcon();

        // Ensure login uses default compact sizing by default
        WindowSizing.applyToNode(usernameField, false);

        // If overlay was included, locate its internal message label for later updates
        try {
            if (loadingOverlay != null) {
                Object node = loadingOverlay.lookup("#loadingMessage");
                if (node instanceof Label) loadingOverlayMessage = (Label) node;
            }
        } catch (Exception ignored) {}

        if (usernameField != null) {
            usernameField.sceneProperty().addListener((obs, oldS, newS) -> {
                if (newS != null) {
                    newS.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
                        if (ev.isControlDown() && ev.getCode() == javafx.scene.input.KeyCode.D) {
                            WindowSizing.toggleGlobalAndApply(usernameField);
                            System.out.println("Window mode toggled. Now desktop=" + WindowSizing.isGlobalDesktopMode());
                            ev.consume();
                        }
                    });
                }
            });
        }
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
        }
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
        passwordHiddenField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> updateClearPasswordButtonVisibility());

        passwordVisibleField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> updateClearPasswordButtonVisibility());
    }

    private void setupEmailField() {
        // Text change listener
        usernameField.textProperty().addListener((obs, oldVal, newVal) -> updateClearEmailButtonVisibility());

        // Focus handling
        usernameField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> updateClearEmailButtonVisibility());
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

        // Show loading state
        if (loadingIndicator != null) {
            loadingIndicator.setVisible(true);
            loadingIndicator.setManaged(true);
        }
        if (loadingOverlayMessage != null) {
            // if overlay label exists, ensure its visible text is updated
            Platform.runLater(() -> {
                loadingOverlayMessage.setVisible(true);
                loadingOverlayMessage.setManaged(true);
                loadingOverlayMessage.setText("Loading...");
            });
        }
        if (loginButton != null) loginButton.setDisable(true);

        // Also show the full-screen overlay if available
        showLoadingOverlay("Loading...");

        // Ensure any previous shell/session is fully reset (especially after logout)
        SceneManager.getInstance().logout();

        Stage stage = (Stage) loginButton.getScene().getWindow();
        // Call loadShellAsync with success and error handlers (method expects 4 args)
        SceneManager.getInstance().loadShellAsync(stage, username,
                // onSuccess: hide loader and leave UI to shell
                () -> {
                    try {
                        // Hide both small indicator and overlay on success
                        if (loadingIndicator != null) { loadingIndicator.setVisible(false); loadingIndicator.setManaged(false); }
                        if (loginButton != null) loginButton.setDisable(false);
                        hideLoadingOverlay();
                    } catch (Exception ignored) {}
                },
                // onError: log and show error message to user
                (ex) -> {
                    logger.error("Failed to load shell asynchronously", ex);
                    Platform.runLater(() -> {
                        showError("Failed to start application. Please try again.");
                        try {
                            if (loadingIndicator != null) { loadingIndicator.setVisible(false); loadingIndicator.setManaged(false); }
                            if (loginButton != null) loginButton.setDisable(false);
                            hideLoadingOverlay();
                        } catch (Exception ignored) {}
                    });
                }
        );
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    @FXML
    private void handleRegister() {
        try {
            Stage stage = (Stage) registerButton.getScene().getWindow();
            SceneManager.showRegister(stage);
        } catch (Exception e) {
            logger.error("Error navigating to register screen", e);
            showError("Error navigating to register screen");
        }
    }

    private void saveCredentials(String username, String password) {
        int pwLen = password == null ? 0 : password.length();
        logger.info("Saving credentials for user: {} (password length={})", username, pwLen);
    }

    private void loadSavedCredentials() {
    }

    private void showLoadingOverlay(String message) {
        try {
            if (loadingOverlay != null) {
                Platform.runLater(() -> {
                    loadingOverlay.setVisible(true);
                    loadingOverlay.setManaged(true);
                    loadingOverlay.toFront();
                    if (loadingOverlayMessage != null) loadingOverlayMessage.setText(message);
                    if (loginButton != null) loginButton.setDisable(true);
                });
            }
        } catch (Exception ignored) {}
    }

    private void hideLoadingOverlay() {
        try {
            if (loadingOverlay != null) {
                Platform.runLater(() -> {
                    loadingOverlay.setVisible(false);
                    loadingOverlay.setManaged(false);
                    if (loginButton != null) loginButton.setDisable(false);
                });
            }
        } catch (Exception ignored) {}
    }
}
