package com.mathspeed.network;

import com.mathspeed.protocol.Message;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe singleton that manages the NetworkManager and dispatches messages to listeners.
 * All listener callbacks that may touch UI are executed on the JavaFX Application Thread.
 */
public class NetworkController {
    private static final Logger logger = LoggerFactory.getLogger(NetworkController.class);
    private static final NetworkController INSTANCE = new NetworkController();

    private final NetworkManager nm = NetworkManager.getInstance();
    private final List<NetworkListener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService readerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "net-reader");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);

    private NetworkController() {}

    public static NetworkController getInstance() { return INSTANCE; }

    public void addListener(NetworkListener l) { if (l != null) listeners.add(l); }
    public void removeListener(NetworkListener l) { if (l != null) listeners.remove(l); }

    public synchronized boolean connect() {
        if (nm.isConnected()) return true;
        boolean ok = nm.connect();
        if (!ok) {
            notifyError(new IOException("Failed to connect to server"));
            return false;
        }
        notifyConnected();
        startReader();
        return true;
    }

    public synchronized void disconnect() {
        stopReader();
        try { nm.disconnect(); } catch (Exception e) { logger.warn("Error while disconnecting", e); }
        notifyDisconnected();
    }

    public void send(Message msg) {
        try {
            nm.sendMessage(msg);
        } catch (Exception e) {
            notifyError(e instanceof Exception ? (Exception)e : new Exception(e));
        }
    }

    public boolean isConnected() { return nm.isConnected(); }

    private void startReader() {
        if (running.compareAndSet(false, true)) {
            readerExecutor.submit(() -> {
                try {
                    while (running.get() && nm.isConnected()) {
                        try {
                            Message m = nm.receiveMessage();
                            if (m == null) continue;
                            dispatchMessage(m);
                        } catch (IOException ioe) {
                            logger.warn("I/O error while receiving message", ioe);
                            notifyError(ioe);
                            break;
                        } catch (Exception ex) {
                            logger.error("Unexpected error in reader loop", ex);
                            notifyError(ex);
                        }
                    }
                } finally {
                    running.set(false);
                    Platform.runLater(this::notifyDisconnected);
                }
            });
        }
    }

    private void stopReader() {
        running.set(false);
        try { readerExecutor.shutdownNow(); } catch (Exception ignored) {}
    }

    private void dispatchMessage(Message m) {
        // Deliver on FX thread so listeners can update UI without extra Platform.runLater calls
        Platform.runLater(() -> {
            for (NetworkListener l : listeners) {
                try { l.onMessage(m); } catch (Exception e) { logger.warn("Listener threw onMessage", e); }
            }
        });
    }

    private void notifyConnected() {
        Platform.runLater(() -> {
            for (NetworkListener l : listeners) {
                try { l.onConnected(); } catch (Exception e) { logger.warn("Listener threw onConnected", e); }
            }
        });
    }

    private void notifyDisconnected() {
        Platform.runLater(() -> {
            for (NetworkListener l : listeners) {
                try { l.onDisconnected(); } catch (Exception e) { logger.warn("Listener threw onDisconnected", e); }
            }
        });
    }

    private void notifyError(Exception ex) {
        Platform.runLater(() -> {
            for (NetworkListener l : listeners) {
                try { l.onError(ex); } catch (Exception e) { logger.warn("Listener threw onError", e); }
            }
        });
    }
}

