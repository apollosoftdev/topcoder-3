/**
 * Topcoder Authentication Configuration
 * Environment-specific configuration for tc-auth-lib integration
 *
 * Configuration is loaded from:
 * 1. window.__ENV__ (injected by server from .env file)
 * 2. Fallback defaults for production
 */
const AuthConfig = (function() {
    'use strict';

    // Get environment variables from server injection or use defaults
    const env = window.__ENV__ || {};

    // Detect environment based on hostname or env variable
    const getEnvironment = function() {
        // Check injected environment first
        if (env.NODE_ENV) {
            return env.NODE_ENV;
        }

        const hostname = window.location.hostname;
        if (hostname === 'localhost' || hostname === '127.0.0.1') {
            return 'development';
        }
        if (hostname === 'local.topcoder-dev.com') {
            return 'local';
        }
        if (hostname.includes('dev.') || hostname.includes('-dev')) {
            return 'development';
        }
        return 'production';
    };

    // Check if running on localhost or local.topcoder-dev.com
    const isLocalhost = window.location.hostname === 'localhost' ||
                        window.location.hostname === '127.0.0.1';
    const isLocalTopcoder = window.location.hostname === 'local.topcoder-dev.com';

    const currentEnv = getEnvironment();

    // Default configuration (production fallbacks)
    const defaults = {
        AUTH_CONNECTOR_URL: 'https://accounts-auth0.topcoder.com',
        AUTH_URL: 'https://accounts-auth0.topcoder.com',
        ACCOUNTS_APP_URL: 'https://accounts.topcoder.com',
        AUTH0_CDN_URL: 'https://cdn.auth0.com',
        COOKIE_NAME: 'tcjwt',
        V3_COOKIE_NAME: 'v3jwt',
        REFRESH_COOKIE_NAME: 'tcrft',
        TOKEN_EXPIRATION_OFFSET: 60,
        API_BASE_URL: '/api'
    };

    // Build configuration from env or defaults
    const config = {
        AUTH_CONNECTOR_URL: env.TC_AUTH_CONNECTOR_URL || defaults.AUTH_CONNECTOR_URL,
        AUTH_URL: env.TC_AUTH_URL || defaults.AUTH_URL,
        ACCOUNTS_APP_URL: env.TC_ACCOUNTS_APP_URL || defaults.ACCOUNTS_APP_URL,
        AUTH0_CDN_URL: env.TC_AUTH0_CDN_URL || defaults.AUTH0_CDN_URL,
        COOKIE_NAME: env.TC_COOKIE_NAME || defaults.COOKIE_NAME,
        V3_COOKIE_NAME: env.TC_V3_COOKIE_NAME || defaults.V3_COOKIE_NAME,
        REFRESH_COOKIE_NAME: env.TC_REFRESH_COOKIE_NAME || defaults.REFRESH_COOKIE_NAME,
        TOKEN_EXPIRATION_OFFSET: env.TC_TOKEN_EXPIRATION_OFFSET || defaults.TOKEN_EXPIRATION_OFFSET,
        API_BASE_URL: env.API_BASE_URL || defaults.API_BASE_URL
    };

    // Log configuration in development
    if (currentEnv !== 'production') {
        console.log('AuthConfig loaded:', {
            env: currentEnv,
            AUTH_CONNECTOR_URL: config.AUTH_CONNECTOR_URL,
            isLocalhost: isLocalhost,
            isLocalTopcoder: isLocalTopcoder
        });
    }

    return {
        // Current environment
        ENV: currentEnv,

        // Whether running on localhost or local.topcoder-dev.com
        IS_LOCALHOST: isLocalhost,
        IS_LOCAL_TOPCODER: isLocalTopcoder,

        // Authentication URLs
        AUTH_CONNECTOR_URL: config.AUTH_CONNECTOR_URL,
        AUTH_URL: config.AUTH_URL,
        ACCOUNTS_APP_URL: config.ACCOUNTS_APP_URL,
        AUTH0_CDN_URL: config.AUTH0_CDN_URL,

        // Cookie configuration
        COOKIE_NAME: config.COOKIE_NAME,
        V3_COOKIE_NAME: config.V3_COOKIE_NAME,
        REFRESH_COOKIE_NAME: config.REFRESH_COOKIE_NAME,

        // Token configuration
        TOKEN_EXPIRATION_OFFSET: config.TOKEN_EXPIRATION_OFFSET,

        // API configuration
        API_BASE_URL: config.API_BASE_URL,

        // Topcoder claim namespaces (for V3 tokens)
        CLAIMS_NAMESPACE: 'https://topcoder.com/claims/',

        // Roles
        ROLES: {
            ADMIN: 'administrator',
            USER: 'Topcoder User'
        },

        // Get full auth status endpoint URL
        getAuthStatusUrl: function() {
            return config.API_BASE_URL + '/auth/status';
        },

        // Get member profile endpoint URL
        getMemberProfileUrl: function(handle) {
            return config.API_BASE_URL + '/auth/member/' + encodeURIComponent(handle);
        },

        // Check if environment is development
        isDevelopment: function() {
            return currentEnv === 'development' || currentEnv === 'local';
        },

        // Check if using dev auth URLs
        isDevAuth: function() {
            return config.AUTH_CONNECTOR_URL.includes('topcoder-dev.com');
        }
    };
})();

// Export for module systems if available
if (typeof module !== 'undefined' && module.exports) {
    module.exports = AuthConfig;
}
