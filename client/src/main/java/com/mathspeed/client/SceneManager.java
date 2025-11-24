package com.mathspeed.client;

import com.mathspeed.controller.GameplayController;
import com.mathspeed.model.Player;
import com.mathspeed.network.NetworkGameplay;
import com.mathspeed.util.WindowConfig;
import com.mathspeed.util.ReloadManager;
import com.mathspeed.util.ResourceLoader;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.EnumMap;
import javafx.geometry.Rectangle2D;

public class SceneManager {
    private static final Logger logger = LoggerFactory.getLogger(SceneManager.class);
    private static SceneManager instance;
    @Setter
    private Stage primaryStage;
    private Player currentPlayer;
    public enum Screen { DASHBOARD, LIBRARY, FRIENDS, PROFILE, LEADERBOARD, LOGIN, REGISTER, SPLASH, GAMEPLAY,POLICY }
    @Getter
    private Screen currentScreen;
    private EnumMap<Screen, Parent> viewCache = new EnumMap<>(Screen.class);
    private EnumMap<Screen, Object> controllerCache = new EnumMap<>(Screen.class);
    private com.mathspeed.controller.ShellController shellController;
    private boolean shellActive = false;
    private double prevX, prevY, prevWidth, prevHeight;
    private boolean hasPrevBounds = false;
    private boolean usingDesktopSize = false;

    private SceneManager() {}

    public static SceneManager getInstance() {
        if (instance == null) {
            instance = new SceneManager();
        }
        return instance;
    }

    private void setCurrent(Screen screen, Player currentPlayer) { this.currentScreen = screen; this.currentPlayer = SessionManager.getInstance().getCurrentPlayer(); }

    public void switchToLogin() {
        // Ensure we reset shell/session state before showing login
        logout();
        if (primaryStage != null) {
            showLogin(primaryStage);
        }
    }

    public static void showSplash(Stage stage, Runnable onFinish) {
        getInstance().setPrimaryStage(stage);
        // Ensure lightweight screens use default size and do not apply desktop-size behavior
        try {
            SceneManager inst = getInstance();
            inst.usingDesktopSize = false;
            if (stage.isMaximized()) {
                // If the stage is maximized, restore it to default size so splash shows at DEFAULT_* dimensions
                try { stage.setMaximized(false); } catch (Exception ignored) {}
                stage.setWidth(WindowConfig.DEFAULT_WIDTH);
                stage.setHeight(WindowConfig.DEFAULT_HEIGHT);
                Rectangle2D vb = javafx.stage.Screen.getPrimary().getVisualBounds();
                stage.setX(vb.getMinX() + (vb.getWidth() - WindowConfig.DEFAULT_WIDTH) / 2.0);
                stage.setY(vb.getMinY() + (vb.getHeight() - WindowConfig.DEFAULT_HEIGHT) / 2.0);
            }
        } catch (Exception e) {
            // non-fatal, continue to show splash
            logger.debug("Failed to enforce default sizing for splash", e);
        }

        ReloadManager.setCurrentSceneReloader(s -> showSplash(s, onFinish));
        try {
            Parent splashRoot = ResourceLoader.loadFXML("src/main/resources/fxml/splash.fxml", SceneManager.class);
            Scene splashScene = new Scene(splashRoot, WindowConfig.DEFAULT_WIDTH, WindowConfig.DEFAULT_HEIGHT);
            splashScene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/theme.css", SceneManager.class));
            splashScene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/splash.css", SceneManager.class));
            stage.setTitle("Math Speed Game - Splash");
            stage.setScene(splashScene);
            // Keep splash fixed size
            stage.setResizable(false);
            // Size the stage to the scene so layout (including bottom bar) is correct
            if (!stage.isMaximized()) stage.sizeToScene();
            stage.show();
            new Thread(() -> {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                javafx.application.Platform.runLater(onFinish);
            }).start();
            getInstance().currentScreen = Screen.SPLASH;
        } catch (Exception e) {
            logger.error("Failed to show splash screen", e);
            e.printStackTrace();
        }
    }

    public static void showLogin(Stage stage) {
        getInstance().setPrimaryStage(stage);
        // Ensure lightweight screens use default size and do not apply desktop-size behavior
        try {
            SceneManager inst = getInstance();
            inst.usingDesktopSize = false;
            if (stage.isMaximized()) {
                try { stage.setMaximized(false); } catch (Exception ignored) {}
                stage.setWidth(WindowConfig.DEFAULT_WIDTH);
                stage.setHeight(WindowConfig.DEFAULT_HEIGHT);
                Rectangle2D vb = javafx.stage.Screen.getPrimary().getVisualBounds();
                stage.setX(vb.getMinX() + (vb.getWidth() - WindowConfig.DEFAULT_WIDTH) / 2.0);
                stage.setY(vb.getMinY() + (vb.getHeight() - WindowConfig.DEFAULT_HEIGHT) / 2.0);
            }
        } catch (Exception e) {
            logger.debug("Failed to enforce default sizing for login", e);
        }

        ReloadManager.setCurrentSceneReloader(SceneManager::showLogin);
        try {
            Parent root = ResourceLoader.loadFXML("src/main/resources/fxml/login.fxml", SceneManager.class);
            Scene scene = new Scene(root, WindowConfig.DEFAULT_WIDTH, WindowConfig.DEFAULT_HEIGHT);
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/theme.css", SceneManager.class));
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/login.css", SceneManager.class));
            stage.setTitle("Math Speed Game - Login");
            stage.setScene(scene);
            stage.setResizable(false);
            if (!stage.isMaximized()) stage.sizeToScene();
            stage.show();
            getInstance().currentScreen = Screen.LOGIN;
        } catch (Exception e) {
            logger.error("Failed to show login screen", e);
            e.printStackTrace();
        }
    }

    public static void showRegister(Stage stage) {
        getInstance().setPrimaryStage(stage);
        // Ensure lightweight screens use default size and do not apply desktop-size behavior
        try {
            SceneManager inst = getInstance();
            inst.usingDesktopSize = false;
            if (stage.isMaximized()) {
                try { stage.setMaximized(false); } catch (Exception ignored) {}
                stage.setWidth(WindowConfig.DEFAULT_WIDTH);
                stage.setHeight(WindowConfig.DEFAULT_HEIGHT);
                Rectangle2D vb = javafx.stage.Screen.getPrimary().getVisualBounds();
                stage.setX(vb.getMinX() + (vb.getWidth() - WindowConfig.DEFAULT_WIDTH) / 2.0);
                stage.setY(vb.getMinY() + (vb.getHeight() - WindowConfig.DEFAULT_HEIGHT) / 2.0);
            }
        } catch (Exception e) {
            logger.debug("Failed to enforce default sizing for register", e);
        }

        ReloadManager.setCurrentSceneReloader(SceneManager::showRegister);
        try {
            Parent root = ResourceLoader.loadFXML("src/main/resources/fxml/register.fxml", SceneManager.class);
            Scene scene = new Scene(root, WindowConfig.DEFAULT_WIDTH, WindowConfig.DEFAULT_HEIGHT);
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/theme.css", SceneManager.class));
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/register.css", SceneManager.class));
            stage.setTitle("Math Speed Game - Register");
            stage.setScene(scene);
            stage.setResizable(false);
            if (!stage.isMaximized()) stage.sizeToScene();
            stage.show();
            getInstance().currentScreen = Screen.REGISTER;
        } catch (Exception e) {
            logger.error("Failed to show register screen", e);
            e.printStackTrace();
        }
    }

    public void initShell(Stage stage, Player currentPlayer, Runnable onFullyReady) {
        Stage usedStage = (stage != null) ? stage : this.primaryStage;
        if (usedStage == null) {
            logger.error("initShell called without a Stage; cannot initialize shell");
            return;
        }
        this.primaryStage = usedStage;

        if (shellActive) {
            navigate(Screen.DASHBOARD);
            if (onFullyReady != null) onFullyReady.run();
            return;
        }
        try {
            // Set current user and a default screen early so nested controllers can rely on non-null
            setCurrent(Screen.DASHBOARD, currentPlayer);

            // Try multiple candidate filenames (with underscore or hyphen) both on disk and classpath
            String[] candidates = {"main_shell.fxml", "main-shell.fxml"};
            FXMLLoader loader = null;
            for (String candidate : candidates) {
                File f = new File("src/main/resources/fxml/" + candidate);
                if (f.exists()) {
                    loader = new FXMLLoader(f.toURI().toURL());
                    break;
                }
            }
            if (loader == null) {
                for (String candidate : candidates) {
                    java.net.URL res = SceneManager.class.getResource("/fxml/" + candidate);
                    if (res != null) {
                        loader = new FXMLLoader(res);
                        break;
                    }
                }
            }
            if (loader == null) {
                logger.error("main shell FXML not found on filesystem or classpath (tried main_shell.fxml and main-shell.fxml)");
                throw new RuntimeException("main shell FXML not found in src/main/resources/fxml or on classpath");
            }
            Parent root = loader.load();
            shellController = loader.getController();
            Scene scene = new Scene(root, WindowConfig.DEFAULT_WIDTH, WindowConfig.DEFAULT_HEIGHT);
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/theme.css", SceneManager.class));
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/dashboard.css", SceneManager.class));
            usedStage.setTitle("Math Speed Game");
            usedStage.setScene(scene);
            // Allow shell to be resizable
            usedStage.setResizable(true);
            usedStage.setMinWidth(WindowConfig.MIN_WIDTH);
            usedStage.setMinHeight(WindowConfig.MIN_HEIGHT);

            // Toggle maximize behavior to apply a fixed desktop size (DESKTOP_WIDTH x DESKTOP_HEIGHT)
            // and allow restoring previous bounds when toggled again.
            usedStage.maximizedProperty().addListener((obs, wasMax, isNowMax) -> {
                if (isNowMax) {
                    Rectangle2D vb = javafx.stage.Screen.getPrimary().getVisualBounds();
                    double targetW = WindowConfig.DESKTOP_WIDTH;
                    double targetH = WindowConfig.DESKTOP_HEIGHT;
                    // Ensure we don't exceed the visual bounds
                    if (targetW > vb.getWidth()) targetW = vb.getWidth();
                    if (targetH > vb.getHeight()) targetH = vb.getHeight();
                    double targetX = vb.getMinX() + (vb.getWidth() - targetW) / 2.0;
                    double targetY = vb.getMinY() + (vb.getHeight() - targetH) / 2.0;

                    if (!usingDesktopSize) {
                        // Save current bounds for restore
                        try {
                            prevX = usedStage.getX(); prevY = usedStage.getY();
                            prevWidth = usedStage.getWidth(); prevHeight = usedStage.getHeight();
                            hasPrevBounds = true;
                        } catch (Exception ignored) { hasPrevBounds = false; }

                        // Apply desktop size centered on visual bounds
                        usedStage.setMaximized(false);
                        usedStage.setWidth(targetW);
                        usedStage.setHeight(targetH);
                        usedStage.setX(targetX);
                        usedStage.setY(targetY);
                        usingDesktopSize = true;
                    } else {
                        // Restore previous bounds if available
                        if (hasPrevBounds) {
                            usedStage.setMaximized(false);
                            usedStage.setWidth(prevWidth);
                            usedStage.setHeight(prevHeight);
                            usedStage.setX(prevX);
                            usedStage.setY(prevY);
                        }
                        usingDesktopSize = false;
                    }
                }
            });
             // Wait until ShellController signals first screen is ready before showing stage
            if (shellController != null) {
                shellController.getFirstScreenReady().whenComplete((v, ex) -> {
                    Platform.runLater(() -> {
                        if (ex == null) {
                            if (!usedStage.isMaximized()) usedStage.sizeToScene();
                            usedStage.show();
                             if (onFullyReady != null) onFullyReady.run();
                        } else {
                            logger.error("Shell failed to become ready", ex);
                            if (!usedStage.isMaximized()) usedStage.sizeToScene();
                            usedStage.show();
                        }
                    });
                });
            }

            shellActive = true;
            if (shellController != null) shellController.init();
            // Preload common screens in background
            new Thread(() -> {
                Screen[] preload = { Screen.LIBRARY, Screen.FRIENDS, Screen.LEADERBOARD };
                for (Screen s : preload) {
                    try { loadScreenRoot(s); } catch (Exception e) { logger.warn("Failed to preload screen " + s, e); }
                }
            }, "shell-preload-thread").start();
        } catch (Exception e) {
            logger.error("Failed to initialize shell", e);
        }
    }

    public void loadShellAsync(Stage stage, Player currentPlayer, Runnable onSuccess, java.util.function.Consumer<Exception> onError) {
        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override protected Void call() {
                Platform.runLater(() -> initShell(stage, currentPlayer, onSuccess));
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
        setCurrent(screen, currentPlayer);
        shellController.show(screen);
        try {
            com.mathspeed.controller.ShellController sc = shellController;
            if (sc != null && sc.getBottomNavController() != null) {
                com.mathspeed.controller.BottomNavController bnc = sc.getBottomNavController();
                switch (screen) {
                    case DASHBOARD -> bnc.setActiveScreen(com.mathspeed.controller.BottomNavController.Screen.HOME);
                    case LIBRARY -> bnc.setActiveScreen(com.mathspeed.controller.BottomNavController.Screen.LIBRARY);
                    case FRIENDS -> bnc.setActiveScreen(com.mathspeed.controller.BottomNavController.Screen.FRIENDS);
                    case LEADERBOARD -> bnc.setActiveScreen(com.mathspeed.controller.BottomNavController.Screen.LEADERBOARD);
                    default -> { /* no bottom highlight for these screens */ }
                }
            }
        } catch (Exception ex) {
            logger.debug("Failed to update bottom nav state after navigation", ex);
        }
    }

    // Load a screen's root node (with caching)
    public Parent loadScreenRoot(Screen screen) throws Exception {
        // Check cache first
        if (viewCache.containsKey(screen)) {
            Parent cached = viewCache.get(screen);
            if (cached.getScene() != null) {
                cached.applyCss();
                cached.layout();
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
        loadStylesheetsForView(root, screen);
        viewCache.put(screen, root);
        return root;
    }

    public Object getController(Screen screen) {
        return controllerCache.get(screen);
    }

    private void loadStylesheetsForView(Parent view, Screen screen) {
        if (view == null) return;

        try {
            // Get the stylesheets list from the view
            javafx.collections.ObservableList<String> stylesheets = view.getStylesheets();

            // Load theme CSS (common for all screens)
            String themeCSS = ResourceLoader.loadCSS("src/main/resources/css/theme.css", SceneManager.class);
            if (themeCSS != null && !stylesheets.contains(themeCSS)) {
                stylesheets.add(themeCSS);
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
        } catch (Exception e) {
            logger.warn("Failed to load CSS for screen: {}", screen, e);
        }
    }

    private String getCSSPathForScreen(Screen screen) {
        return switch (screen) {
            case DASHBOARD, LIBRARY, FRIENDS, PROFILE, LEADERBOARD, POLICY -> "src/main/resources/css/dashboard.css";
            case LOGIN -> "src/main/resources/css/login.css";
            case REGISTER -> "src/main/resources/css/register.css";
            case SPLASH -> "src/main/resources/css/splash.css";
            case GAMEPLAY -> "src/main/resources/css/gameplay.css";
        };
    }

    private String getFxmlPathForScreen(Screen screen) {
        return switch (screen) {
            case DASHBOARD -> "src/main/resources/fxml/pages/dashboard.fxml";
            case LIBRARY -> "src/main/resources/fxml/pages/library.fxml";
            case FRIENDS -> "src/main/resources/fxml/pages/friends.fxml";
            case PROFILE -> "src/main/resources/fxml/pages/profile.fxml";
            case POLICY -> "src/main/resources/fxml/pages/policy.fxml";
            case LEADERBOARD -> "src/main/resources/fxml/pages/leaderboard.fxml";
            case LOGIN -> "src/main/resources/fxml/pages/login.fxml";
            case REGISTER -> "src/main/resources/fxml/pages/register.fxml";
            case SPLASH -> "src/main/resources/fxml/pages/splash.fxml";
            case GAMEPLAY -> "src/main/resources/fxml/pages/gameplay.fxml";
        };
    }

    // Logout/reset helpers
    public void logout() {
        try {
            // Clear cached views and reset shell/controller state
            if (viewCache != null) viewCache.clear();
            shellActive = false;
            shellController = null;
            currentPlayer = null;
            currentScreen = null;
        } catch (Exception e) {
            logger.warn("Error during logout reset", e);
        }
    }


    //Gameplay Screen Specific Methods
    public static void showGame(Stage stage, Player currentPlayer) {
        showGame(stage, currentPlayer, null);
    }

    public static void showGame(Stage stage, Player currentPlayer, NetworkGameplay client) {
        getInstance().setPrimaryStage(stage);
        try {
            SceneManager inst = getInstance();
            inst.usingDesktopSize = false;
            if (stage.isMaximized()) {
                try { stage.setMaximized(false); } catch (Exception ignored) {}
                stage.setWidth(WindowConfig.DEFAULT_WIDTH);
                stage.setHeight(WindowConfig.DEFAULT_HEIGHT);
                Rectangle2D vb = javafx.stage.Screen.getPrimary().getVisualBounds();
                stage.setX(vb.getMinX() + (vb.getWidth() - WindowConfig.DEFAULT_WIDTH) / 2.0);
                stage.setY(vb.getMinY() + (vb.getHeight() - WindowConfig.DEFAULT_HEIGHT) / 2.0);
            }
        } catch (Exception e) {
            logger.debug("Failed to enforce sizing for gameplay", e);
        }

        // Ensure reload will call the variant that accepts a client
        ReloadManager.setCurrentSceneReloader(s -> showGame(s, currentPlayer, client));
        try {
            String fxmlPath = getInstance().getFxmlPathForScreen(Screen.GAMEPLAY);
            Parent root;
            File file = new File(fxmlPath);
            FXMLLoader loader;
            if (file.exists()) {
                loader = new FXMLLoader(file.toURI().toURL());
            } else {
                String resourcePath = fxmlPath.replace("src/main/resources", "");
                loader = new FXMLLoader(SceneManager.class.getResource(resourcePath));
            }
            root = loader.load();
            Object controller = loader.getController();

// DEBUG: log controller identity and player being shown
            System.out.println("showGame: loaded controller instance=" + System.identityHashCode(controller)
                    + " for player=" + (currentPlayer == null ? "null" : currentPlayer.getUsername()));

// Do NOT cache controller/view for gameplay if you need independent windows.
// getInstance().controllerCache.put(Screen.GAMEPLAY, controller);
// getInstance().viewCache.put(Screen.GAMEPLAY, root);

            Scene scene = new Scene(root, WindowConfig.DEFAULT_WIDTH, WindowConfig.DEFAULT_HEIGHT);
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/theme.css", SceneManager.class));
            String gameplayCssPath = getInstance().getCSSPathForScreen(Screen.GAMEPLAY);
            try {
                String css = ResourceLoader.loadCSS(gameplayCssPath, SceneManager.class);
                if (css != null) scene.getStylesheets().add(css);
            } catch (Exception ignored) {}

            stage.setTitle("Math Speed Game - Gameplay");
            stage.setScene(scene);
            stage.setResizable(false);
            if (!stage.isMaximized()) stage.sizeToScene();

            if (controller instanceof GameplayController) {
                GameplayController gc = (GameplayController) controller;
                if (currentPlayer != null) {
                    try { gc.setPlayerUsername(currentPlayer.getUsername()); } catch (Exception ignored) {}
                }
                if (client != null) {
                    try { gc.setGameClient(client); } catch (Exception ex) {
                        logger.warn("Failed to inject NetworkGameplay into GameplayController", ex);
                    }
                }
            }

            stage.show();
            getInstance().currentScreen = Screen.GAMEPLAY;
        } catch (Exception e) {
            logger.error("Failed to show gameplay screen", e);
            e.printStackTrace();
        }
    }
}
