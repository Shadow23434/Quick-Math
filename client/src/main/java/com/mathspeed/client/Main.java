package com.mathspeed.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Main extends Application {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    // Screen size
    public static final double WINDOW_WIDTH = 360;
    public static final double WINDOW_HEIGHT = 640;

    @Override
    public void start(Stage primaryStage) {
        try {
            String splashFxmlPath = "src/main/resources/fxml/splash.fxml";
            String splashCssPath = "src/main/resources/css/splash.css";
            String themeCssPath = "src/main/resources/css/theme.css";
            Parent splashRoot;
            FXMLLoader splashLoader;
            java.io.File splashFile = new java.io.File(splashFxmlPath);
            if (splashFile.exists()) {
                splashLoader = new FXMLLoader(splashFile.toURI().toURL());
                splashRoot = splashLoader.load();
            } else {
                splashLoader = new FXMLLoader(getClass().getResource("/fxml/splash.fxml"));
                splashRoot = splashLoader.load();
            }
            Scene splashScene = new Scene(splashRoot, WINDOW_WIDTH, WINDOW_HEIGHT);
            // Load splash.css
            java.io.File splashCssFile = new java.io.File(splashCssPath);
            if (splashCssFile.exists()) {
                splashScene.getStylesheets().add(splashCssFile.toURI().toString());
            } else {
                splashScene.getStylesheets().add(getClass().getResource("/css/splash.css").toExternalForm());
            }
            // Load theme.css
            java.io.File themeCssFile = new java.io.File(themeCssPath);
            if (themeCssFile.exists()) {
                splashScene.getStylesheets().add(themeCssFile.toURI().toString());
            } else {
                splashScene.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
            }
            primaryStage.setTitle("Math Speed Game - Splash");
            primaryStage.setScene(splashScene);
            primaryStage.setResizable(false);
            primaryStage.setWidth(WINDOW_WIDTH);
            primaryStage.setHeight(WINDOW_HEIGHT);
            primaryStage.show();

            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {}
                Platform.runLater(() -> showLogin(primaryStage));
            }).start();

            logger.info("Splash screen shown");
        } catch (Exception e) {
            logger.error("Failed to start application", e);
            e.printStackTrace();
        }
    }

    private void showLogin(Stage primaryStage) {
        try {
            String fxmlPath = "src/main/resources/fxml/login.fxml";
            String cssPath = "src/main/resources/css/login.css";
            String themeCssPath = "src/main/resources/css/theme.css";
            Parent root;
            FXMLLoader loader;
            java.io.File fxmlFile = new java.io.File(fxmlPath);
            if (fxmlFile.exists()) {
                loader = new FXMLLoader(fxmlFile.toURI().toURL());
                root = loader.load();
            } else {
                loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
                root = loader.load();
            }
            Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
            java.io.File cssFile = new java.io.File(cssPath);
            if (cssFile.exists()) {
                scene.getStylesheets().add(cssFile.toURI().toString());
            } else {
                scene.getStylesheets().add(getClass().getResource("/css/login.css").toExternalForm());
            }
            // Load theme.css nếu có
            java.io.File themeCssFile = new java.io.File(themeCssPath);
            if (themeCssFile.exists()) {
                scene.getStylesheets().add(themeCssFile.toURI().toString());
            } else {
                scene.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
            }
            // Reload button
            final Button restartButton = new Button("Restart");
            restartButton.setOnAction(__ -> {
                System.out.println("Restarting app!");
                primaryStage.close();
                Platform.runLater(() -> new Main().start(new Stage()));
            });
            if (root instanceof javafx.scene.layout.Pane) {
                ((javafx.scene.layout.Pane) root).getChildren().add(restartButton);
                restartButton.setLayoutX(10);
                restartButton.setLayoutY(10);
            }
            primaryStage.setTitle("Math Speed Game - Login");
            primaryStage.setScene(scene);
            primaryStage.setResizable(false);
            primaryStage.setWidth(WINDOW_WIDTH);
            primaryStage.setHeight(WINDOW_HEIGHT);
            primaryStage.show();
            logger.info("Login screen shown");
        } catch (Exception e) {
            logger.error("Failed to show login screen", e);
            e.printStackTrace();
        }
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