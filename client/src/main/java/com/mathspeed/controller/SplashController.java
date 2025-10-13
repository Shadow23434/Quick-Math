package com.mathspeed.controller;

import com.mathspeed.util.SplashSpinner;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

public class SplashController {
    @FXML
    private AnchorPane root;
    @FXML
    private ImageView logoImage;
    @FXML
    private Label titleLabel;
    @FXML
    private Pane spinnerPane;

    @FXML
    public void initialize() {
        try {
            Image logo = new Image(getClass().getResource("/images/logo.png").toExternalForm());
            logoImage.setImage(logo);
        } catch (Exception e) {
        }
//
//        ScaleTransition scale = new ScaleTransition(javafx.util.Duration.seconds(1.2), logoImage);
//        scale.setFromX(0.7);
//        scale.setFromY(0.7);
//        scale.setToX(1.0);
//        scale.setToY(1.0);
//        scale.setCycleCount(1);
//        scale.play();
//
//        FadeTransition fade = new FadeTransition(javafx.util.Duration.seconds(1.2), titleLabel);
//        fade.setFromValue(0.0);
//        fade.setToValue(1.0);
//        fade.setCycleCount(1);
//        fade.play();

        SplashSpinner spinner = new SplashSpinner(40, Color.WHITE, 3);
        spinnerPane.getChildren().add(spinner);
    }
}
