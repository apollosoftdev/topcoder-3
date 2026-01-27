/**
 * Jest Test Setup
 * Initializes test environment with mock DOM and globals
 */

// Mock window.location
delete window.location;
window.location = {
    href: 'http://localhost:8080/',
    hostname: 'localhost',
    origin: 'http://localhost:8080',
    pathname: '/',
    search: '',
    assign: jest.fn(),
    replace: jest.fn(),
    reload: jest.fn()
};

// Mock window.history
window.history = {
    replaceState: jest.fn(),
    pushState: jest.fn()
};

// Mock document.cookie
let cookieStore = {};
Object.defineProperty(document, 'cookie', {
    get: function() {
        return Object.entries(cookieStore)
            .map(([key, value]) => `${key}=${value}`)
            .join('; ');
    },
    set: function(value) {
        const [cookiePair] = value.split(';');
        const [key, val] = cookiePair.split('=');
        if (val && !value.includes('expires=Thu, 01 Jan 1970')) {
            cookieStore[key.trim()] = val.trim();
        } else {
            delete cookieStore[key.trim()];
        }
    }
});

// Helper to clear cookies between tests
global.clearCookies = function() {
    cookieStore = {};
};

// Mock fetch
global.fetch = jest.fn();

// Mock setTimeout/setInterval
jest.useFakeTimers();

// Load auth modules
const fs = require('fs');
const path = require('path');
const vm = require('vm');

// Create a mock for AuthConfig
global.AuthConfig = {
    ENV: 'development',
    AUTH_CONNECTOR_URL: 'https://accounts-auth0.topcoder-dev.com',
    AUTH_URL: 'https://accounts-auth0.topcoder-dev.com',
    ACCOUNTS_APP_URL: 'https://accounts.topcoder-dev.com',
    COOKIE_NAME: 'tcjwt',
    V3_COOKIE_NAME: 'v3jwt',
    REFRESH_COOKIE_NAME: 'tcrft',
    TOKEN_EXPIRATION_OFFSET: 60,
    API_BASE_URL: '/api',
    CLAIMS_NAMESPACE: 'https://topcoder.com/claims/',
    ROLES: {
        ADMIN: 'administrator',
        USER: 'Topcoder User'
    },
    getAuthStatusUrl: function() {
        return '/api/auth/status';
    },
    getMemberProfileUrl: function(handle) {
        return '/api/auth/member/' + encodeURIComponent(handle);
    },
    isDevelopment: function() {
        return true;
    }
};

// Helper to load JS file content
global.loadModule = function(modulePath) {
    const fullPath = path.resolve(__dirname, '../../main/webapp', modulePath);
    const content = fs.readFileSync(fullPath, 'utf8');
    const script = new vm.Script(content);
    script.runInThisContext();
};
