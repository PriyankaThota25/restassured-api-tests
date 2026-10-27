package com.example.auth;

import java.time.Duration;
import java.util.Properties;

/** Builds the shared TokenManager. Precedence: system property, env var, config.properties. */
public final class AuthConfig {

    private static TokenManager instance;

    private AuthConfig() {
    }

    public static synchronized TokenManager tokenManager(Properties props) {
        if (instance == null) {
            instance = new TokenManager(
                    get(props, "auth.token.url"),
                    get(props, "auth.client.id"),
                    get(props, "auth.client.secret"),
                    get(props, "auth.scope"),
                    Duration.ofSeconds(Long.parseLong(get(props, "auth.expiry.skew.seconds"))));
        }
        return instance;
    }

    private static String get(Properties props, String key) {
        String value = System.getProperty(key);
        if (value == null) {
            value = System.getenv(key.toUpperCase().replace('.', '_'));
        }
        return value != null ? value : props.getProperty(key);
    }
}
