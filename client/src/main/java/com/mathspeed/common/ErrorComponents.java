package com.mathspeed.common;

import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ErrorComponents {
    private static final Logger logger = LoggerFactory.getLogger(ErrorComponents.class);

    public static VBox createEmptyPlaceholder(String imageResourcePath) {
        VBox placeholder = new VBox(6);
        placeholder.setAlignment(Pos.TOP_CENTER);
        placeholder.getStyleClass().add("empty-placeholder");

        Region bgRegion = new Region();
        boolean bgSet = false;
         try {
             java.io.InputStream in = ErrorComponents.class.getResourceAsStream(imageResourcePath);
             if (in != null) {
                Image img = new Image(in);
                // Use 'contain' so the whole image scales down to fit the area (no cropping)
                BackgroundSize bgSize = new BackgroundSize(100, 100, true, true, true, false);
                BackgroundImage bimg = new BackgroundImage(img, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT, BackgroundPosition.DEFAULT, bgSize);
                bgRegion.setBackground(new Background(bimg));
                bgSet = true;
             } else {
                 java.net.URL res = ErrorComponents.class.getResource(imageResourcePath);
                 if (res != null) {
                    Image img = new Image(res.toExternalForm(), true);
                    // Use 'contain' so the whole image scales down to fit the area (no cropping)
                    BackgroundSize bgSize = new BackgroundSize(100, 100, true, true, true, false);
                    BackgroundImage bimg = new BackgroundImage(img, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT, BackgroundPosition.DEFAULT, bgSize);
                    bgRegion.setBackground(new Background(bimg));
                    bgSet = true;
                 }
             }
         } catch (Exception e) {
             logger.warn("Failed to load placeholder image {}: {}", imageResourcePath, e.getMessage());
         }

        if (bgSet) {
            // Allow the region to expand and fill available space
            bgRegion.getStyleClass().add("placeholder-image");
            bgRegion.setMinSize(0, 0);
            bgRegion.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
            bgRegion.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            VBox.setVgrow(bgRegion, Priority.ALWAYS);
            placeholder.getChildren().addAll(0, Collections.singletonList(bgRegion)); // add first so message appears on top
            // Bind the region size to the placeholder so the background covers the area
            bgRegion.prefWidthProperty().bind(placeholder.widthProperty());
            bgRegion.prefHeightProperty().bind(placeholder.heightProperty());

            // If the placeholder is attached to a Scene later, make sure the region still fills the scene
            placeholder.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    bgRegion.prefWidthProperty().bind(newScene.widthProperty());
                    bgRegion.prefHeightProperty().bind(newScene.heightProperty());
                } else {
                    // revert to placeholder bounds
                    bgRegion.prefWidthProperty().bind(placeholder.widthProperty());
                    bgRegion.prefHeightProperty().bind(placeholder.heightProperty());
                }
            });
        }
         return placeholder;
     }

     public static void showErrorAlert(String title, String message) {
         try {
             Alert a = new Alert(Alert.AlertType.ERROR);
             a.setTitle(title);
             a.setHeaderText(null);
             a.setContentText(message != null ? message : "Unknown error");
             a.showAndWait();
         } catch (Exception e) {
             logger.error("Failed to show alert: {} - {}", title, message, e);
         }
     }
 }
