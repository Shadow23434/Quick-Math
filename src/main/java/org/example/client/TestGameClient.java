package org.example.client;

import javax.swing.*;

public class TestGameClient {
    public static void main(String[] args) {
        System.out.println("Starting TestGameClientFixed...");

        // Tạo UI trên EDT
        SwingUtilities.invokeLater(() -> {
            try {
                System.out.println("Creating player UIs...");

                // Tạo UI cho người chơi 1
                GameClientUI player1UI = new GameClientUI("Player 1");
                player1UI.setVisible(true);
                System.out.println("Player 1 UI created and should be visible");

                // Tạo UI cho người chơi 2
                GameClientUI player2UI = new GameClientUI("Player 2");
                player2UI.setLocation(player1UI.getX() + player1UI.getWidth() + 20, player1UI.getY());
                player2UI.setVisible(true);
                System.out.println("Player 2 UI created and should be visible");
            } catch (Exception e) {
                System.err.println("Error in TestGameClientFixed: ");
                e.printStackTrace();
            }
        });
    }
}