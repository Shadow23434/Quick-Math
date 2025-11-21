package com.mathspeed.controller;

import com.mathspeed.client.SceneManager;
import com.mathspeed.util.WindowSizing;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class RegisterController {
    private static final Logger logger = LoggerFactory.getLogger(RegisterController.class);

    @FXML private TextField usernameField;
    @FXML private TextField displayNameField;
    @FXML private ComboBox<String> genderComboBox;
    @FXML private ComboBox<String> countryComboBox;
    @FXML private PasswordField passwordHiddenField;
    @FXML private TextField passwordVisibleField;
    @FXML private PasswordField confirmPasswordHiddenField;
    @FXML private TextField confirmPasswordVisibleField;
    @FXML private Button toggleShowPassword;
    @FXML private Button toggleShowConfirmPassword;
    @FXML private Button clearPasswordButton;
    @FXML private Button clearConfirmPasswordButton;
    @FXML private Button clearUsernameButton;
    @FXML private Button clearDisplayNameButton;
    @FXML private Label passwordStrengthLabel;
    @FXML private Label passwordHintLabel;
    @FXML private Label confirmPasswordHintLabel;
    @FXML private CheckBox termsCheckBox;
    @FXML private Label checkIconLabel;
    @FXML private Label errorLabel;
    @FXML private Button registerButton;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Button reloadButton;
    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;
    private FontIcon eyeIcon;
    private FontIcon eyeSlashIcon;
    private FontIcon checkIcon;

    @FXML
    public void initialize() {
        setupIcons();
        setupUsernameField();
        setupDisplayNameField();
        setupGenderComboBox();
        setupCountryComboBox();
        setupPasswordField();
        setupConfirmPasswordField();
        setupEnterKeyRegistration();
        setupCustomCheckboxIcon();

        // Ensure register screen uses default compact sizing by default
        WindowSizing.applyToNode(usernameField, false);

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

        // Set initial icons for password fields
        if (toggleShowPassword != null) {
            FontIcon eyeIconCopy = new FontIcon(FontAwesomeSolid.EYE);
            eyeIconCopy.setIconSize(18);
            eyeIconCopy.getStyleClass().add("icon-view");
            toggleShowPassword.setGraphic(eyeIconCopy);
        }

        if (toggleShowConfirmPassword != null) {
            FontIcon eyeIconCopy = new FontIcon(FontAwesomeSolid.EYE);
            eyeIconCopy.setIconSize(18);
            eyeIconCopy.getStyleClass().add("icon-view");
            toggleShowConfirmPassword.setGraphic(eyeIconCopy);
        }

        // Setup clear username icon
        if (clearUsernameButton != null) {
            FontIcon clearIcon = new FontIcon(FontAwesomeSolid.TIMES);
            clearIcon.setIconSize(16);
            clearIcon.getStyleClass().add("icon-view");
            clearUsernameButton.setGraphic(clearIcon);
        }

        // Setup clear display name icon
        if (clearDisplayNameButton != null) {
            FontIcon clearIcon = new FontIcon(FontAwesomeSolid.TIMES);
            clearIcon.setIconSize(16);
            clearIcon.getStyleClass().add("icon-view");
            clearDisplayNameButton.setGraphic(clearIcon);
        }

        // Setup clear password icon
        if (clearPasswordButton != null) {
            FontIcon clearIcon = new FontIcon(FontAwesomeSolid.TIMES);
            clearIcon.setIconSize(16);
            clearIcon.getStyleClass().add("icon-view");
            clearPasswordButton.setGraphic(clearIcon);
        }

        // Setup clear confirm password icon
        if (clearConfirmPasswordButton != null) {
            FontIcon clearIcon = new FontIcon(FontAwesomeSolid.TIMES);
            clearIcon.setIconSize(16);
            clearIcon.getStyleClass().add("icon-view");
            clearConfirmPasswordButton.setGraphic(clearIcon);
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

    private void setupCustomCheckboxIcon() {
        checkIcon = new FontIcon(FontAwesomeSolid.CHECK);
        checkIcon.setIconSize(10);
        checkIcon.setIconColor(javafx.scene.paint.Color.WHITE);

        termsCheckBox.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                checkIconLabel.setGraphic(checkIcon);
                checkIconLabel.setVisible(true);
            } else {
                checkIconLabel.setGraphic(null);
                checkIconLabel.setVisible(false);
            }
        });

        if (termsCheckBox.isSelected()) {
            checkIconLabel.setGraphic(checkIcon);
            checkIconLabel.setVisible(true);
        }
    }

    private void setupUsernameField() {
        usernameField.textProperty().addListener((obs, oldVal, newVal) -> {
            updateClearUsernameButtonVisibility();
        });

        usernameField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            updateClearUsernameButtonVisibility();
        });
    }

    private void setupDisplayNameField() {
        displayNameField.textProperty().addListener((obs, oldVal, newVal) -> {
            updateClearDisplayNameButtonVisibility();
        });

        displayNameField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            updateClearDisplayNameButtonVisibility();
        });
    }

    private void setupGenderComboBox() {
        if (genderComboBox != null) {
            genderComboBox.getItems().addAll(
                    "Male", "Female", "Other"
            );
            genderComboBox.setEditable(false);
            // Optional: keep no selection by default
            genderComboBox.getSelectionModel().clearSelection();
        }
    }

    private void setupCountryComboBox() {
        if (countryComboBox != null) {
            // Add list of countries
            countryComboBox.getItems().addAll(
                "Afghanistan", "Albania", "Algeria", "Andorra", "Angola",
                "Argentina", "Armenia", "Australia", "Austria", "Azerbaijan",
                "Bahamas", "Bahrain", "Bangladesh", "Barbados", "Belarus",
                "Belgium", "Belize", "Benin", "Bhutan", "Bolivia",
                "Bosnia and Herzegovina", "Botswana", "Brazil", "Brunei", "Bulgaria",
                "Burkina Faso", "Burundi", "Cambodia", "Cameroon", "Canada",
                "Cape Verde", "Central African Republic", "Chad", "Chile", "China",
                "Colombia", "Comoros", "Congo", "Costa Rica", "Croatia",
                "Cuba", "Cyprus", "Czech Republic", "Denmark", "Djibouti",
                "Dominica", "Dominican Republic", "East Timor", "Ecuador", "Egypt",
                "El Salvador", "Equatorial Guinea", "Eritrea", "Estonia", "Ethiopia",
                "Fiji", "Finland", "France", "Gabon", "Gambia",
                "Georgia", "Germany", "Ghana", "Greece", "Grenada",
                "Guatemala", "Guinea", "Guinea-Bissau", "Guyana", "Haiti",
                "Honduras", "Hungary", "Iceland", "India", "Indonesia",
                "Iran", "Iraq", "Ireland", "Israel", "Italy",
                "Jamaica", "Japan", "Jordan", "Kazakhstan", "Kenya",
                "Kiribati", "North Korea", "South Korea", "Kuwait", "Kyrgyzstan",
                "Laos", "Latvia", "Lebanon", "Lesotho", "Liberia",
                "Libya", "Liechtenstein", "Lithuania", "Luxembourg", "Macedonia",
                "Madagascar", "Malawi", "Malaysia", "Maldives", "Mali",
                "Malta", "Marshall Islands", "Mauritania", "Mauritius", "Mexico",
                "Micronesia", "Moldova", "Monaco", "Mongolia", "Montenegro",
                "Morocco", "Mozambique", "Myanmar", "Namibia", "Nauru",
                "Nepal", "Netherlands", "New Zealand", "Nicaragua", "Niger",
                "Nigeria", "Norway", "Oman", "Pakistan", "Palau",
                "Panama", "Papua New Guinea", "Paraguay", "Peru", "Philippines",
                "Poland", "Portugal", "Qatar", "Romania", "Russia",
                "Rwanda", "Saint Kitts and Nevis", "Saint Lucia", "Saint Vincent and the Grenadines", "Samoa",
                "San Marino", "Sao Tome and Principe", "Saudi Arabia", "Senegal", "Serbia",
                "Seychelles", "Sierra Leone", "Singapore", "Slovakia", "Slovenia",
                "Solomon Islands", "Somalia", "South Africa", "South Sudan", "Spain",
                "Sri Lanka", "Sudan", "Suriname", "Swaziland", "Sweden",
                "Switzerland", "Syria", "Taiwan", "Tajikistan", "Tanzania",
                "Thailand", "Togo", "Tonga", "Trinidad and Tobago", "Tunisia",
                "Turkey", "Turkmenistan", "Tuvalu", "Uganda", "Ukraine",
                "United Arab Emirates", "United Kingdom", "United States", "Uruguay", "Uzbekistan",
                "Vanuatu", "Vatican City", "Venezuela", "Vietnam", "Yemen",
                "Zambia", "Zimbabwe"
            );

            // Make it searchable/filterable
            countryComboBox.setEditable(false);
        }
    }

    private void setupPasswordField() {
        passwordHiddenField.textProperty().addListener((obs, oldVal, newVal) -> {
            passwordVisibleField.setText(newVal);
            updateClearPasswordButtonVisibility();
            updatePasswordFieldStyle(passwordHiddenField, passwordVisibleField, newVal);
            checkPasswordStrength(newVal);
            checkPasswordsMatch();
        });

        passwordVisibleField.textProperty().addListener((obs, oldVal, newVal) -> {
            passwordHiddenField.setText(newVal);
            updatePasswordFieldStyle(passwordHiddenField, passwordVisibleField, newVal);
        });

        passwordHiddenField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            updateClearPasswordButtonVisibility();
        });

        passwordVisibleField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            updateClearPasswordButtonVisibility();
        });
    }

    private void setupConfirmPasswordField() {
        confirmPasswordHiddenField.textProperty().addListener((obs, oldVal, newVal) -> {
            confirmPasswordVisibleField.setText(newVal);
            updateClearConfirmPasswordButtonVisibility();
            updatePasswordFieldStyle(confirmPasswordHiddenField, confirmPasswordVisibleField, newVal);
            checkPasswordsMatch();
        });

        confirmPasswordVisibleField.textProperty().addListener((obs, oldVal, newVal) -> {
            confirmPasswordHiddenField.setText(newVal);
            updatePasswordFieldStyle(confirmPasswordHiddenField, confirmPasswordVisibleField, newVal);
        });

        confirmPasswordHiddenField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            updateClearConfirmPasswordButtonVisibility();
        });

        confirmPasswordVisibleField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            updateClearConfirmPasswordButtonVisibility();
        });
    }

    private void setupEnterKeyRegistration() {
        usernameField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                displayNameField.requestFocus();
            }
        });

        displayNameField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                passwordHiddenField.requestFocus();
            }
        });

        passwordHiddenField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                confirmPasswordHiddenField.requestFocus();
            }
        });

        passwordVisibleField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                if (isConfirmPasswordVisible) {
                    confirmPasswordVisibleField.requestFocus();
                } else {
                    confirmPasswordHiddenField.requestFocus();
                }
            }
        });

        confirmPasswordHiddenField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleRegister();
            }
        });

        confirmPasswordVisibleField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleRegister();
            }
        });
    }

    @FXML
    private void handleTogglePassword() {
        isPasswordVisible = !isPasswordVisible;

        FontIcon newEyeIcon = new FontIcon(FontAwesomeSolid.EYE);
        newEyeIcon.setIconSize(18);
        newEyeIcon.getStyleClass().add("icon-view");

        FontIcon newEyeSlashIcon = new FontIcon(FontAwesomeSolid.EYE_SLASH);
        newEyeSlashIcon.setIconSize(18);
        newEyeSlashIcon.getStyleClass().add("icon-view");

        if (isPasswordVisible) {
            passwordHiddenField.setVisible(false);
            passwordHiddenField.setManaged(false);
            passwordVisibleField.setVisible(true);
            passwordVisibleField.setManaged(true);
            toggleShowPassword.setGraphic(newEyeSlashIcon);
            passwordVisibleField.requestFocus();
            passwordVisibleField.positionCaret(passwordVisibleField.getText().length());
        } else {
            passwordVisibleField.setVisible(false);
            passwordVisibleField.setManaged(false);
            passwordHiddenField.setVisible(true);
            passwordHiddenField.setManaged(true);
            toggleShowPassword.setGraphic(newEyeIcon);
            passwordHiddenField.requestFocus();
            passwordHiddenField.positionCaret(passwordHiddenField.getText().length());
        }
    }

    @FXML
    private void handleToggleConfirmPassword() {
        isConfirmPasswordVisible = !isConfirmPasswordVisible;

        FontIcon newEyeIcon = new FontIcon(FontAwesomeSolid.EYE);
        newEyeIcon.setIconSize(18);
        newEyeIcon.getStyleClass().add("icon-view");

        FontIcon newEyeSlashIcon = new FontIcon(FontAwesomeSolid.EYE_SLASH);
        newEyeSlashIcon.setIconSize(18);
        newEyeSlashIcon.getStyleClass().add("icon-view");

        if (isConfirmPasswordVisible) {
            confirmPasswordHiddenField.setVisible(false);
            confirmPasswordHiddenField.setManaged(false);
            confirmPasswordVisibleField.setVisible(true);
            confirmPasswordVisibleField.setManaged(true);
            toggleShowConfirmPassword.setGraphic(newEyeSlashIcon);
            confirmPasswordVisibleField.requestFocus();
            confirmPasswordVisibleField.positionCaret(confirmPasswordVisibleField.getText().length());
        } else {
            confirmPasswordVisibleField.setVisible(false);
            confirmPasswordVisibleField.setManaged(false);
            confirmPasswordHiddenField.setVisible(true);
            confirmPasswordHiddenField.setManaged(true);
            toggleShowConfirmPassword.setGraphic(newEyeIcon);
            confirmPasswordHiddenField.requestFocus();
            confirmPasswordHiddenField.positionCaret(confirmPasswordHiddenField.getText().length());
        }
    }

    @FXML
    private void handleClearUsername() {
        usernameField.clear();
        usernameField.requestFocus();
    }

    @FXML
    private void handleClearDisplayName() {
        displayNameField.clear();
        displayNameField.requestFocus();
    }

    @FXML
    private void handleClearPassword() {
        passwordHiddenField.clear();
        passwordVisibleField.clear();
        updateClearPasswordButtonVisibility();

        if (isPasswordVisible) {
            passwordVisibleField.requestFocus();
        } else {
            passwordHiddenField.requestFocus();
        }
    }

    @FXML
    private void handleClearConfirmPassword() {
        confirmPasswordHiddenField.clear();
        confirmPasswordVisibleField.clear();
        updateClearConfirmPasswordButtonVisibility();

        if (isConfirmPasswordVisible) {
            confirmPasswordVisibleField.requestFocus();
        } else {
            confirmPasswordHiddenField.requestFocus();
        }
    }

    private void updateClearUsernameButtonVisibility() {
        if (clearUsernameButton != null) {
            boolean hasText = !usernameField.getText().isEmpty();
            boolean isFocused = usernameField.isFocused();
            clearUsernameButton.setVisible(hasText && isFocused);
            clearUsernameButton.setManaged(hasText && isFocused);
        }
    }

    private void updateClearDisplayNameButtonVisibility() {
        if (clearDisplayNameButton != null) {
            boolean hasText = !displayNameField.getText().isEmpty();
            boolean isFocused = displayNameField.isFocused();
            clearDisplayNameButton.setVisible(hasText && isFocused);
            clearDisplayNameButton.setManaged(hasText && isFocused);
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

    private void updateClearConfirmPasswordButtonVisibility() {
        if (clearConfirmPasswordButton != null) {
            boolean hasText = !confirmPasswordHiddenField.getText().isEmpty();
            boolean isFocused = confirmPasswordHiddenField.isFocused() || confirmPasswordVisibleField.isFocused();
            clearConfirmPasswordButton.setVisible(hasText && isFocused);
            clearConfirmPasswordButton.setManaged(hasText && isFocused);
        }
    }

    private void updatePasswordFieldStyle(PasswordField hiddenField, TextField visibleField, String text) {
        if (text != null && !text.isEmpty()) {
            if (!hiddenField.getStyleClass().contains("has-text")) {
                hiddenField.getStyleClass().add("has-text");
            }
            if (!visibleField.getStyleClass().contains("has-text")) {
                visibleField.getStyleClass().add("has-text");
            }
        } else {
            hiddenField.getStyleClass().remove("has-text");
            visibleField.getStyleClass().remove("has-text");
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

        passwordStrengthLabel.getStyleClass().removeAll(
                "password-strength-weak",
                "password-strength-medium",
                "password-strength-strong"
        );

        if (strength < 3) {
            passwordStrengthLabel.setText("Weak");
            passwordStrengthLabel.getStyleClass().add("password-strength-weak");
            showPasswordHint("Use at least 8 characters with uppercase, lowercase, and numbers");
        } else if (strength < 5) {
            passwordStrengthLabel.setText("Medium");
            passwordStrengthLabel.getStyleClass().add("password-strength-medium");
            showPasswordHint("Add special characters for a stronger password");
        } else {
            passwordStrengthLabel.setText("Strong");
            passwordStrengthLabel.getStyleClass().add("password-strength-strong");
            passwordHintLabel.setVisible(false);
            passwordHintLabel.setManaged(false);
        }
    }

    private void showPasswordHint(String hint) {
        if (passwordHintLabel != null) {
            passwordHintLabel.setText(hint);
            passwordHintLabel.setVisible(true);
            passwordHintLabel.setManaged(true);
            passwordHintLabel.getStyleClass().remove("password-hint-error");
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

    private void checkPasswordsMatch() {
        String password = passwordHiddenField.getText();
        String confirmPassword = confirmPasswordHiddenField.getText();

        if (confirmPassword.isEmpty()) {
            confirmPasswordHintLabel.setVisible(false);
            confirmPasswordHintLabel.setManaged(false);
            return;
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordHintLabel.setText("Passwords do not match");
            confirmPasswordHintLabel.setVisible(true);
            confirmPasswordHintLabel.setManaged(true);
            if (!confirmPasswordHintLabel.getStyleClass().contains("password-hint-error")) {
                confirmPasswordHintLabel.getStyleClass().add("password-hint-error");
            }
        } else {
            confirmPasswordHintLabel.setVisible(false);
            confirmPasswordHintLabel.setManaged(false);
        }
    }

    @FXML
    private void handleRegister() {
        String username = usernameField.getText().trim();
        String displayName = displayNameField.getText().trim();
        String password = passwordHiddenField.getText();
        String confirmPassword = confirmPasswordHiddenField.getText();

        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        if (username.isEmpty()) {
            showError("Please enter a username");
            usernameField.requestFocus();
            return;
        }

        if (username.length() < 3) {
            showError("Username must be at least 3 characters long");
            usernameField.requestFocus();
            return;
        }

        if (displayName.isEmpty()) {
            showError("Please enter your display name");
            displayNameField.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            showError("Please enter a password");
            passwordHiddenField.requestFocus();
            return;
        }

        if (password.length() < 8) {
            showError("Password must be at least 8 characters long");
            passwordHiddenField.requestFocus();
            return;
        }

        if (confirmPassword.isEmpty()) {
            showError("Please confirm your password");
            confirmPasswordHiddenField.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match");
            confirmPasswordHiddenField.requestFocus();
            return;
        }

        if (!termsCheckBox.isSelected()) {
            showError("Please agree to the Terms & Conditions");
            return;
        }

        // Map selected country name to ISO code (store country as code)
        String selectedCountryName = countryComboBox != null ? countryComboBox.getSelectionModel().getSelectedItem() : null;
        String countryCode = null;
        if (selectedCountryName != null && !selectedCountryName.isEmpty()) {
            countryCode = com.mathspeed.util.Countries.getCodeForName(selectedCountryName);
        }

        // Selected gender (optional)
        String selectedGender = genderComboBox != null ? genderComboBox.getSelectionModel().getSelectedItem() : null;

        setLoading(true);
        performRegistration(username, displayName, password, countryCode, selectedGender);
    }

    private void performRegistration(String username, String displayName, String password, String countryCode, String gender) {
        logger.info("Registering user: {} displayName={} countryCode={} gender={}", username, displayName, countryCode, gender);
        navigateToLogin();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void setLoading(boolean loading) {
        registerButton.setDisable(loading);
        usernameField.setDisable(loading);
        displayNameField.setDisable(loading);
        if (genderComboBox != null) genderComboBox.setDisable(loading);
        passwordHiddenField.setDisable(loading);
        passwordVisibleField.setDisable(loading);
        confirmPasswordHiddenField.setDisable(loading);
        confirmPasswordVisibleField.setDisable(loading);
        termsCheckBox.setDisable(loading);
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
    }

    @FXML
    private void handleLogin() {
        navigateToLogin();
    }

    private void navigateToLogin() {
        try {
            Stage stage = (Stage) registerButton.getScene().getWindow();
            SceneManager.showLogin(stage);
        } catch (Exception e) {
            logger.error("Error navigating to login screen", e);
            showError("Error navigating to login screen");
            setLoading(false);
        }
    }
}
