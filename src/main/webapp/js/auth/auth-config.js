/**
 * Topcoder Authentication Configuration
 * Environment-specific configuration for tc-auth-lib integration
 */
const AuthConfig = (function() {
    'use strict';

    // Detect environment based on hostname
    const getEnvironment = function() {
        const hostname = window.location.hostname;
        if (hostname === 'localhost' || hostname === '127.0.0.1') {
            return 'development';
        }
        if (hostname === 'local.topcoder-dev.com') {
            return 'local'; // Special local dev environment
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

    const environments = {
        // Local development with local.topcoder-dev.com (HTTPS)
        // Uses dev auth URLs - the domain is in topcoder-dev.com trusted list
        local: {
            AUTH_CONNECTOR_URL: 'https://accounts-auth0.topcoder-dev.com',
            AUTH_URL: 'https://accounts-auth0.topcoder-dev.com',
            ACCOUNTS_APP_URL: 'https://accounts.topcoder-dev.com',
            AUTH0_CDN_URL: 'https://cdn.auth0.com',
            COOKIE_NAME: 'tcjwt',
            V3_COOKIE_NAME: 'v3jwt',
            REFRESH_COOKIE_NAME: 'tcrft',
            TOKEN_EXPIRATION_OFFSET: 60,
            API_BASE_URL: '/api'
        },
        // Development (localhost) - uses production auth to avoid CSP issues
        development: {
            AUTH_CONNECTOR_URL: 'https://accounts-auth0.topcoder.com',
            AUTH_URL: 'https://accounts-auth0.topcoder.com',
            ACCOUNTS_APP_URL: 'https://accounts.topcoder.com',
            AUTH0_CDN_URL: 'https://cdn.auth0.com',
            COOKIE_NAME: 'tcjwt',
            V3_COOKIE_NAME: 'v3jwt',
            REFRESH_COOKIE_NAME: 'tcrft',
            TOKEN_EXPIRATION_OFFSET: 60,
            API_BASE_URL: '/api'
        },
        // Production
        production: {
            AUTH_CONNECTOR_URL: 'https://accounts-auth0.topcoder.com',
            AUTH_URL: 'https://accounts-auth0.topcoder.com',
            ACCOUNTS_APP_URL: 'https://accounts.topcoder.com',
            AUTH0_CDN_URL: 'https://cdn.auth0.com',
            COOKIE_NAME: 'tcjwt',
            V3_COOKIE_NAME: 'v3jwt',
            REFRESH_COOKIE_NAME: 'tcrft',
            TOKEN_EXPIRATION_OFFSET: 60,
            API_BASE_URL: '/api'
        }
    };

    const currentEnv = getEnvironment();
    const config = environments[currentEnv];

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
            return currentEnv === 'development';
        }
    };
})();

// Export for module systems if available
if (typeof module !== 'undefined' && module.exports) {
    module.exports = AuthConfig;
}
