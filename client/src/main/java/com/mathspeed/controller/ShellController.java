package com.mathspeed.controller;

import com.mathspeed.client.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;

public class ShellController {
    private static final Logger logger = LoggerFactory.getLogger(ShellController.class);

    @FXML private BorderPane root;
    @FXML private StackPane contentPane;
    @FXML private StackPane bottomNav; // root node of included bottom_nav.fxml
    @FXML private BottomNavController bottomNavController; // injected from fx:include

    private String username;

    // Future completes when the first content view has been attached and laid out
    private final CompletableFuture<Void> firstScreenReady = new CompletableFuture<>();
    private boolean firstReadySignalled = false;

    public CompletableFuture<Void> getFirstScreenReady() { return firstScreenReady; }

    @FXML
    private void initialize() {
        logger.info("ShellController initialized");
        logger.info("root: {}", root);
        logger.info("contentPane: {}", contentPane);
        logger.info("bottomNav (root): {}", bottomNav);
        logger.info("bottomNavController: {}", bottomNavController);

        // Ensure bottomNav is attached to root bottom (safety if FXML include didn't wire as expected)
        if (root != null && bottomNav != null && root.getBottom() != bottomNav) {
            root.setBottom(bottomNav);
            logger.info("Attached bottomNav to root.bottom programmatically");
        }

        // Defensive fixes for bottomNav: make visible, managed, reset transforms and bring to front
        if (bottomNav != null) {
            bottomNav.setVisible(true);
            bottomNav.setManaged(true);
            bottomNav.setOpacity(1.0);
            bottomNav.setTranslateX(0);
            bottomNav.setTranslateY(0);
            bottomNav.toFront();
            logger.info("Enforced bottomNav visibility/position and toFront");
        }

        // Log bottomNav bounds once layout has occurred
        javafx.application.Platform.runLater(() -> {
            if (bottomNav != null) {
                logger.info("bottomNav layoutBounds (post-layout): {}", bottomNav.getLayoutBounds());
                logger.info("bottomNav boundsInParent (post-layout): {}", bottomNav.getBoundsInParent());
                logger.info("bottomNav visible={}, managed={}, opacity={}, translateY={}",
                    bottomNav.isVisible(), bottomNav.isManaged(), bottomNav.getOpacity(), bottomNav.getTranslateY());
            }
        });
    }

    public void init(String username) {
        logger.info("ShellController.init called with username: {}", username);
        this.username = username;
        if (bottomNavController != null) {
            bottomNavController.setUsername(username);
        }
        // use username locally to avoid unused-field warning (no-op here but keeps field used)
        if (username != null && username.length() > 0) {
            logger.debug("init received username length: {}", username.length());
        }
        logger.info("About to show DASHBOARD screen");
        show(SceneManager.Screen.DASHBOARD);
    }

    public void show(SceneManager.Screen screen) {
        if (contentPane == null) {
            logger.error("contentPane is null - FXML not wired correctly");
            return;
        }
        try {
            logger.info("Loading screen: {}", screen);
            Parent view = SceneManager.getInstance().loadScreenRoot(screen);
            logger.info("Loaded view for {}: {} (type: {})", screen, view, view != null ? view.getClass().getSimpleName() : "null");
            if (view == null) {
                logger.error("Loaded view is null for screen: {}", screen);
                return;
            }

            // Ensure CSS is loaded and applied to the view
            ensureCSSLoaded(view);

            // Check view dimensions
            logger.info("View dimensions - prefWidth: {}, prefHeight: {}, minWidth: {}, minHeight: {}",
                view.prefWidth(-1), view.prefHeight(-1), view.minWidth(-1), view.minHeight(-1));
            logger.info("View visibility: visible={}, managed={}", view.isVisible(), view.isManaged());
            logger.info("View opacity: {}", view.getOpacity());

            // Check contentPane dimensions
            logger.info("ContentPane dimensions - width: {}, height: {}, prefWidth: {}, prefHeight: {}",
                contentPane.getWidth(), contentPane.getHeight(),
                contentPane.getPrefWidth(), contentPane.getPrefHeight());

            // Ensure view fills the StackPane
            javafx.scene.layout.StackPane.setAlignment(view, javafx.geometry.Pos.TOP_LEFT);

            // Force view to be visible and have proper size
            view.setVisible(true);
            view.setManaged(true);
            if (view instanceof javafx.scene.layout.Region region) {
                region.setPrefSize(360, 560);
                region.setMinSize(360, 560);
                logger.info("Set view preferred and min size to 360x560");
                // Prevent view from pushing layout beyond preferred
                region.setMaxSize(javafx.scene.layout.Region.USE_PREF_SIZE, javafx.scene.layout.Region.USE_PREF_SIZE);
            }

            // Set view to invisible temporarily while CSS is being applied
            view.setOpacity(0);

            contentPane.getChildren().setAll(view);
            logger.info("contentPane children count after setAll: {}", contentPane.getChildren().size());

            // Ensure bottom nav stays on top of the content
            if (bottomNav != null) {
                // also reattach to bottom to make sure layout places it correctly
                if (root != null && root.getBottom() != bottomNav) root.setBottom(bottomNav);
                bottomNav.toFront();
                logger.info("Called bottomNav.toFront() after setting the view");
            }

            // Check if view was actually added
            if (!contentPane.getChildren().isEmpty()) {
                javafx.scene.Node firstChild = contentPane.getChildren().get(0);
                logger.info("First child in contentPane: {}", firstChild);
                logger.info("First child visibility: visible={}, managed={}",
                    firstChild.isVisible(), firstChild.isManaged());
            }

            // Force immediate layout pass to apply CSS
            view.applyCss();
            view.layout();
            contentPane.applyCss();
            contentPane.layout();
            logger.info("Applied CSS and forced layout on view and contentPane");

            // Force layout
            contentPane.requestLayout();
            view.requestLayout();
            logger.info("Requested layout update on contentPane and view");

            // Schedule bounds check and fade-in after layout and CSS application
            javafx.application.Platform.runLater(() -> {
                // Ensure CSS is fully applied with another pass
                view.applyCss();
                view.layout();

                logger.info("After layout - View bounds: {}", view.getLayoutBounds());
                logger.info("After layout - View in parent: {}", view.getBoundsInParent());
                logger.info("After layout - ContentPane bounds: {}", contentPane.getLayoutBounds());

                // Fade in the view now that CSS is applied
                view.setOpacity(1);

                if (bottomNav != null) {
                    bottomNav.toFront();
                }
                // Signal first screen readiness once
                if (!firstReadySignalled) {
                    firstReadySignalled = true;
                    firstScreenReady.complete(null);
                }
            });

            logger.info("Shell switched to screen: {}", screen);
        } catch (Exception e) {
            logger.error("Failed to load screen " + screen, e);
            if (!firstScreenReady.isDone()) firstScreenReady.completeExceptionally(e);
        }
    }

    /**
     * Ensure CSS stylesheets are loaded and applied to the view
     */
    private void ensureCSSLoaded(Parent view) {
        if (view == null || view.getScene() == null) {
            logger.debug("View or scene is null, CSS will be applied when view is added to scene");
            return;
        }

        javafx.scene.Scene scene = view.getScene();
        if (scene != null) {
            // Check if stylesheets are already loaded
            java.util.List<String> stylesheets = scene.getStylesheets();
            logger.info("Scene has {} stylesheets loaded", stylesheets.size());

            // Ensure stylesheets are applied
            if (!stylesheets.isEmpty()) {
                view.applyCss();
                logger.info("Applied {} CSS stylesheets to view", stylesheets.size());
            }
        }
    }
}
