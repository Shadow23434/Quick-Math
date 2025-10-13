package org.example.server;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.*;

class GameSessionTest {
    private ClientHandler mockPlayer1;
    private ClientHandler mockPlayer2;
    private GameSession gameSession;

    @BeforeEach
    void setUp() {
        mockPlayer1 = mock(ClientHandler.class);
        mockPlayer2 = mock(ClientHandler.class);

        when(mockPlayer1.getUsername()).thenReturn("player1");
        when(mockPlayer2.getUsername()).thenReturn("player2");

        gameSession = new GameSession(mockPlayer1, mockPlayer2);
    }

    @AfterEach
    void tearDown() {
        if (gameSession != null) {
            // Clean up scheduler
            try {
                Method endGameMethod = GameSession.class.getDeclaredMethod("endGame");
                endGameMethod.setAccessible(true);
                endGameMethod.invoke(gameSession);
            } catch (Exception e) {
                // ignore cleanup errors
            }
        }
    }

    @Test
    void testTimeoutRound_invokedViaReflection() throws Exception {
        // Reset mocks to ignore the initial ROUND_START from constructor
        Mockito.clearInvocations(mockPlayer1, mockPlayer2);

        // Invoke the private timeoutRound method to avoid waiting 30 seconds
        Method timeoutMethod = GameSession.class.getDeclaredMethod("timeoutRound");
        timeoutMethod.setAccessible(true);
        timeoutMethod.invoke(gameSession);

        // Both players should receive ROUND_TIMEOUT
        verify(mockPlayer1, timeout(1000)).sendMessage("ROUND_TIMEOUT");
        verify(mockPlayer2, timeout(1000)).sendMessage("ROUND_TIMEOUT");

        // Also ensure a new ROUND_START was sent (startNewRound called)
        verify(mockPlayer1, timeout(1000)).sendMessage(matches("ROUND_START\\|.*"));
        verify(mockPlayer2, timeout(1000)).sendMessage(matches("ROUND_START\\|.*"));
    }

    @Test
    void testPlayerQuit_notifiesOpponent() {
        Mockito.clearInvocations(mockPlayer1, mockPlayer2);

        gameSession.playerQuit(mockPlayer1);

        verify(mockPlayer2, timeout(1000)).sendMessage("OPPONENT_QUIT|YOU_WIN");
        verify(mockPlayer2, timeout(1000)).sendMessage(matches("GAME_END\\|.*"));
    }

    @Test
    void testSubmitAnswer_updatesScore() {
        Mockito.clearInvocations(mockPlayer1, mockPlayer2);

        gameSession.submitAnswer(mockPlayer1, "2+3");

        verify(mockPlayer1, timeout(1000)).sendMessage(matches("CORRECT\\|\\d+"));
    }
}