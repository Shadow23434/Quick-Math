package com.mathspeed.dao;

import com.mathspeed.util.DBConfig;

import javax.sql.DataSource;
import java.sql.*;

/**
 * BaseDAO: provides helpers:
 * - getConnection()
 * - executeQuery(...)
 * - executeUpdate(...) returns number of affected rows
 * - withTransaction(...) runs TransactionRunnable with commit/rollback
 *
 * Usage:
 *  - For read: executeQuery(sql, ps -> ps.setString(1, ...), rs -> { ... })
 *  - For update: int rows = executeUpdate(sql, ps -> { ... });
 *  - For transactional work:
 *      withTransaction(conn -> {
 *          try (PreparedStatement ps1 = conn.prepareStatement(...)) { ... ps1.executeUpdate(); }
 *          try (PreparedStatement ps2 = conn.prepareStatement(...)) { ... ps2.executeUpdate(); }
 *      });
 */
public abstract class BaseDAO {
    private final DataSource dataSource;

    protected BaseDAO() {
        this(DBConfig.getDataSource());
    }

    protected BaseDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    protected Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    protected void closeQuietly(ResultSet rs) {
        if (rs != null) try { rs.close(); } catch (SQLException ignored) {}
    }

    protected void closeQuietly(Statement stmt) {
        if (stmt != null) try { stmt.close(); } catch (SQLException ignored) {}
    }

    protected void closeQuietly(Connection conn) {
        if (conn != null) try { conn.close(); } catch (SQLException ignored) {}
    }

    protected void closeQuietly(ResultSet rs, Statement stmt, Connection conn) {
        closeQuietly(rs);
        closeQuietly(stmt);
        closeQuietly(conn);
    }

    @FunctionalInterface
    protected interface SQLConsumer<T> {
        void accept(T t) throws SQLException;
    }

    @FunctionalInterface
    protected interface SQLFunction<T, R> {
        R apply(T t) throws SQLException;
    }

    /**
     * Execute a query (SELECT). The resultHandler will be called with the ResultSet.
     * This method manages connection/statement/resultset lifecycle automatically.
     */
    protected void executeQuery(String sql, SQLConsumer<PreparedStatement> preparer, SQLConsumer<ResultSet> resultHandler) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (preparer != null) preparer.accept(ps);
            try (ResultSet rs = ps.executeQuery()) {
                if (resultHandler != null) resultHandler.accept(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Execute an update statement (INSERT/UPDATE/DELETE). Returns affected row count.
     */
    protected int executeUpdate(String sql, SQLConsumer<PreparedStatement> preparer) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (preparer != null) preparer.accept(ps);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Execute update with generated keys retrieval.
     * Example usage: long id = executeUpdateWithKeys("INSERT ...", ps -> { ... }, rs -> { if (rs.next()) return rs.getLong(1); return -1L; });
     */
    protected <R> R executeUpdateWithKeys(String sql, SQLConsumer<PreparedStatement> preparer, SQLFunction<ResultSet, R> keysHandler) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (preparer != null) preparer.accept(ps);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (keysHandler != null) return keysHandler.apply(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Run multiple DB operations in a single transaction. The TransactionRunnable receives a Connection
     * with autoCommit=false. Commit is attempted on successful completion, otherwise rollback on exception.
     *
     * Example:
     *   withTransaction(conn -> {
     *       try (PreparedStatement ps = conn.prepareStatement(...)) { ... ps.executeUpdate(); }
     *       // other statements
     *   });
     */
    @FunctionalInterface
    public interface TransactionRunnable {
        void run(Connection conn) throws SQLException;
    }

    protected void withTransaction(TransactionRunnable runnable) {
        try (Connection conn = getConnection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                runnable.run(conn);
                conn.commit();
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ex) { /* log if needed */ }
                throw e;
            } finally {
                try { conn.setAutoCommit(previousAutoCommit); } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Shutdown pool helper (delegates to DBConfig).
     */
    public static void shutdownPool() {
        DBConfig.close();
    }
}