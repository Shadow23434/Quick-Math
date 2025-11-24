package com.mathspeed.bootstrap;

import com.mathspeed.adapter.network.*;
import com.mathspeed.domain.port.*;
import com.mathspeed.infrastructure.persistence.*;
import com.mathspeed.adapter.network.library.LibraryHandler;
import com.mathspeed.application.library.LibraryService;
import com.mathspeed.domain.port.GameHistoryRepository;
import com.mathspeed.domain.port.GameRepository;
import com.mathspeed.domain.port.PlayerRepository;
import com.mathspeed.domain.port.QuizzRepository;
import com.mathspeed.infrastructure.persistence.GameDAOImpl;
import com.mathspeed.infrastructure.persistence.PlayerDAOImpl;
import com.mathspeed.infrastructure.persistence.QuizDAOImpl;
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
import com.mathspeed.adapter.network.stat.StatsHandler;
import com.mathspeed.infrastructure.persistence.GameHistoryDAOImpl;
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
        QuizzRepository quizRepository = new QuizDAOImpl();
        GameRepository gameRepository = new GameDAOImpl();
        GameHistoryRepository gameHistoryRepository = new GameHistoryDAOImpl();

        ClientRegistry clientRegistry = new ClientRegistry(playerRepository);
        GameSessionManager sessionManager = new GameSessionManager(clientRegistry, gameRepository);
        Matchmaker matchmaker = new Matchmaker(clientRegistry, sessionManager);
        ChallengeManager challengeManager = new ChallengeManager(clientRegistry, sessionManager);
        ServerAcceptor acceptor = new ServerAcceptor(PORT, clientRegistry, matchmaker, challengeManager, playerRepository);

        // shared HTTP server for multiple features
        HttpServer httpServer = new HttpServer(HTTP_PORT);
        AuthService authService = new AuthService(playerRepository);
        FriendService friendService = new FriendService(playerRepository);
        LibraryService libraryService = new LibraryService(quizRepository);
        try {
            httpServer.createContext("/api/health", new HealthHandler());
            httpServer.createContext("/api/auth", new AuthHandler(authService));
            httpServer.createContext("/api/friends/", new FriendHandler(friendService));
            httpServer.createContext("/api/library", new LibraryHandler(authService, libraryService));
            httpServer.createContext("/api/stats", new StatsHandler(authService, quizRepository, gameHistoryRepository));

             LeaderboardRepository leaderboardRepository = new LeaderboardDAOImpl();
            httpServer.createContext("/api/leaderboard", new LeaderboardHandler(leaderboardRepository));
            
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
