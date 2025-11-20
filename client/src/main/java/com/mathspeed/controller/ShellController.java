package com.mathspeed.controller;

import com.mathspeed.client.UiUtils;
import com.mathspeed.client.WindowConfig;
import com.mathspeed.client.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.scene.image.WritableImage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ShellController {
    private static final Logger logger = LoggerFactory.getLogger(ShellController.class);

    @FXML private BorderPane root;
    @FXML private StackPane contentPane;
    @FXML private StackPane bottomNav;
    @Getter
    @FXML private BottomNavController bottomNavController;

    private String username;

    private final CompletableFuture<Void> firstScreenReady = new CompletableFuture<>();
    private boolean firstReadySignalled = false;

    // Timeout scheduler for readiness (to avoid overlay hanging indefinitely)
    private ScheduledExecutorService readyTimeoutScheduler;
    private ScheduledFuture<?> readyTimeoutFuture;
    private static final long READY_TIMEOUT_MS = 10_000L; // 10 seconds

    public CompletableFuture<Void> getFirstScreenReady() { return firstScreenReady; }

    @FXML
    private void initialize() {
        if (root != null && bottomNav != null && root.getBottom() != bottomNav) {
            root.setBottom(bottomNav);
        }

        if (bottomNav != null) {
            bottomNav.setVisible(true);
            bottomNav.setManaged(true);
            bottomNav.setOpacity(1.0);
            bottomNav.setTranslateX(0);
            bottomNav.setTranslateY(0);
            bottomNav.toFront();
        }
    }

    public void init(String username) {
        this.username = username;
        if (bottomNavController != null) {
            bottomNavController.setUsername(username);
        }
        show(SceneManager.Screen.DASHBOARD);
    }

    public void show(SceneManager.Screen screen) {
        if (contentPane == null) {
            logger.error("contentPane is null - FXML not wired correctly");
            return;
        }
        try {
            Parent view = SceneManager.getInstance().loadScreenRoot(screen);
            if (view == null) {
                logger.error("Loaded view is null for screen: {}", screen);
                return;
            }
            ensureCSSLoaded(view);
            javafx.scene.layout.StackPane.setAlignment(view, javafx.geometry.Pos.TOP_LEFT);

            // Force view to be visible and have proper size
            view.setVisible(true);
            view.setManaged(true);
            if (view instanceof javafx.scene.layout.Region region) {
                // If any size properties were previously bound, unbind them so we can set explicit sizes.
                try {
                    region.maxWidthProperty().unbind();
                    region.maxHeightProperty().unbind();
                    region.prefWidthProperty().unbind();
                    region.prefHeightProperty().unbind();
                    region.minWidthProperty().unbind();
                    region.minHeightProperty().unbind();
                } catch (Exception ignored) {}

                region.setPrefSize(360, 560);
                region.setMinSize(360, 560);
                region.setMaxSize(javafx.scene.layout.Region.USE_PREF_SIZE, javafx.scene.layout.Region.USE_PREF_SIZE);
            }
            view.setOpacity(0);
            contentPane.getChildren().setAll(view);

            // Ensure bottom nav stays on top of the content
            if (bottomNav != null) {
                // also reattach to bottom to make sure layout places it correctly
                if (root != null && root.getBottom() != bottomNav) root.setBottom(bottomNav);
                bottomNav.toFront();
            }

            // Force immediate layout pass to apply CSS
            view.applyCss();
            view.layout();
            contentPane.applyCss();
            contentPane.layout();

            contentPane.requestLayout();
            view.requestLayout();

            // Schedule bounds check, snapshot + fade-in after layout and CSS application
            Platform.runLater(() -> {
                try {
                    view.applyCss();
                    view.layout();
                    view.setOpacity(1);

                    // Apply adaptive width binding so page respects WindowConfig sizes
                    try {
                        javafx.stage.Window window = view.getScene() != null ? view.getScene().getWindow() : null;
                        if (view instanceof javafx.scene.layout.Region region && window instanceof javafx.stage.Stage stage) {
                            double chosenTarget = screenWidthBasedTarget();
                            UiUtils.applyAdaptiveWidth(region, stage, chosenTarget);
                        }
                    } catch (Exception ignored) { }

                    if (bottomNav != null) {
                        bottomNav.toFront();
                    }

                    // Start timeout watcher
                    startReadyTimeoutWatcher();

                    // Attempt snapshot to ensure the view has been rendered at least once. If snapshot succeeds, complete the future.
                    attemptSnapshotAndComplete(view);
                } catch (Exception e) {
                    logger.warn("Exception during post-layout handling", e);
                    if (!firstScreenReady.isDone()) firstScreenReady.completeExceptionally(e);
                    cancelReadyTimeoutWatcher();
                }
            });
        } catch (Exception e) {
            logger.error("Failed to load screen " + screen, e);
            if (!firstScreenReady.isDone()) firstScreenReady.completeExceptionally(e);
            cancelReadyTimeoutWatcher();
        }
    }

    private double screenWidthBasedTarget() {
        double screenWidth = javafx.stage.Screen.getPrimary().getVisualBounds().getWidth();
        if (screenWidth >= WindowConfig.DESKTOP_WIDTH) return WindowConfig.DESKTOP_WIDTH;
        if (screenWidth >= WindowConfig.DEFAULT_WIDTH) return WindowConfig.DEFAULT_WIDTH;
        return WindowConfig.MIN_WIDTH;
    }

    private void ensureCSSLoaded(Parent view) {
        if (view == null || view.getScene() == null) {
            logger.debug("View or scene is null, CSS will be applied when view is added to scene");
            return;
        }

        javafx.scene.Scene scene = view.getScene();
        if (scene != null) {
            java.util.List<String> stylesheets = scene.getStylesheets();
            if (!stylesheets.isEmpty()) {
                view.applyCss();
            }
        }
    }

    // Snapshot-based readiness helper
    private void attemptSnapshotAndComplete(Parent view) {
        try {
            double w = Math.max(1, Math.ceil(view.getBoundsInParent().getWidth()));
            double h = Math.max(1, Math.ceil(view.getBoundsInParent().getHeight()));
            if (w <= 0 || h <= 0) {
                // If bounds are zero, schedule a short re-try on the FX thread
                Platform.runLater(() -> {
                    try {
                        double rw = Math.max(1, Math.ceil(view.getBoundsInParent().getWidth()));
                        double rh = Math.max(1, Math.ceil(view.getBoundsInParent().getHeight()));
                        WritableImage wi = new WritableImage((int)rw, (int)rh);
                        // Use synchronous snapshot overload that accepts SnapshotParameters
                        view.snapshot(new javafx.scene.SnapshotParameters(), wi);
                        completeFirstReady();
                    } catch (Exception e) {
                        logger.warn("Snapshot retry failed", e);
                        completeFirstReady();
                    }
                });
                return;
            }

            WritableImage wi = new WritableImage((int)w, (int)h);
            // Use synchronous snapshot overload that accepts SnapshotParameters
            view.snapshot(new javafx.scene.SnapshotParameters(), wi);
            completeFirstReady();
        } catch (Exception e) {
            logger.warn("Snapshot failed, completing ready future as fallback", e);
            completeFirstReady();
        }
    }

    private synchronized void completeFirstReady() {
        if (!firstReadySignalled) {
            firstReadySignalled = true;
            try {
                firstScreenReady.complete(null);
            } catch (Exception e) {
                logger.debug("Exception while completing firstScreenReady", e);
            }
        }
        cancelReadyTimeoutWatcher();
    }

    private void startReadyTimeoutWatcher() {
        try {
            cancelReadyTimeoutWatcher();
            readyTimeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "shell-ready-timeout");
                t.setDaemon(true);
                return t;
            });
            readyTimeoutFuture = readyTimeoutScheduler.schedule(() -> {
                if (!firstScreenReady.isDone()) {
                    firstScreenReady.completeExceptionally(new TimeoutException("Shell first-screen readiness timeout after " + READY_TIMEOUT_MS + "ms"));
                }
            }, READY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.warn("Failed to start ready timeout watcher", e);
        }
    }

    private void cancelReadyTimeoutWatcher() {
        try {
            if (readyTimeoutFuture != null) readyTimeoutFuture.cancel(true);
            if (readyTimeoutScheduler != null) {
                readyTimeoutScheduler.shutdownNow();
                readyTimeoutScheduler = null;
            }
        } catch (Exception ignored) {}
    }
}
