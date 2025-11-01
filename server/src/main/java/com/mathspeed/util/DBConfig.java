package com.mathspeed.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.InputStream;
import java.util.Properties;

/**
 * DBConfig: build HikariDataSource from (in order of precedence):
 * 1) Environment variables: DB_JDBC_URL, DB_USER, DB_PASSWORD, DB_MAX_POOL, DB_MIN_IDLE
 * 2) classpath db.properties
 * 3) built-in defaults
 *
 * Also exposes getDataSource(), getHikariDataSource(), reload() and close().
 */
public class DBConfig {
    private static volatile HikariDataSource dataSource;

    private static final String DEFAULT_JDBC_URL = "jdbc:mysql://localhost:3306/qcalgame";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "";
    private static final int DEFAULT_MAX_POOL = 10;
    private static final int DEFAULT_MIN_IDLE = 2;

    static {
        initDataSource();
    }

    private static void initDataSource() {
        try {
            Properties props = new Properties();

            // 1) try to load classpath properties file
            try (InputStream is = DBConfig.class.getClassLoader().getResourceAsStream("db.properties")) {
                if (is != null) {
                    props.load(is);
                }
            } catch (Exception ignored) {}

            // 2) override with env vars if present
            String jdbcUrl = firstNonEmpty(
                    System.getenv("DB_JDBC_URL"),
                    props.getProperty("db.jdbcUrl"),
                    DEFAULT_JDBC_URL
            );

            String user = firstNonEmpty(
                    System.getenv("DB_USER"),
                    props.getProperty("db.user"),
                    DEFAULT_USER
            );

            String password = firstNonEmpty(
                    System.getenv("DB_PASSWORD"),
                    props.getProperty("db.password"),
                    DEFAULT_PASSWORD
            );

            int maxPool = parseIntOrDefault(firstNonEmpty(System.getenv("DB_MAX_POOL"), props.getProperty("db.maxPool")), DEFAULT_MAX_POOL);
            int minIdle = parseIntOrDefault(firstNonEmpty(System.getenv("DB_MIN_IDLE"), props.getProperty("db.minIdle")), DEFAULT_MIN_IDLE);

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
            config.setConnectionTimeout(parseLongOrDefault(props.getProperty("db.connectionTimeout"), 30000L));
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

    public static HikariDataSource getHikariDataSource() {
        return dataSource;
    }

    /**
     * Reload datasource (rebuild pool). Useful if config changed at runtime.
     */
    public static synchronized void reload() {
        initDataSource();
    }

    /**
     * Close the underlying pool.
     */
    public static synchronized void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}