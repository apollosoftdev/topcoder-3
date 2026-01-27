/**
 * Environment configuration placeholder
 *
 * This file is:
 * - Overwritten by docker-entrypoint.sh at container startup
 * - Injected by server.js for Node.js development
 * - Used as fallback for static file serving
 *
 * Values here are defaults - actual values come from environment variables
 */
window.__ENV__ = window.__ENV__ || {
    NODE_ENV: 'local',
    TC_AUTH_CONNECTOR_URL: 'https://accounts-auth0.topcoder-dev.com',
    TC_AUTH_URL: 'https://accounts-auth0.topcoder-dev.com',
    TC_ACCOUNTS_APP_URL: 'https://accounts.topcoder-dev.com',
    TC_AUTH0_CDN_URL: 'https://cdn.auth0.com',
    TC_COOKIE_NAME: 'tcjwt',
    TC_V3_COOKIE_NAME: 'v3jwt',
    TC_REFRESH_COOKIE_NAME: 'tcrft',
    TC_TOKEN_EXPIRATION_OFFSET: 60,
    API_BASE_URL: '/api'
};
