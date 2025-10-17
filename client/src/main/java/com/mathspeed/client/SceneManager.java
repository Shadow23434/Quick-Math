package com.mathspeed.client;

import com.mathspeed.util.ReloadManager;
import com.mathspeed.util.ResourceLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SceneManager {
    private static final Logger logger = LoggerFactory.getLogger(SceneManager.class);
    public static final double WINDOW_WIDTH = 360;
    public static final double WINDOW_HEIGHT = 640;

    public static void showSplash(Stage stage, Runnable onFinish) {
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

    public static void showLobby(Stage stage) {
        ReloadManager.setCurrentSceneReloader(SceneManager::showLobby);
        try {
            Parent root = ResourceLoader.loadFXML("src/main/resources/fxml/lobby.fxml", SceneManager.class);
            Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/theme.css", SceneManager.class));
            scene.getStylesheets().add(ResourceLoader.loadCSS("src/main/resources/css/lobby.css", SceneManager.class));
            stage.setTitle("Math Speed Game - Lobby");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.setWidth(WINDOW_WIDTH);
            stage.setHeight(WINDOW_HEIGHT);
            stage.show();
            logger.info("Lobby screen shown");
        } catch (Exception e) {
            logger.error("Failed to show lobby screen", e);
            e.printStackTrace();
        }
    }
}
