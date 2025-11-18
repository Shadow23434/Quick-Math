package com.mathspeed.client_test;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client {
    private final String host;
    private final int port;

    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService exec = Executors.newCachedThreadPool();

    // optional local IP bind
    private final String localIp;

    public Client(String host, int port, String localIp) {
        this.host = host;
        this.port = port;
        this.localIp = localIp; // ex: "192.168.60.10" or "" to ignore
    }

    public void start() {
        try {
            socket = new Socket();

            if (localIp != null && !localIp.isBlank()) {
                socket.bind(new InetSocketAddress(localIp, 0));
            }

            socket.connect(new InetSocketAddress(host, port));

            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            running.set(true);

            System.out.println("Connected from [" + (localIp.isBlank() ? "auto-IP" : localIp) +
                    "] to " + host + ":" + port);

            printHelp();

            exec.submit(this::readerLoop);

            consoleLoop();

        } catch (Exception e) {
            System.err.println("Failed to start client: " + e.getMessage());
            closeQuiet();
        }
    }

    private void readerLoop() {
        try {
            String line;
            while (running.get() && (line = in.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                if (line.startsWith("{")) {
                    System.out.println("[SERVER][JSON] " + line);
                } else {
                    System.out.println("[SERVER] " + line);
                }
            }
        } catch (IOException e) {
            if (running.get())
                System.err.println("Reader error: " + e.getMessage());
        } finally {
            running.set(false);
        }
    }

    private void consoleLoop() {
        try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
            String input;
            while (running.get() && (input = console.readLine()) != null) {
                input = input.trim();
                if (input.isEmpty()) continue;

                if (input.equalsIgnoreCase("/quit") || input.equalsIgnoreCase("/exit")) {
                    sendLine("QUIT");
                    shutdown();
                    break;
                } else if (input.equalsIgnoreCase("/help")) {
                    printHelp();
                } else {
                    sendLine(input);
                }
            }
        } catch (IOException e) {
            System.err.println("Console error: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    private synchronized void sendLine(String s) {
        if (!running.get()) return;

        try {
            out.write(s);
            out.newLine();
            out.flush();
        } catch (IOException e) {
            System.err.println("Send failed: " + e.getMessage());
            shutdown();
        }
    }

    private void shutdown() {
        if (!running.getAndSet(false)) return;

        closeQuiet();
        exec.shutdownNow();
        System.out.println("Client stopped.");
    }

    private void closeQuiet() {
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (in != null) in.close(); } catch (Exception ignored) {}
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (Exception ignored) {}
    }

    private void printHelp() {
        System.out.println("Interactive TestClient ready. Type server commands:");
        System.out.println("  REGISTER alice pass");
        System.out.println("  LOGIN alice pass");
        System.out.println("  CHALLENGE bob 5");
        System.out.println("  ACCEPT alice");
        System.out.println("  READY");
        System.out.println("  REQUEST_MATCH_INFO");
        System.out.println("  SUBMIT_ANSWER 1+2*3");
        System.out.println("  JOIN_QUEUE");
        System.out.println("  LEAVE_QUEUE");
        System.out.println("  PING");
        System.out.println("  QUIT (/quit or /exit)");
        System.out.println("  /help");
    }

    public static void main(String[] args) {

        // DEFAULT FOR TESTING IN VM
        String host = "localhost";
        int port = 8888;
        String localIp = ""; // auto bind

        if (args.length >= 1) host = args[0];
        if (args.length >= 2) port = Integer.parseInt(args[1]);
        if (args.length >= 3) localIp = args[2];

        System.out.println("Starting client with:");
        System.out.println("HOST=" + host);
        System.out.println("PORT=" + port);
        System.out.println("LOCAL IP=" + (localIp.isBlank() ? "auto" : localIp));

        Client client = new Client(host, port, localIp);
        client.start();
    }
}
