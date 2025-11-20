package com.mathspeed.infrastructure.persistence;

import com.mathspeed.infrastructure.db.DBConnect;

import javax.sql.DataSource;
import java.sql.*;

public abstract class BaseDAO {
    private final DataSource dataSource;

    protected BaseDAO() {
        this(DBConnect.getDataSource());
    }

    protected BaseDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    protected Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    protected <T> T executeQuery(String sql, PrepStatementSetter setter, ResultSetExtractor<T> extractor) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (setter != null) setter.set(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return extractor.extract(rs);
            }
        }
    }

    protected int executeUpdate(String sql, PrepStatementSetter setter) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (setter != null) setter.set(ps);
            return ps.executeUpdate();
        }
    }

    @FunctionalInterface
    protected interface PrepStatementSetter {
        void set(PreparedStatement ps) throws SQLException;
    }

    @FunctionalInterface
    protected interface ResultSetExtractor<T> {
        T extract(ResultSet rs) throws SQLException;
    }
}


