package com.mathspeed.application.game;

import com.mathspeed.adapter.network.ClientHandler;
import com.mathspeed.adapter.network.ClientRegistry;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Simple matchmaking: players call joinQueue/removeQueue.
 * This class pairs players every second.
 */
public class Matchmaker {
    private final ClientRegistry clientRegistry;
    private final GameSessionManager sessionManager;
    private final Queue<ClientHandler> waitingQueue = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running = true;

    // MÃ¡ÂºÂ·c Ã„â€˜Ã¡Â»â€¹nh sÃ¡Â»â€˜ vÃƒÂ²ng vÃƒÂ  thÃ¡Â»Âi gian mÃ¡Â»â€”i round
    private final int DEFAULT_TOTAL_ROUNDS = 10;
    private final long DEFAULT_ROUND_TIME_SECONDS = 25;

    public Matchmaker(ClientRegistry clientRegistry, GameSessionManager sessionManager) {
        this.clientRegistry = clientRegistry;
        this.sessionManager = sessionManager;
        scheduler.scheduleAtFixedRate(this::matchLoop, 0, 1, TimeUnit.SECONDS);
    }


    public void joinQueue(ClientHandler client) {
        waitingQueue.offer(client);
    }

    public void leaveQueue(ClientHandler client) {
        waitingQueue.remove(client);
    }

    private void matchLoop() {
        if (!running) return;
        while (waitingQueue.size() >= 2) {
            ClientHandler p1 = waitingQueue.poll();
            ClientHandler p2 = waitingQueue.poll();
            if (p1 == null || p2 == null) continue;

            // GÃ¡Â»Âi GameSessionManager vÃ¡Â»â€ºi tham sÃ¡Â»â€˜ mÃ¡ÂºÂ·c Ã„â€˜Ã¡Â»â€¹nh
            sessionManager.createSessionSafely(p1, p2, DEFAULT_TOTAL_ROUNDS, DEFAULT_ROUND_TIME_SECONDS);
        }
    }

    public void shutdown() {
        running = false;
        scheduler.shutdownNow();
        waitingQueue.clear();
    }
}
