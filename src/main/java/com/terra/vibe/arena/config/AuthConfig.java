package com.terra.vibe.arena.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authentication configuration loaded from properties file.
 * Supports environment-specific configuration (dev/prod).
 */
public class AuthConfig {

    private static final Logger logger = LoggerFactory.getLogger(AuthConfig.class);
    private static final String PROPERTIES_FILE = "auth.properties";
    private static AuthConfig instance;

    private final Properties properties;
    private final boolean isDevelopment;

    // Configuration keys
    public static final String CONNECTOR_URL = "topcoder.auth.connector.url";
    public static final String ACCOUNTS_URL = "topcoder.auth.accounts.url";
    public static final String COOKIE_NAME = "topcoder.auth.cookie.name";
    public static final String COOKIE_NAME_V3 = "topcoder.auth.cookie.name.v3";
    public static final String COOKIE_NAME_REFRESH = "topcoder.auth.cookie.name.refresh";
    public static final String TOKEN_EXPIRATION_OFFSET = "topcoder.auth.token.expiration.offset";
    public static final String JWKS_URL = "topcoder.auth.jwks.url";
    public static final String ISSUER = "topcoder.auth.issuer";
    public static final String CLAIMS_NAMESPACE = "topcoder.auth.claims.namespace";
    // Role configuration property keys (not credentials)
    public static final String ROLE_ADMIN_KEY = "topcoder.auth.role.admin"; // nosemgrep: hardcoded_username
    public static final String ROLE_MEMBER_KEY = "topcoder.auth.role.member"; // nosemgrep: hardcoded_username

    private AuthConfig() {
        this.properties = loadProperties();
        this.isDevelopment = detectEnvironment();
    }

    /**
     * Get singleton instance of AuthConfig.
     */
    public static synchronized AuthConfig getInstance() {
        if (instance == null) {
            instance = new AuthConfig();
        }
        return instance;
    }

    /**
     * Load properties from classpath.
     */
    private Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (input != null) {
                props.load(input);
                logger.info("Loaded authentication configuration from {}", PROPERTIES_FILE);
            } else {
                logger.warn("Authentication properties file not found: {}", PROPERTIES_FILE);
            }
        } catch (IOException e) {
            logger.error("Failed to load authentication properties", e);
        }
        return props;
    }

    /**
     * Detect if running in development environment.
     */
    private boolean detectEnvironment() {
        String env = System.getenv("ENVIRONMENT");
        if (env == null) {
            env = System.getProperty("environment", "production");
        }
        return "development".equalsIgnoreCase(env) || "dev".equalsIgnoreCase(env);
    }

    /**
     * Get property value with optional development override.
     */
    public String getProperty(String key) {
        if (isDevelopment) {
            String devKey = key + ".dev";
            String devValue = properties.getProperty(devKey);
            if (devValue != null) {
                return devValue;
            }
        }
        return properties.getProperty(key);
    }

    /**
     * Get property with default value.
     */
    public String getProperty(String key, String defaultValue) {
        String value = getProperty(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Get integer property with default value.
     */
    public int getIntProperty(String key, int defaultValue) {
        String value = getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                logger.warn("Invalid integer value for property {}: {}", key, value);
            }
        }
        return defaultValue;
    }

    // Convenience methods for common properties

    public String getConnectorUrl() {
        return getProperty(CONNECTOR_URL, "https://accounts-auth0.topcoder.com");
    }

    public String getAccountsUrl() {
        return getProperty(ACCOUNTS_URL, "https://accounts.topcoder.com");
    }

    public String getCookieName() {
        return getProperty(COOKIE_NAME, "tcjwt");
    }

    public String getV3CookieName() {
        return getProperty(COOKIE_NAME_V3, "v3jwt");
    }

    public String getRefreshCookieName() {
        return getProperty(COOKIE_NAME_REFRESH, "tcrft");
    }

    public int getTokenExpirationOffset() {
        return getIntProperty(TOKEN_EXPIRATION_OFFSET, 60);
    }

    public String getJwksUrl() {
        return getProperty(JWKS_URL, "https://topcoder.auth0.com/.well-known/jwks.json");
    }

    public String getIssuer() {
        return getProperty(ISSUER, "https://topcoder.auth0.com/");
    }

    public String getClaimsNamespace() {
        return getProperty(CLAIMS_NAMESPACE, "https://topcoder.com/claims/");
    }

    public String getAdminRole() {
        return getProperty(ROLE_ADMIN_KEY, "administrator");
    }

    public String getMemberRole() {
        // nosemgrep: hardcoded_username
        return getProperty(ROLE_MEMBER_KEY, "Topcoder User");
    }

    public boolean isDevelopment() {
        return isDevelopment;
    }
}
