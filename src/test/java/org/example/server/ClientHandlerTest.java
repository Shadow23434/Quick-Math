package org.example.server;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientHandlerTest {

    @Test
    void testLoginMessage_addsClientAndSendsLoginSuccess() throws Exception {
        Socket mockSocket = Mockito.mock(Socket.class);
        GameServer mockServer = Mockito.mock(GameServer.class);

        ByteArrayInputStream inStream = new ByteArrayInputStream("LOGIN|testUser\n".getBytes());
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();

        Mockito.when(mockSocket.getInputStream()).thenReturn(inStream);
        Mockito.when(mockSocket.getOutputStream()).thenReturn(outStream);

        ClientHandler handler = new ClientHandler(mockSocket, mockServer);

        // run() will process the single login line then exit (input stream EOF) and call disconnect()
        handler.run();

        // verify server received addClient for the login
        Mockito.verify(mockServer).addClient(Mockito.eq("testUser"), Mockito.any(ClientHandler.class));

        // verify login success was sent to client
        String output = outStream.toString();
        assertTrue(output.contains("LOGIN_SUCCESS"));

        // disconnect is called by run() finally block, so removeClient should be invoked
        Mockito.verify(mockServer).removeClient(Mockito.eq("testUser"));
    }

    @Test
    void testDisconnect_closesSocket_and_callsRemoveClient() throws Exception {
        Socket mockSocket = Mockito.mock(Socket.class);
        GameServer mockServer = Mockito.mock(GameServer.class);

        ByteArrayInputStream inStream = new ByteArrayInputStream("LOGIN|anotherUser\n".getBytes());
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();

        Mockito.when(mockSocket.getInputStream()).thenReturn(inStream);
        Mockito.when(mockSocket.getOutputStream()).thenReturn(outStream);

        ClientHandler handler = new ClientHandler(mockSocket, mockServer);

        // run to perform login and ensure username is set
        handler.run();

        // verify socket.close() was invoked during disconnect
        Mockito.verify(mockSocket).close();

        // verify server.removeClient was called with the username
        Mockito.verify(mockServer).removeClient(Mockito.eq("anotherUser"));
    }
}