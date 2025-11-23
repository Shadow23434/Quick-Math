package com.mathspeed;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/dashboard.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root);
        stage.setTitle("MathSpeed - UI Test");
        stage.setScene(scene);
        stage.setWidth(900);
        stage.setHeight(600);
        stage.show();

        Object controller = loader.getController();
    }

    public static void main(String[] args) {
        launch(args);
    }
}