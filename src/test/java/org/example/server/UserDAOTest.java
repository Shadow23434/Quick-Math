package org.example.server;

import org.junit.jupiter.api.*;
import javax.sql.DataSource;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserDAOTest {

    @Test
    void testRegister_success_createsStatsAndReturnsTrue() throws Exception {
        DataSource mockDs = mock(DataSource.class);
        Connection mockConn = mock(Connection.class);
        PreparedStatement insertStmt = mock(PreparedStatement.class);
        PreparedStatement statsStmt = mock(PreparedStatement.class);

        when(mockDs.getConnection()).thenReturn(mockConn);
        when(mockConn.prepareStatement(eq("INSERT INTO users (username, password_hash) VALUES (?, ?)"))).thenReturn(insertStmt);
        when(mockConn.prepareStatement(eq("INSERT INTO game_stats (user_id) SELECT id FROM users WHERE username = ?"))).thenReturn(statsStmt);

        when(insertStmt.executeUpdate()).thenReturn(1);
        when(statsStmt.executeUpdate()).thenReturn(1);

        UserDAO dao = new UserDAO(mockDs);
        boolean result = dao.register("alice", "password");

        assertTrue(result);
        verify(insertStmt).setString(eq(1), eq("alice"));
        verify(insertStmt).executeUpdate();
        verify(statsStmt).setString(eq(1), eq("alice"));
        verify(statsStmt).executeUpdate();
    }

    @Test
    void testRegister_duplicate_returnsFalse() throws Exception {
        DataSource mockDs = mock(DataSource.class);
        Connection mockConn = mock(Connection.class);
        PreparedStatement insertStmt = mock(PreparedStatement.class);

        when(mockDs.getConnection()).thenReturn(mockConn);
        when(mockConn.prepareStatement(eq("INSERT INTO users (username, password_hash) VALUES (?, ?)"))).thenReturn(insertStmt);

        SQLException dupEx = new SQLException("Duplicate", "23000", 1062);
        when(insertStmt.executeUpdate()).thenThrow(dupEx);

        UserDAO dao = new UserDAO(mockDs);
        boolean result = dao.register("bob", "pw");

        assertFalse(result);
    }

    @Test
    void testLogin_success_returnsUser_and_updatesLastLogin() throws Exception {
        DataSource mockDs = mock(DataSource.class);
        Connection mockConn = mock(Connection.class);
        PreparedStatement selectStmt = mock(PreparedStatement.class);
        PreparedStatement updateStmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(mockDs.getConnection()).thenReturn(mockConn);
        when(mockConn.prepareStatement(eq("SELECT * FROM users WHERE username = ? AND password_hash = ?"))).thenReturn(selectStmt);
        when(mockConn.prepareStatement(eq("UPDATE users SET last_login = ?, is_online = TRUE WHERE username = ?"))).thenReturn(updateStmt);

        when(selectStmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getString("username")).thenReturn("carol");
        when(rs.getString("password_hash")).thenReturn("hashed");
        Timestamp ts = Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(1));
        when(rs.getTimestamp("last_login")).thenReturn(ts);

        UserDAO dao = new UserDAO(mockDs);
        User u = dao.login("carol", "pw");

        assertNotNull(u);
        assertEquals("carol", u.getUsername());
        assertTrue(u.isOnline());
        verify(updateStmt).setString(eq(2), eq("carol"));
        verify(updateStmt).executeUpdate();
    }

    @Test
    void testLogout_executesUpdate() throws Exception {
        DataSource mockDs = mock(DataSource.class);
        Connection mockConn = mock(Connection.class);
        PreparedStatement logoutStmt = mock(PreparedStatement.class);

        when(mockDs.getConnection()).thenReturn(mockConn);
        when(mockConn.prepareStatement(eq("UPDATE users SET is_online = FALSE WHERE username = ?"))).thenReturn(logoutStmt);

        UserDAO dao = new UserDAO(mockDs);
        dao.logout("dave");

        verify(logoutStmt).setString(eq(1), eq("dave"));
        verify(logoutStmt).executeUpdate();
    }
}