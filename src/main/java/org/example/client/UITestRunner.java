//package org.example.client;
//
//import org.example.client.UserManagementGUI;
//
//import javax.swing.*;
//
//public class UITestRunner {
//    public static void main(String[] args) {
//        // Start GameServer first and keep it running
//        Thread serverThread = new Thread(() -> {
//            try {
//                System.out.println("Starting GameServer...");
//                org.example.server.GameServer server = new org.example.server.GameServer();
//                server.start();
//                System.out.println("GameServer started successfully");
//
//                // Keep the server thread alive
//                while (!Thread.currentThread().isInterrupted()) {
//                    Thread.sleep(1000);
//                }
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                System.out.println("Server thread interrupted");
//            } catch (Exception e) {
//                System.err.println("Server startup failed: " + e.getMessage());
//                e.printStackTrace();
//            }
//        });
//        serverThread.setDaemon(false); // Prevent JVM from exiting
//        serverThread.start();
//
//        // Wait for server to start
//        try {
//            Thread.sleep(3000);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
//
//        System.out.println("Launching clients...");
//
//        // Start first client
//        SwingUtilities.invokeLater(() -> {
//            UserManagementGUI client1 = new UserManagementGUI();
//            client1.setTitle("Player 1 - QCA Game");
//            client1.setLocation(100, 100);
//            client1.setVisible(true);
//            System.out.println("Client 1 launched");
//        });
//
//        // Start second client after a delay
//        Timer timer = new Timer(2000, e -> {
//            SwingUtilities.invokeLater(() -> {
//                UserManagementGUI client2 = new UserManagementGUI();
//                client2.setTitle("Player 2 - QCA Game");
//                client2.setLocation(900, 100);
//                client2.setVisible(true);
//                System.out.println("Client 2 launched");
//            });
//        });
//        timer.setRepeats(false);
//        timer.start();
//
//        // Add shutdown hook to keep application running
//        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//            System.out.println("Shutting down UITestRunner...");
//            serverThread.interrupt();
//        }));
//
//        // Keep main thread alive
//        try {
//            Thread.currentThread().join();
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
//    }
//}