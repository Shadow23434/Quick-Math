package com.gamesession;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class GameClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean connected = false;
    private Thread listenerThread;
    private final String host;
    private final int port;
    private String username;
    private Consumer<String> messageHandler;

    public GameClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public boolean connect() {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            connected = true;

            // Start listener thread
            listenerThread = new Thread(this::listen);
            listenerThread.setDaemon(true);
            listenerThread.start();

            return true;
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
            return false;
        }
    }

    private void listen() {
        try {
            String message;
            while (connected && (message = in.readLine()) != null) {
                if (messageHandler != null) {
                    messageHandler.accept(message);
                }
            }
        } catch (IOException e) {
            if (connected) {
                System.err.println("Error reading from server: " + e.getMessage());
                disconnect();
            }
        }
    }

    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler;
    }

    public boolean login(String username, String password) {
        if (!connected) return false;
        this.username = username;
        sendMessage("LOGIN|" + username + "|" + password);
        // Actual verification will happen in the message handler
        return true;
    }

    public void submitAnswer(String answer) {
        if (!connected || username == null) return;
        sendMessage("SUBMIT_ANSWER|" + answer);
    }

    public void sendMessage(String message) {
        if (connected && out != null) {
            out.println(message);
        }
    }

    public void disconnect() {
        connected = false;
        try {
            if (socket != null) socket.close();
            if (in != null) in.close();
            if (out != null) out.close();
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }

    public String getUsername() {
        return username;
    }

    public boolean isConnected() {
        return connected;
    }
}