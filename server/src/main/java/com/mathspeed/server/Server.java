package com.mathspeed.server;

import com.mathspeed.dao.GameDAO;
import com.mathspeed.dao.PlayerDAO;
import com.mathspeed.dao.impl.GameDAOImpl;
import com.mathspeed.dao.impl.PlayerDAOImpl;
import com.mathspeed.network.ClientRegistry;
import com.mathspeed.server.service.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Server {
    private static final Logger logger = LogManager.getLogger(Server.class);
    private static final int PORT = 8888;

    public static void main(String[] args) {
        logger.info("Server starting on port " + PORT);
        logger.info("Server setup complete!");

        PlayerDAO playerDAO = new PlayerDAOImpl();
        GameDAO gameDAO = new GameDAOImpl();
        ClientRegistry clientRegistry = new ClientRegistry(playerDAO);
        GameSessionManager sessionManager = new GameSessionManager(clientRegistry, gameDAO);
        Matchmaker matchmaker = new Matchmaker(clientRegistry, sessionManager);
        ChallengeManager challengeManager = new ChallengeManager(clientRegistry, sessionManager);
        ServerAcceptor acceptor = new ServerAcceptor(PORT, clientRegistry, matchmaker, challengeManager, playerDAO);

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