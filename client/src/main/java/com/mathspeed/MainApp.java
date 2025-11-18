package com.mathspeed;

import com.mathspeed.controller.GameController;
import com.mathspeed.controller.MatchResultController;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.Arrays;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        // đổi đường dẫn nếu bạn muốn test gameplay.fxml thay vì matchresult.fxml
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/gameplay.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root);
        stage.setTitle("MathSpeed - UI Test");
        stage.setScene(scene);
        stage.setWidth(900);
        stage.setHeight(600);
        stage.show();

        // Lấy controller và xử lý tùy loại controller trả về
        Object controller = loader.getController();

        if (controller instanceof MatchResultController) {
            MatchResultController rc = (MatchResultController) controller;
            // Demo: hiển thị overlay kết quả ngay (vì root mặc định visible=false)
            rc.show("Bạn", 12, 52.4, "Đối thủ", 9, 60.1);
            // optional: đóng overlay sau 4s để demo hide()
            Timeline tHide = new Timeline(new KeyFrame(Duration.seconds(4), e -> rc.hide()));
            tHide.play();
        } else if (controller instanceof GameController) {
            GameController gc = (GameController) controller;
            // populate sample numbers and start countdown 5s
            gc.populateServerNumbers(Arrays.asList(3, 8, 5, 2, 9), selected -> {
                System.out.println("Selected numbers: " + selected);
            });

            // start countdown -> when finished user can interact
            gc.startCountdown(5, () -> System.out.println("Countdown finished, inputs enabled"));

            // Show a sample round result 8 seconds later (so it appears after round)
            Timeline t = new Timeline(new KeyFrame(Duration.seconds(8), e -> {
                gc.showRoundResult(+1, -1, 3, 1, 3500); // visible 3.5s
            }));
            t.play();
        } else {
            System.out.println("Loaded controller is of type: " + (controller != null ? controller.getClass().getName() : "null"));
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}