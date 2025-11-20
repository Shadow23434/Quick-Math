package com.mathspeed.controller;

import com.mathspeed.util.SplashSpinner;
import com.mathspeed.client.WindowSizing;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

public class SplashController {
    @FXML
    private ImageView logoImage;
    @FXML
    private Pane spinnerPane;

    @FXML
    public void initialize() {
        try {
            Image logo = new Image(getClass().getResource("/images/logo.png").toExternalForm());
            logoImage.setImage(logo);
        } catch (Exception e) {
        }

        SplashSpinner spinner = new SplashSpinner(40, Color.WHITE, 3);
        spinnerPane.getChildren().add(spinner);

        // Apply window sizing: use default size (not desktop) so splash stays compact
        WindowSizing.applyToNode(logoImage, false);
    }
}
