package com.mathspeed.util;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class ReloadManager {
    private static final Logger logger = LoggerFactory.getLogger(ReloadManager.class);
    private static Stage primaryStage;
    private static Consumer<Stage> currentSceneReloader;

    public static void setPrimaryStage(Stage stage) {
        primaryStage = stage;
    }

    public static void setCurrentSceneReloader(Consumer<Stage> reloader) {
        currentSceneReloader = reloader;
    }

    public static void reloadCurrentScene() {
        if (primaryStage != null && currentSceneReloader != null) {
            Platform.runLater(() -> {
                try {
                    currentSceneReloader.accept(primaryStage);
                    logger.info("Scene reloaded successfully");
                } catch (Exception e) {
                    logger.error("Failed to reload scene", e);
                }
            });
        } else {
            logger.warn("Cannot reload: primaryStage or reloader not set");
        }
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }
}
