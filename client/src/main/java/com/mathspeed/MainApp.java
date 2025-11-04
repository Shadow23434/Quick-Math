package com.mathspeed;

import com.mathspeed.controller.GameController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Arrays;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/GamePlay.fxml"));
        Parent root = loader.load();

        // If you want to test Dashboard instead:
        // FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Dashboard.fxml"));
        // Parent root = loader.load();

        Scene scene = new Scene(root);
        // Optional: add your stylesheet if exists
        // scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());

        stage.setTitle("MathSpeed - UI Test");
        stage.setScene(scene);
        stage.setWidth(700);
        stage.setHeight(520);
        stage.show();

        // Demo: start a round after UI is shown
        Object controller = loader.getController();
        if (controller instanceof GameController) {
            GameController gc = (GameController) controller;
            // populate sample numbers and start countdown 5s
            gc.populateServerNumbers(Arrays.asList(3, 8, 5, 2, 9), selected -> {
                // selection change callback (optional)
                System.out.println("Selected numbers: " + selected);
            });

            // start countdown -> when finished user can interact
            gc.startCountdown(5, () -> System.out.println("Countdown finished, inputs enabled"));

            // Show a sample round result 8 seconds later (so it appears after round)
            javafx.animation.Timeline t = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(javafx.util.Duration.seconds(8), e -> {
                        gc.showRoundResult(+1, -1, 3, 1, 3500); // visible 3.5s
                    })
            );
            t.play();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}