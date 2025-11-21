package com.mathspeed.common;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.util.Duration;

public class SplashSpinner extends Region {
    private final Canvas canvas;
    private double angle = 0;
    private final Timeline timeline;

    public SplashSpinner(double size, Color color, double strokeWidth) {
        this.setPrefSize(size, size);
        this.setMinSize(size, size);
        this.setMaxSize(size, size);
        canvas = new Canvas(size, size);
        getChildren().add(canvas);
        timeline = new Timeline(new KeyFrame(Duration.millis(16), e -> {
            angle -= 6;
            if (angle <= -360) angle += 360;
            drawSpinner(color, strokeWidth);
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
        drawSpinner(color, strokeWidth);
    }

    private void drawSpinner(Color color, double strokeWidth) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double size = canvas.getWidth();
        gc.clearRect(0, 0, size, size);
        gc.setStroke(color);
        gc.setLineWidth(strokeWidth);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.strokeArc(strokeWidth, strokeWidth, size - 2 * strokeWidth, size - 2 * strokeWidth, angle, 270, javafx.scene.shape.ArcType.OPEN);
    }
}
