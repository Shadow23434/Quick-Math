package com.mathspeed.adapter.network;

import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class HttpServer {
    private final int port;
    private com.sun.net.httpserver.HttpServer server;

    public HttpServer(int port) {
        this.port = port;
    }

    // Registers a context (path -> handler). Can be called before or after start().
    public synchronized void createContext(String path, HttpHandler handler) throws IOException {
        ensureServerCreated();
        server.createContext(path, handler);
    }

    public synchronized void start() throws IOException {
        ensureServerCreated();
        server.start();
        System.out.println("HTTP server started on port " + port);
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("HTTP server stopped");
        }
    }

    private void ensureServerCreated() throws IOException {
        if (server == null) {
            server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newCachedThreadPool());
        }
    }
}
