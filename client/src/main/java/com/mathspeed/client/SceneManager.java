package com.mathspeed.client;

import com.mathspeed.util.ReloadManager;
import com.mathspeed.util.ResourceLoader;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.EnumMap;

public class SceneManager {
    private static final Logger logger = LoggerFactory.getLogger(SceneManager.class);
    private static SceneManager instance;
    private Stage primaryStage; // removed Lombok @Setter
    private String currentUsername;
    public enum Screen { DASHBOARD, LIBRARY, FRIENDS, PROFILE, LEADERBOARD, LOGIN, REGISTER, SPLASH }
    private Screen currentScreen;

    public static final double WINDOW_WIDTH = 360;
    public static final double WINDOW_HEIGHT = 640;

    private SceneManager() {}

    public static SceneManager getInstance() {
        if (instance == null) {
            instance = new SceneManager();
        }
        return instance;
    }

    public void setPrimaryStage(Stage stage) { this.primaryStage = stage; }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public String getCurrentUsername() { return currentUsername; }
    public Screen getCurrentScreen() { return currentScreen; }
    private void setCurrent(Screen screen, String username) { this.currentScreen = screen; this.currentUsername = username; }

    // Instance methods for controllers to call
    public void switchToLogin() {
        // Ensure we reset shell/session state before showing login
        logout();
        if (primaryStage != null) {
            showLogin(primaryStage);
        }
    }

    public void switchToRegister() {
        if (primaryStage != null) {
            showRegister(primaryStage);
        }
    }

    public void switchToDashboard(String username) {
        if (primaryStage != null) {
            showDashboard(primaryStage, username);
        }
    }

    public void switchToLibrary(String username) {
        if (primaryStage != null) {
            showLibrary(primaryStage, username);
        }
    }

    public void switchToFriends(String username) {
        if (primaryStage != null) {
            showFriends(primaryStage, username);
        }
    }

    public void switchToProfile(String username) {
        if (primaryStage != null) {
            showProfile(primaryStage, username);
        }
    }

    public void switchToLeaderboard(String username) {
        if (primaryStage != null) {
            showLeaderboard(primaryStage, username);
        }
    }

    // Static methods for backward compatibility
    public static void showSplash(Stage stage, Runnable onFinish) {
        getInstance().setPrimaryStage(stage);
        ReloadManager.setCurrentSceneReloader(s -> showSplash(s, onFinish));
        try {
            Parent splashRoot = ResourceLoader.loadFXML("src/main/resources/fxml/splash.fxml", SceneManager.class);
            Scene splashScene = new Scene(splashRoot, WINDOW_WIDTH, WINDOW_HEIGHT);
            splashScene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/theme.css", SceneManager.class));
            splashScene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/splash.css", SceneManager.class));
            stage.setTitle("Math Speed Game - Splash");
            stage.setScene(splashScene);
            stage.setResizable(false);
            // Size the stage to the scene so layout (including bottom bar) is correct
            stage.sizeToScene();
            stage.show();
            new Thread(() -> {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                javafx.application.Platform.runLater(onFinish);
            }).start();
            logger.info("Splash screen shown");
            getInstance().currentScreen = Screen.SPLASH;
        } catch (Exception e) {
            logger.error("Failed to show splash screen", e);
            e.printStackTrace();
        }
    }

    public static void showLogin(Stage stage) {
        getInstance().setPrimaryStage(stage);
        ReloadManager.setCurrentSceneReloader(SceneManager::showLogin);
        try {
            Parent root = ResourceLoader.loadFXML("src/main/resources/fxml/login.fxml", SceneManager.class);
            Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/theme.css", SceneManager.class));
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/login.css", SceneManager.class));
            stage.setTitle("Math Speed Game - Login");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.sizeToScene();
            stage.show();
            logger.info("Login screen shown");
            getInstance().currentScreen = Screen.LOGIN;
        } catch (Exception e) {
            logger.error("Failed to show login screen", e);
            e.printStackTrace();
        }
    }

    public static void showRegister(Stage stage) {
        getInstance().setPrimaryStage(stage);
        ReloadManager.setCurrentSceneReloader(SceneManager::showRegister);
        try {
            Parent root = ResourceLoader.loadFXML("src/main/resources/fxml/register.fxml", SceneManager.class);
            Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/theme.css", SceneManager.class));
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/register.css", SceneManager.class));
            stage.setTitle("Math Speed Game - Register");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.sizeToScene();
            stage.show();
            logger.info("Register screen shown");
            getInstance().currentScreen = Screen.REGISTER;
        } catch (Exception e) {
            logger.error("Failed to show register screen", e);
            e.printStackTrace();
        }
    }

    public static void showDashboard(Stage stage, String username) {
        getInstance().setPrimaryStage(stage);
        getInstance().setCurrent(Screen.DASHBOARD, username);
        ReloadManager.setCurrentSceneReloader(s -> showDashboard(s, username));
        try {
            FXMLLoader loader = new FXMLLoader();
            Parent root = ResourceLoader.loadFXML("src/main/resources/fxml/dashboard.fxml", SceneManager.class);

            // Need to get the controller after loading, but ResourceLoader doesn't return it
            // So we need to use a different approach
            File file = new File("src/main/resources/fxml/dashboard.fxml");
            if (file.exists()) {
                loader = new FXMLLoader(file.toURI().toURL());
            } else {
                loader = new FXMLLoader(SceneManager.class.getResource("/fxml/pages/dashboard.fxml"));
            }
            root = loader.load();

            Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/theme.css", SceneManager.class));
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/dashboard.css", SceneManager.class));
            stage.setTitle("Math Speed Game - Dashboard");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.sizeToScene();
            stage.show();
            com.mathspeed.controller.DashboardController controller = loader.getController();
            if (controller != null) {
                controller.setUsername(username);
            }
            logger.info("Dashboard screen shown with username: " + username);
        } catch (Exception e) {
            logger.error("Failed to show dashboard screen", e);
            e.printStackTrace();
        }
    }

    public static void showLibrary(Stage stage, String username) {
        getInstance().setPrimaryStage(stage);
        getInstance().setCurrent(Screen.LIBRARY, username);
        ReloadManager.setCurrentSceneReloader(s -> showLibrary(s, username));
        try {
            FXMLLoader loader = new FXMLLoader();
            Parent root;

            File file = new File("src/main/resources/fxml/library.fxml");
            if (file.exists()) {
                loader = new FXMLLoader(file.toURI().toURL());
            } else {
                loader = new FXMLLoader(SceneManager.class.getResource("/fxml/pages/library.fxml"));
            }
            root = loader.load();

            Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/theme.css", SceneManager.class));
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/dashboard.css", SceneManager.class));
            stage.setTitle("Math Speed Game - Library");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.sizeToScene();
            stage.show();

            com.mathspeed.controller.LibraryController controller = loader.getController();
            if (controller != null) {
                controller.setUsername(username);
            }
            logger.info("Library screen shown with username: " + username);
        } catch (Exception e) {
            logger.error("Failed to show library screen", e);
            e.printStackTrace();
        }
    }

    public static void showFriends(Stage stage, String username) {
        getInstance().setPrimaryStage(stage);
        getInstance().setCurrent(Screen.FRIENDS, username);
        ReloadManager.setCurrentSceneReloader(s -> showFriends(s, username));
        try {
            FXMLLoader loader = new FXMLLoader();
            Parent root;

            File file = new File("src/main/resources/fxml/friends.fxml");
            if (file.exists()) {
                loader = new FXMLLoader(file.toURI().toURL());
            } else {
                loader = new FXMLLoader(SceneManager.class.getResource("/fxml/pages/friends.fxml"));
            }
            root = loader.load();

            Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/theme.css", SceneManager.class));
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/dashboard.css", SceneManager.class));
            stage.setTitle("Math Speed Game - Friends");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.sizeToScene();
            stage.show();

            com.mathspeed.controller.FriendsController controller = loader.getController();
            if (controller != null) {
                controller.setUsername(username);
            }
            logger.info("Friends screen shown with username: " + username);
        } catch (Exception e) {
            logger.error("Failed to show friends screen", e);
            e.printStackTrace();
        }
    }

    public static void showProfile(Stage stage, String username) {
        getInstance().setPrimaryStage(stage);
        getInstance().setCurrent(Screen.PROFILE, username);
        ReloadManager.setCurrentSceneReloader(s -> showProfile(s, username));
        try {
            FXMLLoader loader = new FXMLLoader();
            Parent root;

            File file = new File("src/main/resources/fxml/profile.fxml");
            if (file.exists()) {
                loader = new FXMLLoader(file.toURI().toURL());
            } else {
                loader = new FXMLLoader(SceneManager.class.getResource("/fxml/pages/profile.fxml"));
            }
            root = loader.load();

            Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/theme.css", SceneManager.class));
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/dashboard.css", SceneManager.class));
            stage.setTitle("Math Speed Game - Profile");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.sizeToScene();
            stage.show();

            com.mathspeed.controller.ProfileController controller = loader.getController();
            if (controller != null) {
                controller.setUsername(username);
            }
            logger.info("Profile screen shown with username: " + username);
        } catch (Exception e) {
            logger.error("Failed to show profile screen", e);
            e.printStackTrace();
        }
    }

    public static void showLeaderboard(Stage stage, String username) {
        getInstance().setPrimaryStage(stage);
        getInstance().setCurrent(Screen.LEADERBOARD, username);
        ReloadManager.setCurrentSceneReloader(s -> showLeaderboard(s, username));
        try {
            FXMLLoader loader = new FXMLLoader();
            Parent root;

            File file = new File("src/main/resources/fxml/leaderboard.fxml");
            if (file.exists()) {
                loader = new FXMLLoader(file.toURI().toURL());
            } else {
                loader = new FXMLLoader(SceneManager.class.getResource("/fxml/pages/leaderboard.fxml"));
            }
            root = loader.load();

            Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/theme.css", SceneManager.class));
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/dashboard.css", SceneManager.class));
            stage.setTitle("Math Speed Game - Leaderboard");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.sizeToScene();
            stage.show();

            com.mathspeed.controller.LeaderboardController controller = loader.getController();
            if (controller != null) {
                controller.setUsername(username);
            }
            logger.info("Leaderboard screen shown with username: " + username);
        } catch (Exception e) {
            logger.error("Failed to show leaderboard screen", e);
            e.printStackTrace();
        }
    }

    private EnumMap<Screen, Parent> viewCache = new EnumMap<>(Screen.class);
    private EnumMap<Screen, Object> controllerCache = new EnumMap<>(Screen.class);
    private com.mathspeed.controller.ShellController shellController;
    private boolean shellActive = false;

    // Shell initialization
    public void initShell(Stage stage, String username) {
        initShell(stage, username, null);
    }

    public void initShell(Stage stage, String username, Runnable onFullyReady) {
        this.primaryStage = stage;
        if (shellActive) {
            navigate(Screen.DASHBOARD);
            if (onFullyReady != null) onFullyReady.run();
            return;
        }
        try {
            // Set current user and a default screen early so nested controllers can rely on non-null
            setCurrent(Screen.DASHBOARD, username);

            File file = new File("src/main/resources/fxml/main_shell.fxml");
            FXMLLoader loader = file.exists() ? new FXMLLoader(file.toURI().toURL()) : new FXMLLoader(SceneManager.class.getResource("/fxml/main_shell.fxml"));
            Parent root = loader.load();
            shellController = loader.getController();
            Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/theme.css", SceneManager.class));
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/dashboard.css", SceneManager.class));
            stage.setTitle("Math Speed Game");
            stage.setScene(scene);
            stage.setResizable(false);

            // Wait until ShellController signals first screen is ready before showing stage
            if (shellController != null) {
                shellController.getFirstScreenReady().whenComplete((v, ex) -> {
                    Platform.runLater(() -> {
                        if (ex == null) {
                            stage.sizeToScene();
                            stage.show();
                            if (onFullyReady != null) onFullyReady.run();
                        } else {
                            logger.error("Shell failed to become ready", ex);
                            stage.sizeToScene();
                            stage.show();
                        }
                    });
                });
            }

            shellActive = true;
            if (shellController != null) shellController.init(username);
            // Preload common screens in background
            new Thread(() -> {
                Screen[] preload = { Screen.LIBRARY, Screen.FRIENDS, Screen.LEADERBOARD };
                for (Screen s : preload) {
                    try { loadScreenRoot(s); } catch (Exception e) { logger.warn("Failed to preload screen " + s, e); }
                }
            }, "shell-preload-thread").start();
            logger.info("Shell initialized for user {}", username);
        } catch (Exception e) {
            logger.error("Failed to initialize shell", e);
        }
    }

    public void loadShellAsync(Stage stage, String username, Runnable onSuccess, java.util.function.Consumer<Exception> onError) {
        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override protected Void call() {
                Platform.runLater(() -> initShell(stage, username, onSuccess));
                return null;
            }
        };
        task.setOnFailed(ev -> { if (onError != null) onError.accept(new Exception(task.getException())); });
        Thread th = new Thread(task, "shell-loader-async"); th.setDaemon(true); th.start();
    }

    // Navigate to a screen within the shell
    public void navigate(Screen screen) {
        if (!shellActive || shellController == null) {
            logger.warn("Cannot navigate - shell not active");
            return;
        }
        setCurrent(screen, currentUsername);
        shellController.show(screen);
        logger.info("Navigated to screen: {}", screen);
    }

    // Load a screen's root node (with caching)
    public Parent loadScreenRoot(Screen screen) throws Exception {
        // Check cache first
        if (viewCache.containsKey(screen)) {
            logger.debug("Returning cached view for screen: {}", screen);
            Parent cached = viewCache.get(screen);
            if (cached.getScene() != null) {
                cached.applyCss();
                cached.layout();
                logger.debug("Applied CSS to cached view for screen: {}", screen);
            }
            return cached;
        }

        // Load the FXML for this screen
        String fxmlPath = getFxmlPathForScreen(screen);
        FXMLLoader loader;
        Parent root;

        File file = new File(fxmlPath);
        if (file.exists()) {
            loader = new FXMLLoader(file.toURI().toURL());
        } else {
            loader = new FXMLLoader(SceneManager.class.getResource(fxmlPath.replace("src/main/resources", "")));
        }
        root = loader.load();
        Object controller = loader.getController();
        // Cache controller
        controllerCache.put(screen, controller);
        // Set username
        if (controller != null && currentUsername != null) {
            try {
                java.lang.reflect.Method setUsernameMethod = controller.getClass().getMethod("setUsername", String.class);
                setUsernameMethod.invoke(controller, currentUsername);
                logger.debug("Set username on controller for screen: {}", screen);
            } catch (NoSuchMethodException e) {
                logger.debug("Controller for {} doesn't have setUsername method", screen);
            } catch (Exception e) {
                logger.warn("Failed to set username on controller for screen: {}", screen, e);
            }
        }
        loadStylesheetsForView(root, screen);
        viewCache.put(screen, root);
        logger.info("Loaded and cached view + controller for screen: {}", screen);
        return root;
    }

    // Provide access to controller
    public Object getController(Screen screen) {
        return controllerCache.get(screen);
    }

    /**
     * Load CSS stylesheets for a specific view
     */
    private void loadStylesheetsForView(Parent view, Screen screen) {
        if (view == null) return;

        try {
            // Get the stylesheets list from the view
            javafx.collections.ObservableList<String> stylesheets = view.getStylesheets();

            // Load theme CSS (common for all screens)
            String themeCSS = ResourceLoader.loadCSS("src/main/resources/css/theme.css", SceneManager.class);
            if (themeCSS != null && !stylesheets.contains(themeCSS)) {
                stylesheets.add(themeCSS);
                logger.debug("Added theme.css to view for screen: {}", screen);
            }

            // Load screen-specific CSS
            String screenCSS = getCSSPathForScreen(screen);
            if (screenCSS != null) {
                String loadedCSS = ResourceLoader.loadCSS(screenCSS, SceneManager.class);
                if (loadedCSS != null && !stylesheets.contains(loadedCSS)) {
                    stylesheets.add(loadedCSS);
                    logger.debug("Added screen-specific CSS to view for screen: {}", screen);
                }
            }

            logger.info("Loaded {} stylesheets for screen: {}", stylesheets.size(), screen);
        } catch (Exception e) {
            logger.warn("Failed to load CSS for screen: {}", screen, e);
        }
    }

    /**
     * Get CSS path for a specific screen
     */
    private String getCSSPathForScreen(Screen screen) {
        return switch (screen) {
            case DASHBOARD, LIBRARY, FRIENDS, PROFILE, LEADERBOARD -> "src/main/resources/css/dashboard.css";
            case LOGIN -> "src/main/resources/css/login.css";
            case REGISTER -> "src/main/resources/css/register.css";
            case SPLASH -> "src/main/resources/css/splash.css";
        };
    }

    // Helper to get FXML path for a screen
    private String getFxmlPathForScreen(Screen screen) {
        return switch (screen) {
            case DASHBOARD -> "src/main/resources/fxml/pages/dashboard.fxml";
            case LIBRARY -> "src/main/resources/fxml/pages/library.fxml";
            case FRIENDS -> "src/main/resources/fxml/pages/friends.fxml";
            case PROFILE -> "src/main/resources/fxml/pages/profile.fxml";
            case LEADERBOARD -> "src/main/resources/fxml/pages/leaderboard.fxml";
            case LOGIN -> "src/main/resources/fxml/pages/login.fxml";
            case REGISTER -> "src/main/resources/fxml/pages/register.fxml";
            case SPLASH -> "src/main/resources/fxml/pages/splash.fxml";
        };
    }

    // Logout/reset helpers
    public void logout() {
        try {
            // Clear cached views and reset shell/controller state
            if (viewCache != null) viewCache.clear();
            shellActive = false;
            shellController = null;
            currentUsername = null;
            currentScreen = null;
            logger.info("SceneManager state reset on logout");
        } catch (Exception e) {
            logger.warn("Error during logout reset", e);
        }
    }
}
