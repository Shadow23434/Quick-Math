package com.mathspeed.application.game;

import com.mathspeed.adapter.network.ClientHandler;
import com.mathspeed.domain.port.*;
import com.mathspeed.adapter.network.ClientRegistry;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.*;

public class ServerAcceptor {
    private final int port;
    private final ClientRegistry clientRegistry;
    private final Matchmaker matchmaker;
    private final ChallengeManager challengeManager;
    private final PlayerRepository playerDAO;

    private volatile ServerSocket serverSocket;
    private final ExecutorService acceptPool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "server-acceptor");
        t.setDaemon(false);
        return t;
    });

    // Bounded ThreadPoolExecutor to avoid unbounded growth
    private final ThreadPoolExecutor clientPool;

    private volatile boolean running = true;

    public ServerAcceptor(int port,
                          ClientRegistry clientRegistry,
                          Matchmaker matchmaker,
                          ChallengeManager challengeManager,
                          PlayerRepository playerDAO) {
        this.port = port;
        this.clientRegistry = clientRegistry;
        this.matchmaker = matchmaker;
        this.challengeManager = challengeManager;
        this.playerDAO = playerDAO;

        int corePool = 10;               // adjust to your needs
        int maxPool = 50;                // max worker threads
        long keepAliveSeconds = 60L;
        int queueCapacity = 200;         // max queued connections waiting for a worker

        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(queueCapacity);
        this.clientPool = new ThreadPoolExecutor(
                corePool,
                maxPool,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                workQueue,
                new ThreadFactory() {
                    private final ThreadFactory defaultFactory = Executors.defaultThreadFactory();
                    private int idx = 0;
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = defaultFactory.newThread(r);
                        t.setName("client-worker-" + (++idx));
                        t.setDaemon(false);
                        return t;
                    }
                },
                // Use AbortPolicy so we can explicitly handle rejection and close socket
                new ThreadPoolExecutor.AbortPolicy()
        );
        // allow service threads to time out if desired:
        this.clientPool.allowCoreThreadTimeOut(true);
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("ServerApp started on port " + port);
            acceptPool.execute(this::acceptLoop);
        } catch (IOException e) {
            System.err.println("Could not start server on port " + port + ": " + e.getMessage());
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                //Chap nhan ket noi moi
                Socket socket = serverSocket.accept();
                // Optionally set sane defaults for the socket
                try {
                    socket.setSoTimeout(120_000); // 2 minutes read timeout; handler may override
                } catch (IOException ignored) {}

                //Tao mot ClientHandler moi de xu ly ket noi
                ClientHandler handler = new ClientHandler(socket, clientRegistry, matchmaker, challengeManager, playerDAO);

                try {

                    //Dua ClientHandler vao pool de xu ly I/O va logic
                    clientPool.execute(handler);
                } catch (RejectedExecutionException rej) {
                    // Pool is saturated or shutting down: politely reject connection
                    System.err.println("Connection rejected (server overloaded). Closing socket: " + socket.getRemoteSocketAddress());
                    sendServerBusyAndClose(socket);
                }
            } catch (SocketException se) {
                // SocketException is expected when serverSocket.close() is called during shutdown.
                if (running) {
                    System.err.println("Socket error accepting connection: " + se.getMessage());
                } else {
                    // server is stopping; exit loop quietly
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting connection: " + e.getMessage());
                }
            } catch (Throwable t) {
                // Catch-all to ensure accept loop doesn't die unexpectedly
                System.err.println("Unexpected error in acceptLoop: " + t.getMessage());
                t.printStackTrace();
            }
        }
    }

    private void sendServerBusyAndClose(Socket socket) {
        try (OutputStream os = socket.getOutputStream();
             PrintWriter pw = new PrintWriter(os, true)) {
            pw.println("ERROR: ServerApp busy. Try again later.");
        } catch (IOException e) {
            // ignore - we're closing anyway
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    public void shutdown() {
        running = false;

        // Close server socket to unblock accept()
        try {
            ServerSocket ss = serverSocket;
            if (ss != null && !ss.isClosed()) {
                ss.close();
            }
        } catch (IOException ignored) {}

        // Stop acceptor
        acceptPool.shutdown();
        try {
            if (!acceptPool.awaitTermination(5, TimeUnit.SECONDS)) {
                acceptPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            acceptPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        clientPool.shutdown();
        try {
            // wait for handlers to finish (adjust timeout as appropriate)
            if (!clientPool.awaitTermination(30, TimeUnit.SECONDS)) {
                System.err.println("Client handlers did not terminate in time; forcing shutdown.");
                clientPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            clientPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("ServerAcceptor shutdown complete.");
    }
}