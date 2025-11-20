package com.mathspeed.client;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Rectangle2D;
import javafx.scene.layout.Region;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Utility helpers for applying adaptive width based on WindowConfig.
 */
public final class UiUtils {
    private UiUtils() { /* utility */ }

    /**
     * Initialize the given stage's width and minWidth based on WindowConfig and the screen size.
     */
    public static void initStageSizing(Stage stage) {
        if (stage == null) return;

        // Determine the screen where the stage is expected to show. Use primary screen as fallback.
        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        try {
            // If stage positioned already, try to get the screen under the stage
            if (stage.getX() != 0 || stage.getY() != 0) {
                var screens = Screen.getScreensForRectangle(stage.getX(), stage.getY(), 1, 1);
                if (!screens.isEmpty()) {
                    visualBounds = screens.get(0).getVisualBounds();
                }
            }
        } catch (Exception ignored) { }

        double screenWidth = visualBounds.getWidth();

        // Choose target width depending on screenWidth
        double targetWidth;
        if (screenWidth >= WindowConfig.DESKTOP_WIDTH) targetWidth = WindowConfig.DESKTOP_WIDTH;
        else if (screenWidth >= WindowConfig.DEFAULT_WIDTH) targetWidth = WindowConfig.DEFAULT_WIDTH;
        else targetWidth = WindowConfig.MIN_WIDTH;

        // Ensure minWidth doesn't exceed screen width
        double minWidth = Math.min(WindowConfig.MIN_WIDTH, screenWidth);

        stage.setMinWidth(minWidth);
        stage.setWidth(Math.min(targetWidth, screenWidth));

        // Optional: when stage is later shown, if it's larger than screen, clamp it
        stage.xProperty().addListener((obs, o, n) -> Platform.runLater(() -> clampToCurrentScreen(stage)));
        stage.yProperty().addListener((obs, o, n) -> Platform.runLater(() -> clampToCurrentScreen(stage)));
    }

    private static void clampToCurrentScreen(Stage stage) {
        try {
            var screens = Screen.getScreensForRectangle(stage.getX(), stage.getY(), 1, 1);
            Rectangle2D visualBounds = screens.isEmpty() ? Screen.getPrimary().getVisualBounds() : screens.get(0).getVisualBounds();
            double screenWidth = visualBounds.getWidth();
            if (stage.getWidth() > screenWidth) {
                stage.setWidth(Math.max(WindowConfig.MIN_WIDTH, Math.min(WindowConfig.DESKTOP_WIDTH, screenWidth)));
            }
            if (stage.getMinWidth() > screenWidth) {
                stage.setMinWidth(Math.max(100, screenWidth));
            }
        } catch (Exception ignored) { }
    }

    /**
     * Bind a Region's maxWidth to the stage width but clamped to a chosen targetWidth.
     * Use Platform.runLater if called during controller initialize where scene/window may not be ready.
     */
    public static void applyAdaptiveWidth(Region rootPane, Stage stage, double targetWidth) {
        if (rootPane == null || stage == null) return;
        try {
            // Unbind any previous binding to avoid 'A bound value cannot be set' when we set new binding later
            rootPane.maxWidthProperty().unbind();
        } catch (Exception ignored) { }

        rootPane.maxWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.min(stage.getWidth(), targetWidth),
                stage.widthProperty()
        ));
    }

    /**
     * Convenience: choose a target width based on current primary screen and WindowConfig.
     */
    public static double chooseTargetWidthForPrimary() {
        double screenWidth = Screen.getPrimary().getVisualBounds().getWidth();
        if (screenWidth >= WindowConfig.DESKTOP_WIDTH) return WindowConfig.DESKTOP_WIDTH;
        if (screenWidth >= WindowConfig.DEFAULT_WIDTH) return WindowConfig.DEFAULT_WIDTH;
        return WindowConfig.MIN_WIDTH;
    }
}
