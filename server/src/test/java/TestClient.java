import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * Simple test client for Math Speed Server
 *
 * Usage:
 *   javac TestClient.java
 *   java TestClient
 *
 * Commands:
 *   PING                           - Test connection
 *   REGISTER <user> <pass> [gender] - Register new account
 *   LOGIN <user> <pass>            - Login
 *   JOIN_QUEUE                     - Join matchmaking queue
 *   LEAVE_QUEUE                    - Leave queue
 *   CHALLENGE <target> [rounds]    - Challenge another player
 *   ACCEPT <challenger>            - Accept challenge
 *   DECLINE <challenger>           - Decline challenge
 *   quit                           - Exit client
 */
public class TestClient {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8888;

    public static void main(String[] args) {
        System.out.println("===========================================");
        System.out.println("   Math Speed Server - Test Client");
        System.out.println("===========================================\n");

        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Connected to server at " + SERVER_HOST + ":" + SERVER_PORT);
            System.out.println("Connection: " + socket.getLocalSocketAddress() + " -> " + socket.getRemoteSocketAddress());

            // Thread để đọc responses từ server
            Thread readerThread = new Thread(() -> {
                try {
                    String response;
                    while ((response = in.readLine()) != null) {
                        System.out.println("\n<<< SERVER: " + response);
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

            String command;
            while (true) {
                System.out.print(">>> ");
                command = scanner.nextLine().trim();

                if (command.isEmpty()) {
                    continue;
                }

                if (command.equalsIgnoreCase("quit") || command.equalsIgnoreCase("exit")) {
                    System.out.println("👋 Disconnecting...");
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

            System.out.println("Disconnected from server.");

        } catch (UnknownHostException e) {
            System.err.println("Unknown host: " + SERVER_HOST);
            System.err.println("   Make sure server hostname is correct.");
        } catch (ConnectException e) {
            System.err.println("Connection refused to " + SERVER_HOST + ":" + SERVER_PORT);
            System.err.println("   Make sure server is running on port " + SERVER_PORT);
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("Interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    private static void printHelp() {
        System.out.println("\n📝 Available Commands:");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  Connection & Info:");
        System.out.println("    PING                          - Test connection");
        System.out.println("    help                          - Show this help");
        System.out.println("    clear                         - Clear screen");
        System.out.println("    quit / exit                   - Disconnect");
        System.out.println();
        System.out.println("  Authentication:");
        System.out.println("    REGISTER <user> <pass> [gender] - Register (gender: male/female/other)");
        System.out.println("    LOGIN <user> <pass>           - Login to account");
        System.out.println();
        System.out.println("  Matchmaking:");
        System.out.println("    JOIN_QUEUE                    - Join matchmaking queue");
        System.out.println("    LEAVE_QUEUE                   - Leave queue");
        System.out.println();
        System.out.println("  Challenge:");
        System.out.println("    CHALLENGE <target> [rounds]   - Challenge player (default: 10 rounds)");
        System.out.println("    ACCEPT <challenger>           - Accept challenge");
        System.out.println("    DECLINE <challenger>          - Decline challenge");
        System.out.println();
        System.out.println("  In-Game:");
        System.out.println("    READY                         - Mark ready for game start");
        System.out.println("    ANSWER <expression>           - Submit answer (e.g., ANSWER 1+2*3)");
        System.out.println("    FORFEIT                       - Forfeit current game");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();
        System.out.println("💡 Examples:");
        System.out.println("   REGISTER alice pass123 female");
        System.out.println("   LOGIN alice pass123");
        System.out.println("   CHALLENGE bob 5");
        System.out.println("   ANSWER 9*8+7-6");
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
            // Fallback: print many newlines
            for (int i = 0; i < 50; i++) {
                System.out.println();
            }
        }
    }
}

