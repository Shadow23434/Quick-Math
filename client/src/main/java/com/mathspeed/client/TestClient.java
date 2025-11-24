package com.mathspeed.client;

import com.mathspeed.controller.GameplayController;
import com.mathspeed.model.*;
import com.mathspeed.network.NetworkGameplay;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mathspeed.util.GsonJavaTime;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.*;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

public class TestClient extends Application {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8888;

    private static Socket socket;
    private static BufferedReader in;
    private static PrintWriter out;
    private static GameplayController gameController;
    private static Stage primaryStage;
    private final Gson gson = GsonJavaTime.create();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        primaryStage.setTitle("Math Speed Test Client");
        primaryStage.setOnCloseRequest(e -> cleanup());
        // Start console and socket in background
        new Thread(this::runConsoleAndSocket, "TestClient-Main").start();
    }

    private void runConsoleAndSocket() {
        System.out.println("TestClient: connecting to " + SERVER_HOST + ":" + SERVER_PORT);
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            System.out.println("Connected to server.");

            // reader thread
            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        final String l = line;
                        System.out.println("\n<<< SERVER: " + l);
                        // deliver to controller if UI active
                        if (gameController != null) {
                            deliverToController(l);
                        } else {
                            // auto switch to gameplay UI when MATCH_START_INFO appears
                            if (l.contains("\"type\":\"MATCH_START_INFO\"") || l.contains("\"type\": \"MATCH_START_INFO\"")) {
                                Platform.runLater(() -> {
                                    try {
                                        switchToGameplay();
                                    } catch (Exception ex) {
                                        ex.printStackTrace();
                                    }
                                });
                                // give UI some time then deliver
                                int tries = 0;
                                while (gameController == null && tries++ < 40) {
                                    Thread.sleep(50);
                                }
                                if (gameController != null) deliverToController(l);
                            }
                        }
                        System.out.print(">>> ");
                        System.out.flush();
                    }
                } catch (Exception e) {
                    System.out.println("Reader thread ended: " + e.getMessage());
                }
            }, "TestClient-Reader");
            reader.setDaemon(true);
            reader.start();

            // console input loop
            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    System.out.print(">>> ");
                    String cmd = scanner.nextLine();
                    if (cmd == null) break;
                    cmd = cmd.trim();
                    if (cmd.equalsIgnoreCase("quit") || cmd.equalsIgnoreCase("exit")) {
                        cleanup();
                        Platform.exit();
                        break;
                    }
                    if (cmd.isEmpty()) continue;
                    // send raw to server
                    out.println(cmd);
                    Thread.sleep(50);
                }
            }
        } catch (UnknownHostException e) {
            System.err.println("Unknown host");
        } catch (ConnectException e) {
            System.err.println("Connection refused - make sure server is running on port " + SERVER_PORT);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void switchToGameplay() throws Exception {
        System.out.println("Switching to gameplay UI...");
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/gameplay.fxml"));
        Scene s = new Scene(loader.load(), 800, 620);
        primaryStage.setScene(s);
        primaryStage.show();
        gameController = loader.getController();
        gameController.setPlayerUsername("TestPlayer");

        // create a wrapper client that uses this TestClient socket/out
        TestGameplayClientWrapper wrapper = new TestGameplayClientWrapper();
        // give wrapper to controller
        gameController.setGameClient(wrapper);

        System.out.println("Gameplay UI ready and wrapper client installed.");
    }

    /**
     * Deliver an incoming raw server message into the GameplayController.
     * This will parse JSON messages and invoke the appropriate handler method on the controller
     * (using reflection if methods are non-public). All invocations happen on the JavaFX thread.
     */
    private void deliverToController(String message) {
        if (gameController == null || message == null) return;
        Platform.runLater(() -> {
            try {
                System.out.println("Delivering to controller: " + message);

                // JSON message?
                String trimmed = message.trim();
                if (trimmed.startsWith("{")) {
                    JsonElement je = JsonParser.parseString(trimmed);
                    if (je != null && je.isJsonObject()) {
                        JsonObject jo = je.getAsJsonObject();
                        String type = jo.has("type") ? jo.get("type").getAsString() : "";

                        switch (type.toUpperCase()) {
                            case "MATCH_START_INFO": {
                                MatchStartInfo msi = gson.fromJson(trimmed, MatchStartInfo.class);
                                invokeControllerMethod("handleMatchStart", new Class[]{MatchStartInfo.class}, new Object[]{msi});
                                break;
                            }
                            case "NEW_ROUND": {
                                NewRound nr = gson.fromJson(trimmed, NewRound.class);
                                invokeControllerMethod("handleNewRoundWithCountdown", new Class[]{NewRound.class}, new Object[]{nr});
                                break;
                            }
                            case "ANSWER_RESULT": {
                                AnswerResult ar = gson.fromJson(trimmed, AnswerResult.class);
                                invokeControllerMethod("handleAnswerResult", new Class[]{AnswerResult.class}, new Object[]{ar});
                                break;
                            }
                            case "ROUND_RESULT": {
                                RoundResult rr = gson.fromJson(trimmed, RoundResult.class);
                                invokeControllerMethod("handleRoundResult", new Class[]{RoundResult.class}, new Object[]{rr});
                                break;
                            }
                            case "GAME_END":
                            case "GAME_OVER": {
                                RoundResult fr = gson.fromJson(trimmed, RoundResult.class);
                                invokeControllerMethod("handleGameEnd", new Class[]{RoundResult.class}, new Object[]{fr});
                                invokeControllerMethod("showMatchResult", new Class[]{String.class}, new Object[]{trimmed});
                                break;
                            }
                            default: {
                                // Unknown JSON type -> show as feedback
                                invokeControllerMethod("showTemporaryFeedback", new Class[]{String.class,int.class,String.class}, new Object[]{trimmed,3,""});
                                break;
                            }
                        }
                        return;
                    }
                }

                // non-JSON messages (pipe-delimited)
                if (trimmed.startsWith("INFO|")) {
                    String info = trimmed.substring(Math.min(trimmed.length(), 5));
                    invokeControllerMethod("showTemporaryFeedback", new Class[]{String.class,int.class,String.class}, new Object[]{info,3,""});
                } else if (trimmed.startsWith("ERROR|")) {
                    String err = trimmed.substring(Math.min(trimmed.length(), 6));
                    invokeControllerMethod("showTemporaryFeedback", new Class[]{String.class,int.class,String.class}, new Object[]{"Lỗi: " + err,5,""});
                } else if (trimmed.equals("FORFEIT_ACK")) {
                    invokeControllerMethod("showTemporaryFeedback", new Class[]{String.class,int.class,String.class}, new Object[]{"Đã thoát trận đấu",3,""});
                } else {
                    invokeControllerMethod("showTemporaryFeedback", new Class[]{String.class,int.class,String.class}, new Object[]{trimmed,2,""});
                }
            } catch (Throwable ex) {
                System.err.println("deliverToController error:");
                ex.printStackTrace();
            }
        });
    }

    /**
     * Reflection helper: try to find method (public or declared), set accessible if necessary and invoke.
     */
    private void invokeControllerMethod(String name, Class<?>[] paramTypes, Object[] args) {
        try {
            Method m;
            try {
                m = gameController.getClass().getMethod(name, paramTypes);
            } catch (NoSuchMethodException e) {
                m = gameController.getClass().getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
            }
            m.invoke(gameController, args);
        } catch (NoSuchMethodException nsme) {
            // method not found on controller - just log
            System.out.println("Controller does not implement " + name);
        } catch (Exception e) {
            System.err.println("invokeControllerMethod error for " + name + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void cleanup() {
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (in != null) in.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        System.out.println("Disconnected.");
    }

    /**
     * Wrapper that uses TestClient's socket/out for send operations.
     * It does NOT open its own socket; connect() is a no-op.
     */
    private class TestGameplayClientWrapper extends NetworkGameplay {
        public TestGameplayClientWrapper() {
            super("localhost", 8888, msg -> {
                // raw consumer - not used as we forward server lines to controller directly
                System.out.println("Wrapper onMessage: " + msg);
            });
        }

        @Override
        public void connect() { System.out.println("Wrapper connect(): using existing TestClient socket"); }

        @Override
        public boolean isConnected() {
            try {
                return socket != null && socket.isConnected() && !socket.isClosed();
            } catch (Exception ex) { return false; }
        }

        @Override
        public synchronized void sendRaw(String message) {
            if (out != null) {
                out.println(message);
                System.out.println(">>> GAME: " + message);
            } else {
                System.err.println("Wrapper sendRaw: no connection");
            }
        }

        @Override
        public void disconnect() {
            cleanup();
        }
    }
}