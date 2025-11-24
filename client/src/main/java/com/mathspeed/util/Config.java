package com.mathspeed.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Small configuration helper that resolves API URL from multiple sources in order of precedence:
 * 1) System property -Dapi.url
 * 2) Environment variable API_URL
 * 3) External config.properties file in working directory (key: api.url)
 * 4) classpath /config.properties (key: api.url)
 * 5) default fallback
 */
public final class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);

    private static final String SYSTEM_PROP_KEY = "api.url";
    private static final String ENV_VAR_KEY = "API_URL";
    private static final String EXTERNAL_CONFIG = "config.properties"; // working dir or same folder as jar
    private static final String CLASSPATH_CONFIG = "/config.properties"; // inside resources
    private static final String DEFAULT_API_URL = "http://localhost:8080/api";

    private static final Properties props = new Properties();
    private static final String resolvedApiUrl;

    static {
        // Load external file if present (working directory)
        try {
            Path p = Paths.get(EXTERNAL_CONFIG);
            if (Files.exists(p) && Files.isReadable(p)) {
                try (InputStream in = new FileInputStream(p.toFile())) {
                    props.load(in);
                }
            }
        } catch (Exception ignored) {
            // ignore - best effort
        }

        // Load classpath defaults (won't override keys already loaded)
        try (InputStream in = Config.class.getResourceAsStream(CLASSPATH_CONFIG)) {
            if (in != null) props.load(in);
        } catch (Exception ignored) {
            // ignore
        }

        resolvedApiUrl = resolveApiUrl();
    }

    private Config() { /* utility */ }

    private static String resolveApiUrl() {
        // 1) system property
        String v = System.getProperty(SYSTEM_PROP_KEY);
        if (v != null && !v.isBlank()) return v.trim();

        // 2) env var
        v = System.getenv(ENV_VAR_KEY);
        if (v != null && !v.isBlank()) return v.trim();

        // 3) properties from external/classpath
        v = props.getProperty("api.url");
        if (v != null && !v.isBlank()) {
            return v.trim();
        }

        // fallback
        System.out.println("Using default API URL: " + DEFAULT_API_URL);
        return DEFAULT_API_URL;
    }

    public static String getApiUrl() {
        return resolvedApiUrl;
    }

    public static boolean isValidUrl(String u) {
        if (u == null || u.isBlank()) return false;
        try {
            new URL(u);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }
}

