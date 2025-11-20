package com.mathspeed.infrastructure.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.InputStream;
import java.util.Properties;

public class DBConnect {
    private static volatile HikariDataSource dataSource;
    static {
        initDataSource();
    }

    private static void initDataSource() {
        try {
            Properties props = new Properties();

            try (InputStream is = DBConnect.class.getClassLoader().getResourceAsStream("db.properties")) {
                if (is != null) {
                    props.load(is);
                }
            } catch (Exception ignored) {}

            // Read string configs from env or properties. If absent, fail fast so configuration is explicit.
            String jdbcUrl = firstNonEmpty(
                    System.getenv("DB_JDBC_URL"),
                    props.getProperty("db.jdbcUrl"),
                    props.getProperty("hibernate.connection.url")
            );
            if (jdbcUrl == null || jdbcUrl.isEmpty()) {
                throw new RuntimeException("Database JDBC URL is not configured. Set db.jdbcUrl in db.properties or DB_JDBC_URL environment variable.");
            }

            String user = firstNonEmpty(
                    System.getenv("DB_USER"),
                    props.getProperty("db.user"),
                    props.getProperty("hibernate.connection.username")
            );
            if (user == null || user.isEmpty()) {
                throw new RuntimeException("Database user is not configured. Set db.user in db.properties or DB_USER environment variable.");
            }

            String password = firstNonEmpty(
                    System.getenv("DB_PASSWORD"),
                    props.getProperty("db.password"),
                    props.getProperty("hibernate.connection.password")
            );
            if (password == null) {
                // allow empty password explicitly, but not missing
                throw new RuntimeException("Database password is not configured. Set db.password in db.properties or DB_PASSWORD environment variable (can be empty string).");
            }

            // Numeric defaults: try environment -> db.* property -> explicit default in properties -> fallback hardcoded
            int fallbackMaxPool = 10;
            int fallbackMinIdle = 2;
            int defaultMaxPool = parseIntOrDefault(props.getProperty("db.maxPool"), parseIntOrDefault(props.getProperty("db.defaultMaxPool"), fallbackMaxPool));
            int defaultMinIdle = parseIntOrDefault(props.getProperty("db.minIdle"), parseIntOrDefault(props.getProperty("db.defaultMinIdle"), fallbackMinIdle));

            int maxPool = parseIntOrDefault(firstNonEmpty(System.getenv("DB_MAX_POOL"), props.getProperty("db.maxPool")), defaultMaxPool);
            int minIdle = parseIntOrDefault(firstNonEmpty(System.getenv("DB_MIN_IDLE"), props.getProperty("db.minIdle")), defaultMinIdle);

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(user);
            config.setPassword(password);

            // set driver if provided in properties
            String driver = firstNonEmpty(System.getenv("DB_DRIVER"), props.getProperty("db.driver"));
            if (driver != null && !driver.isEmpty()) {
                config.setDriverClassName(driver);
            } else {
                // best-effort default for MySQL (no harm if driver auto-loaded)
                config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            }

            config.setMaximumPoolSize(maxPool);
            config.setMinimumIdle(minIdle);
            config.setConnectionTimeout(parseLongOrDefault(firstNonEmpty(props.getProperty("db.connectionTimeout"), props.getProperty("hibernate.connection.timeout")), 30000L));
            config.setIdleTimeout(parseLongOrDefault(props.getProperty("db.idleTimeout"), 600000L));
            config.setMaxLifetime(parseLongOrDefault(props.getProperty("db.maxLifetime"), 1800000L));

            // Performance settings (can be overridden by properties)
            config.addDataSourceProperty("cachePrepStmts", firstNonEmpty(props.getProperty("db.cachePrepStmts"), "true"));
            config.addDataSourceProperty("prepStmtCacheSize", firstNonEmpty(props.getProperty("db.prepStmtCacheSize"), "250"));
            config.addDataSourceProperty("prepStmtCacheSqlLimit", firstNonEmpty(props.getProperty("db.prepStmtCacheSqlLimit"), "2048"));
            config.addDataSourceProperty("useServerPrepStmts", firstNonEmpty(props.getProperty("db.useServerPrepStmts"), "true"));

            HikariDataSource ds = new HikariDataSource(config);
            HikariDataSource previous = dataSource;
            dataSource = ds;
            if (previous != null && !previous.isClosed()) {
                previous.close();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize DataSource", e);
        }
    }

    private static String firstNonEmpty(String... candidates) {
        for (String s : candidates) {
            if (s != null && !s.isEmpty()) return s;
        }
        return null;
    }

    private static int parseIntOrDefault(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s); } catch (NumberFormatException ignored) { return def; }
    }

    private static long parseLongOrDefault(String s, long def) {
        if (s == null) return def;
        try { return Long.parseLong(s); } catch (NumberFormatException ignored) { return def; }
    }

    public static DataSource getDataSource() {
        return dataSource;
    }

    public static synchronized void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}