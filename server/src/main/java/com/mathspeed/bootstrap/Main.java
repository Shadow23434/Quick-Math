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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);
    private static final int PORT = 8888;

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

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown requested, stopping server...");
            acceptor.shutdown();
            matchmaker.shutdown();
            challengeManager.shutdown();
            sessionManager.shutdown();
            clientRegistry.shutdown();
            System.out.println("Server stopped.");
        }));

        acceptor.start();
    }
}
