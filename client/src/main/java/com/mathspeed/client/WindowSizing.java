package com.mathspeed.client;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class WindowSizing {
    private WindowSizing() {}

    private static volatile boolean globalDesktopMode = true; // default: desktop

    public static void setGlobalDesktopMode(boolean desktop) {
        globalDesktopMode = desktop;
    }

    public static boolean isGlobalDesktopMode() {
        return globalDesktopMode;
    }

    public static void toggleGlobalAndApply(Node node) {
        setGlobalDesktopMode(!isGlobalDesktopMode());
        applyToNode(node, isGlobalDesktopMode());
    }

    public static void applyToNode(Node node) {
        applyToNode(node, isGlobalDesktopMode());
    }

    public static void apply(Parent root, boolean desktop) {
        if (root == null) return;
        if (root.getScene() != null) {
            applyToScene(root.getScene(), desktop);
        } else {
            ChangeListener<Scene> listener = (obs, oldS, newS) -> {
                if (newS != null) {
                    applyToScene(newS, desktop);
                }
            };
            root.sceneProperty().addListener(listener);
        }
    }

    public static void applyToNode(Node node, boolean desktop) {
        if (node == null) return;
        if (node.getScene() != null) {
            applyToScene(node.getScene(), desktop);
        } else {
            ChangeListener<Scene> listener = (obs, oldS, newS) -> {
                if (newS != null) applyToScene(newS, desktop);
            };
            node.sceneProperty().addListener(listener);
        }
    }

    private static void applyToScene(Scene scene, boolean desktop) {
        if (scene == null) return;

        // Stage may be available now or may become available later
        if (scene.getWindow() instanceof Stage) {
            setSizes((Stage) scene.getWindow(), scene, desktop);
        } else {
            // wait for window
            ChangeListener<javafx.stage.Window> wListener = (obs, oldW, newW) -> {
                if (newW instanceof Stage) setSizes((Stage) newW, scene, desktop);
            };
            scene.windowProperty().addListener(wListener);
        }
    }

    private static void setSizes(Stage stage, Scene scene, boolean desktop) {
        if (stage == null || scene == null) return;
        Platform.runLater(() -> {
            // Enforce minimums
            stage.setMinWidth(WindowConfig.MIN_WIDTH);
            stage.setMinHeight(WindowConfig.MIN_HEIGHT);

            double targetW = desktop ? WindowConfig.DESKTOP_WIDTH : WindowConfig.DEFAULT_WIDTH;
            double targetH = desktop ? WindowConfig.DESKTOP_HEIGHT : WindowConfig.DEFAULT_HEIGHT;

            // Set stage and scene sizes
            stage.setWidth(targetW);
            stage.setHeight(targetH);

            // If root is a Region, set its preferred size so layouts can adapt
            Parent root = scene.getRoot();
            if (root instanceof javafx.scene.layout.Region) {
                javafx.scene.layout.Region r = (javafx.scene.layout.Region) root;
                r.setPrefWidth(targetW);
                r.setPrefHeight(targetH);
            }
        });
    }
}
