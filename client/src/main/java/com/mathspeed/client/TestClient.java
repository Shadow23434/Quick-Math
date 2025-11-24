package com.mathspeed.client;

import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.function.Consumer;
import java.lang.reflect.Method;

import com.mathspeed.controller.GameplayController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Enhanced test client with JavaFX integration for gameplay testing
 *
 * Changes in this version:
 * - No longer calls missing GameplayController methods (initializeWithCustomClient / handleExternalMessage).
 * - When switching to gameplay UI, the TestGameplayClientWrapper is created and assigned to the controller via
 *   controller.setGameClient(...).
 * - Incoming server messages are forwarded to the controller by invoking either
 *   handleExternalMessage(String) or handleServerMessage(String) (if present) via reflection. The call is scheduled
 *   on the JavaFX Application Thread using Platform.runLater.
 * - When MATCH_START_INFO arrives, the reader thread schedules the UI switch and then waits briefly for the
 *   controller to be ready; once ready, the MATCH_START_INFO payload is forwarded to the controller via reflection.
 */
public class TestClient extends Application {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8888;

    private static Socket socket;
    private static BufferedReader in;
    private static PrintWriter out;
    private static Stage primaryStage;
    private static GameplayController gameController;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        primaryStage.setTitle("Math Speed Test Client");
        primaryStage.setOnCloseRequest(e -> cleanup());

        // Show console first
        showConsoleMode();
    }

    private void showConsoleMode() {
        System.out.println("===========================================");
        System.out.println("   Math Speed Server - Test Client");
        System.out.println("===========================================\n");

        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            System.out.println("✅ Connected to server at " + SERVER_HOST + ":" + SERVER_PORT);
            System.out.println("Connection: " + socket.getLocalSocketAddress() + " -> " + socket.getRemoteSocketAddress());

            // Thread để đọc responses từ server
            Thread readerThread = new Thread(() -> {
                try {
                    String response;
                    while ((response = in.readLine()) != null) {
                        System.out.println("\n<<< SERVER: " + response);

                        final String finalResponse = response;

                        // If gameplay UI is already active, deliver message to controller via reflection
                        if (gameController != null) {
                            deliverToController(finalResponse);
                        }

                        // Check for MATCH_START_INFO to switch to gameplay if not already switched
                        if (response.contains("\"type\":\"MATCH_START_INFO\"")) {
                            // Schedule UI switch on JavaFX thread
                            Platform.runLater(() -> {
                                try {
                                    switchToGameplay();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });

                            // Wait briefly for the gameplay UI/controller to initialize, then forward the original payload.
                            boolean delivered = false;
                            try {
                                int attempts = 0;
                                int maxAttempts = 40; // wait up to ~2 seconds (40 * 50ms)
                                while (attempts++ < maxAttempts && gameController == null) {
                                    Thread.sleep(50);
                                }
                                if (gameController != null) {
                                    // deliver payload to controller on FX thread
                                    final String payloadToDeliver = finalResponse;
                                    deliverToController(payloadToDeliver);
                                    delivered = true;
                                }
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                            if (!delivered) {
                                System.err.println("Warning: could not deliver initial MATCH_START_INFO to GameplayController (controller not ready).");
                            }
                        }

                        System.out.print(">>> ");
                        System.out.flush();
                    }
                } catch (IOException e) {
                    if (!socket.isClosed()) {
                        System.out.println("\n⚠️  Connection closed by server.");
                    }
                }
            });
            readerThread.setDaemon(true);
            readerThread.start();

            // Main thread gửi commands
            printHelp();

            // Console input thread
            Thread consoleThread = new Thread(() -> {
                try (Scanner scanner = new Scanner(System.in)) {
                    String command;
                    while (true) {
                        System.out.print(">>> ");
                        command = scanner.nextLine().trim();

                        if (command.isEmpty()) {
                            continue;
                        }

                        if (command.equalsIgnoreCase("quit") || command.equalsIgnoreCase("exit")) {
                            System.out.println("👋 Disconnecting...");
                            cleanup();
                            Platform.exit();
                            break;
                        }

                        if (command.equalsIgnoreCase("help")) {
                            printHelp();
                            continue;
                        }

                        if (command.equalsIgnoreCase("clear")) {
                            clearScreen();
                            continue;
                        }

                        // Gửi command tới server
                        out.println(command);

                        // Small delay để đợi response
                        Thread.sleep(100);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            consoleThread.setDaemon(true);
            consoleThread.start();

        } catch (UnknownHostException e) {
            System.err.println("❌ Unknown host: " + SERVER_HOST);
        } catch (ConnectException e) {
            System.err.println("❌ Connection refused to " + SERVER_HOST + ":" + SERVER_PORT);
        } catch (IOException e) {
            System.err.println("❌ I/O error: " + e.getMessage());
        }
    }

    private void switchToGameplay() throws Exception {
        System.out.println("\n🎮 Switching to gameplay interface...");

        // Load gameplay.fxml
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/gameplay.fxml"));
        Scene gameplayScene = new Scene(loader.load(), 800, 620);
        gameplayScene.getStylesheets().add(getClass().getResource("/css/gameplay.css").toExternalForm());

        // Get controller and setup connection
        gameController = loader.getController();
        gameController.setPlayerUsername("TestPlayer");

        // Create a compatible GameplayClient wrapper
        TestGameplayClientWrapper testClient = new TestGameplayClientWrapper();

        // Provide the wrapper to controller so controller can use it for outgoing messages
        try {
            gameController.setGameClient(testClient); // set the client reference on controller
        } catch (Exception ex) {
            System.err.println("Warning: controller.setGameClient failed: " + ex.getMessage());
        }

        primaryStage.setScene(gameplayScene);
        primaryStage.show();
        primaryStage.toFront();
    }

    private static void printHelp() {
        System.out.println("\n📝 Available Commands:");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  Test Game Flow:");
        System.out.println("    REGISTER test pass123           - Register test account");
        System.out.println("    LOGIN test pass123              - Login");
        System.out.println("    JOIN_QUEUE                       - Join queue (auto-match)");
        System.out.println("    CHALLENGE test 5                 - Self-challenge for testing");
        System.out.println();
        System.out.println("  Connection & Info:");
        System.out.println("    PING                            - Test connection");
        System.out.println("    TIME_PING <timestamp>           - Test time sync");
        System.out.println("    help                            - Show this help");
        System.out.println("    clear                           - Clear screen");
        System.out.println("    quit / exit                     - Disconnect");
        System.out.println();
        System.out.println("  Authentication:");
        System.out.println("    REGISTER <user> <pass> [gender] - Register (gender: male/female/other)");
        System.out.println("    LOGIN <user> <pass>             - Login to account");
        System.out.println();
        System.out.println("  Matchmaking:");
        System.out.println("    JOIN_QUEUE                      - Join matchmaking queue");
        System.out.println("    LEAVE_QUEUE                     - Leave queue");
        System.out.println();
        System.out.println("  Challenge:");
        System.out.println("    CHALLENGE <target> [rounds]     - Challenge player (default: 10 rounds)");
        System.out.println("    ACCEPT <challenger>             - Accept challenge");
        System.out.println("    DECLINE <challenger>            - Decline challenge");
        System.out.println();
        System.out.println("  In-Game:");
        System.out.println("    READY                           - Mark ready for game start");
        System.out.println("    ANSWER <expression>             - Submit answer (e.g., ANSWER 1+2*3)");
        System.out.println("    FORFEIT                         - Forfeit current game");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();
        System.out.println("💡 Quick Test Flow:");
        System.out.println("   1. REGISTER test pass123");
        System.out.println("   2. LOGIN test pass123");
        System.out.println("   3. CHALLENGE test 3");
        System.out.println("   4. Wait for MATCH_START_INFO -> Auto switch to gameplay");
        System.out.println("   5. Game UI will auto-sync time and start countdown");
        System.out.println();
    }

    private static void clearScreen() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            for (int i = 0; i < 50; i++) {
                System.out.println();
            }
        }
    }

    private static void cleanup() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            // ignore
        }
        System.out.println("✅ Disconnected from server.");
    }

    /**
     * Deliver incoming server message to controller using reflection to call
     * either handleExternalMessage(String) or handleServerMessage(String).
     */
    private static void deliverToController(String message) {
        if (gameController == null) return;
        Platform.runLater(() -> {
            try {
                Method m = null;
                try {
                    m = gameController.getClass().getMethod("handleExternalMessage", String.class);
                } catch (NoSuchMethodException ignored) {
                    // try private method name used in some implementations
                    try {
                        m = gameController.getClass().getDeclaredMethod("handleServerMessage", String.class);
                        m.setAccessible(true);
                    } catch (NoSuchMethodException ignored2) {
                        m = null;
                    }
                }
                if (m != null) {
                    m.invoke(gameController, message);
                } else {
                    // last resort: try to find any public method named "handleMessage" (unlikely)
                    try {
                        Method m2 = gameController.getClass().getMethod("handleMessage", String.class);
                        m2.invoke(gameController, message);
                    } catch (NoSuchMethodException ex) {
                        System.err.println("No suitable message handler found on GameplayController.");
                    }
                }
            } catch (Exception ex) {
                System.err.println("Failed to deliver message to controller: " + ex.getMessage());
                ex.printStackTrace();
            }
        });
    }

    /**
     * Wrapper class that extends GameplayClient for compatibility
     *
     * This wrapper uses the TestClient socket/out to send messages to the server and
     * provides basic overrides expected by the controller.
     */
    private class TestGameplayClientWrapper extends GameplayClient {

        public TestGameplayClientWrapper() {
            // We don't provide a typed onMatchStart handler here; reader thread will deliver that payload.
            super("localhost", 8888, message -> {
                // This onMessage consumer is not used because TestClient reader thread receives raw lines
                // and forwards them to the controller directly. We still print for debugging.
                System.out.println("TestClient wrapper received: " + message);
            });
        }

        @Override
        public boolean isConnected() {
            return socket != null && socket.isConnected() && !socket.isClosed();
        }

        @Override
        public void sendRaw(String message) {
            if (out != null) {
                out.println(message);
                System.out.println(">>> GAME: " + message);
            } else {
                System.err.println("❌ Cannot send: No connection");
            }
        }

        @Override
        public void sendSubmitAnswer(String expression) {
            sendRaw("ANSWER " + expression);
        }

        @Override
        public void disconnect() {
            cleanup();
        }

        @Override
        public void connect() throws IOException {
            // Already connected via TestClient socket
            System.out.println("✅ Using existing TestClient connection for gameplay");
        }

        // Add custom methods for game functionality (not @Override)
        public void sendTimeSync() {
            long timestamp = System.currentTimeMillis();
            sendRaw("TIME_PING " + timestamp);
        }

        public void sendReady() {
            sendRaw("READY");
        }

        public void sendForfeit() {
            sendRaw("FORFEIT");
        }
    }
}