package com.mathspeed.network;

import com.google.gson.Gson;
import com.mathspeed.util.GsonFactory;
import com.mathspeed.protocol.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class NetworkManager {
    private static final Logger logger = LoggerFactory.getLogger(NetworkManager.class);
    private static NetworkManager instance;

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8888;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private Gson gson;
    private boolean connected;

    private NetworkManager() {
        gson = GsonFactory.createGson();
        connected = false;
    }

    public static synchronized NetworkManager getInstance() {
        if (instance == null) {
            instance = new NetworkManager();
        }
        return instance;
    }

    public boolean connect() {
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            connected = true;
            logger.info("Connected to server at {}:{}", SERVER_HOST, SERVER_PORT);
            return true;
        } catch (IOException e) {
            logger.error("Failed to connect to server", e);
            connected = false;
            return false;
        }
    }

    public void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            connected = false;
            logger.info("Disconnected from server");
        } catch (IOException e) {
            logger.error("Error disconnecting from server", e);
        }
    }

    public void sendMessage(Message message) {
        if (!connected) {
            logger.warn("Cannot send message - not connected");
            return;
        }

        String json = gson.toJson(message);
        writer.println(json);
        logger.debug("Sent message: {}", json);
    }

    public Message receiveMessage() throws IOException {
        if (!connected) {
            throw new IOException("Not connected to server");
        }

        String json = reader.readLine();
        if (json == null) {
            throw new IOException("Connection closed by server");
        }

        logger.debug("Received message: {}", json);
        return gson.fromJson(json, Message.class);
    }

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    // New: connect to a specific host/port (non-destructive to existing constants)
    public boolean connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            connected = true;
            logger.info("Connected to server at {}:{}", host, port);
            return true;
        } catch (IOException e) {
            logger.error("Failed to connect to server {}:{}", host, port, e);
            connected = false;
            return false;
        }
    }

    // New: receive a Message but fail with timeout after timeoutMs milliseconds
    public Message receiveMessageWithTimeout(long timeoutMs) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to server");
        }

        try {
            socket.setSoTimeout((int) timeoutMs);
            String json = reader.readLine();
            if (json == null) {
                throw new IOException("Connection closed by server");
            }
            logger.debug("Received message: {}", json);
            return gson.fromJson(json, Message.class);
        } catch (SocketTimeoutException e) {
            throw new IOException("Timeout waiting for server response", e);
        } finally {
            try {
                socket.setSoTimeout(0); // reset to infinite
            } catch (Exception ignored) {}
        }
    }
}