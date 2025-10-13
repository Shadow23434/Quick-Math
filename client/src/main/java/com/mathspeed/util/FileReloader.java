package com.mathspeed.util;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileReloader {
    private final Stage stage;
    private final Scene scene;
    private final String fxmlFileName;
    private final String cssFileName;
    private final Path watchPath;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private WatchService watchService;

    public FileReloader(Stage stage, Scene scene, String fxmlFileName, String cssFileName, Path watchPath) {
        this.stage = stage;
        this.scene = scene;
        this.fxmlFileName = fxmlFileName;
        this.cssFileName = cssFileName;
        this.watchPath = watchPath;
    }

    public void start() throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        watchPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
        executor.submit(this::watchLoop);
    }

    private void watchLoop() {
        try {
            while (true) {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        Path changed = (Path) event.context();
                        if (changed.toString().equals(fxmlFileName)) {
                            reloadFxml();
                        } else if (changed.toString().equals(cssFileName)) {
                            reloadCss();
                        }
                    }
                }
                key.reset();
            }
        } catch (InterruptedException ignored) {
        }
    }

    private void reloadFxml() {
        Platform.runLater(() -> {
            try {
                // Reload from classpath (target/classes/fxml)
                Parent root = FXMLLoader.load(getClass().getResource("/fxml/" + fxmlFileName));
                scene.setRoot(root);
                System.out.println("FXML reloaded: " + fxmlFileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void reloadCss() {
        Platform.runLater(() -> {
            try {
                scene.getStylesheets().clear();
                scene.getStylesheets().add(getClass().getResource("/css/" + cssFileName).toExternalForm());
                System.out.println("CSS reloaded: " + cssFileName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void stop() throws IOException {
        watchService.close();
        executor.shutdownNow();
    }
}

