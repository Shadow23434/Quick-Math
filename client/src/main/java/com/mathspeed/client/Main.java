package com.mathspeed.client;

import com.mathspeed.util.ReloadManager;
import javafx.application.Application;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main extends Application {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    @Override
    public void start(Stage primaryStage) {
        SceneManager.getInstance().setPrimaryStage(primaryStage);

        ReloadManager.setPrimaryStage(primaryStage);
        SceneManager.showSplash(primaryStage, () -> SceneManager.showDashboard(primaryStage));
    }

    @Override
    public void stop() {
        logger.info("Application stopping");
        // Cleanup resources here
    }

    public static void main(String[] args) {
        launch(args);
    }
}