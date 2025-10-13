// java
package org.example.server;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

public class GameServerTest {

    // Helper to set private (possibly final) fields via reflection
    private static void setPrivateField(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testStartAndShutdown_stopsServerThread() throws Exception {
        GameServer server = new GameServer();

        // Inject mock UserDAO so server interactions don't hit real DB
        UserDAO mockDao = Mockito.mock(UserDAO.class);
        setPrivateField(server, "userDAO", mockDao);

        Thread serverThread = new Thread(server::start);
        serverThread.start();

        // give server a moment to start
        Thread.sleep(300);

        server.shutdown();

        // wait for server thread to exit
        serverThread.join(2000);
        assertFalse(serverThread.isAlive(), "Server thread should be stopped after shutdown");
    }

    @Test
    public void testRemoveClient_invokesUserDaoLogout() {
        GameServer server = new GameServer();

        UserDAO mockDao = Mockito.mock(UserDAO.class);
        setPrivateField(server, "userDAO", mockDao);

        // Mock a ClientHandler and register it
        ClientHandler mockHandler = Mockito.mock(ClientHandler.class);
        Mockito.when(mockHandler.getUsername()).thenReturn("testuser");

        server.addClient("testuser", mockHandler);

        // Call removeClient and verify logout called on the DAO
        server.removeClient("testuser");
        Mockito.verify(mockDao).logout("testuser");
    }
}