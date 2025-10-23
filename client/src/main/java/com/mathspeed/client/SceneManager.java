package com.mathspeed.client;

import com.mathspeed.util.ReloadManager;
import com.mathspeed.util.ResourceLoader;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class SceneManager {
    private static final Logger logger = LoggerFactory.getLogger(SceneManager.class);
    private static SceneManager instance;
    private Stage primaryStage;
    private String currentUsername;

    public static final double WINDOW_WIDTH = 360;
    public static final double WINDOW_HEIGHT = 640;

    private SceneManager() {}

    public static SceneManager getInstance() {
        if (instance == null) {
            instance = new SceneManager();
        }
        return instance;
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    // Instance methods for controllers to call
    public void switchToLogin() {
        if (primaryStage != null) {
            showLogin(primaryStage);
        }
    }

    public void switchToRegister() {
        if (primaryStage != null) {
            showRegister(primaryStage);
        }
    }

    public void switchToDashboard() {
        if (primaryStage != null) {
            showDashboard(primaryStage);
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
            stage.setWidth(WINDOW_WIDTH);
            stage.setHeight(WINDOW_HEIGHT);
            stage.show();
            new Thread(() -> {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                javafx.application.Platform.runLater(onFinish);
            }).start();
            logger.info("Splash screen shown");
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
            stage.setWidth(WINDOW_WIDTH);
            stage.setHeight(WINDOW_HEIGHT);
            stage.show();
            logger.info("Login screen shown");
            System.out.println("Hot Reload");
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
            stage.setWidth(WINDOW_WIDTH);
            stage.setHeight(WINDOW_HEIGHT);
            stage.show();
            logger.info("Register screen shown");
            System.out.println("Hot Reload");
        } catch (Exception e) {
            logger.error("Failed to show register screen", e);
            e.printStackTrace();
        }
    }

    public static void showDashboard(Stage stage) {
        getInstance().setPrimaryStage(stage);
        // Use stored username if available, otherwise reload with null
        String username = getInstance().currentUsername;
        if (username != null) {
            showDashboard(stage, username);
            return;
        }
        ReloadManager.setCurrentSceneReloader(SceneManager::showDashboard);
        try {
            Parent root = ResourceLoader.loadFXML("src/main/resources/fxml/dashboard.fxml", SceneManager.class);
            Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/theme.css", SceneManager.class));
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/dashboard.css", SceneManager.class));
            stage.setTitle("Math Speed Game - Dashboard");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.setWidth(WINDOW_WIDTH);
            stage.setHeight(WINDOW_HEIGHT);
            stage.show();
            logger.info("Dashboard screen shown");
            System.out.println("Hot Reload");
        } catch (Exception e) {
            logger.error("Failed to show dashboard screen", e);
            e.printStackTrace();
        }
    }

    public static void showDashboard(Stage stage, String username) {
        getInstance().setPrimaryStage(stage);
        getInstance().currentUsername = username; // Store username for reload
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
                loader = new FXMLLoader(SceneManager.class.getResource("/fxml/dashboard.fxml"));
            }
            root = loader.load();

            Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/theme.css", SceneManager.class));
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/dashboard.css", SceneManager.class));
            stage.setTitle("Math Speed Game - Dashboard");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.setWidth(WINDOW_WIDTH);
            stage.setHeight(WINDOW_HEIGHT);
            stage.show();
            com.mathspeed.controller.DashboardController controller = loader.getController();
            if (controller != null) {
                controller.setUsername(username);
            }
            logger.info("Dashboard screen shown with username: " + username);
            System.out.println("Hot Reload");
        } catch (Exception e) {
            logger.error("Failed to show dashboard screen", e);
            e.printStackTrace();
        }
    }
}
