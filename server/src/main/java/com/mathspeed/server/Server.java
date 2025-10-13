package com.mathspeed.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Server {
    private static final Logger logger = LogManager.getLogger(Server.class);
    private static final int PORT = 8888;

    public static void main(String[] args) {
        logger.info("Server starting on port " + PORT);
        logger.info("Server setup complete!");
    }
}