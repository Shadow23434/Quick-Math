package com.mathspeed.bootstrap;

import com.mathspeed.domain.port.GameRepository;
import com.mathspeed.domain.port.PlayerRepository;
import com.mathspeed.infrastructure.persistence.GameDAOImpl;
import com.mathspeed.infrastructure.persistence.PlayerDAOImpl;
import com.mathspeed.adapter.network.ClientRegistry;
import com.mathspeed.application.game.ChallengeManager;
import com.mathspeed.application.game.GameSessionManager;
import com.mathspeed.application.game.Matchmaker;
import com.mathspeed.adapter.network.ServerAcceptor;
import com.mathspeed.adapter.network.HttpServer;
import com.mathspeed.adapter.network.auth.AuthHandler;
import com.mathspeed.adapter.network.HealthHandler;
import com.mathspeed.application.auth.AuthService;
import com.mathspeed.application.friend.FriendService;
import com.mathspeed.adapter.network.friend.FriendHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);
    private static final int PORT = 8888;
    private static final int HTTP_PORT = 8080;

    public static void main(String[] args) {
        logger.info("Server starting on port " + PORT);
        logger.info("Server setup complete!");

        PlayerRepository playerRepository = new PlayerDAOImpl();
        GameRepository gameRepository = new GameDAOImpl();
        ClientRegistry clientRegistry = new ClientRegistry(playerRepository);
        GameSessionManager sessionManager = new GameSessionManager(clientRegistry, gameRepository);
        Matchmaker matchmaker = new Matchmaker(clientRegistry, sessionManager);
        ChallengeManager challengeManager = new ChallengeManager(clientRegistry, sessionManager);
        ServerAcceptor acceptor = new ServerAcceptor(PORT, clientRegistry, matchmaker, challengeManager, playerRepository);

        // shared HTTP server for multiple features
        HttpServer httpServer = new HttpServer(HTTP_PORT);
        AuthService authService = new AuthService(playerRepository);
        FriendService friendService = new FriendService(playerRepository);
        try {
            httpServer.createContext("/api/health", new HealthHandler());
            httpServer.createContext("/api/auth", new AuthHandler(authService));
            httpServer.createContext("/api/friends/", new FriendHandler(friendService));
            httpServer.start();
        } catch (Exception e) {
            System.err.println("Failed to start shared HTTP server: " + e.getMessage());
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown requested, stopping server...");
            acceptor.shutdown();
            matchmaker.shutdown();
            challengeManager.shutdown();
            sessionManager.shutdown();
            clientRegistry.shutdown();
            // stop shared HTTP server
            httpServer.stop();
            System.out.println("Server stopped.");
        }));

        acceptor.start();
    }
}
