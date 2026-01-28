/**
 * Topcoder Authentication Service
 * Handles authentication with tc-auth-lib, token management, and user session
 *
 * This service integrates with Topcoder's tc-auth-lib library pattern:
 * - Uses iframe-based connector for token refresh
 * - Supports both V2 (tcjwt cookie) and V3 (connector) tokens
 * - Automatically refreshes tokens before expiration
 */
const AuthService = (function() {
    'use strict';

    // Private state - stored in memory only (not localStorage)
    let _memberInfo = null;
    let _isInitialized = false;
    let _connectorReady = false;
    let _initPromise = null;
    let _refreshTimer = null;
    let _currentToken = null;

    // Error types
    const ErrorTypes = {
        INIT_FAILED: 'INIT_FAILED',
        TOKEN_EXPIRED: 'TOKEN_EXPIRED',
        TOKEN_INVALID: 'TOKEN_INVALID',
        NETWORK_ERROR: 'NETWORK_ERROR',
        SERVICE_UNAVAILABLE: 'SERVICE_UNAVAILABLE',
        UNAUTHORIZED: 'UNAUTHORIZED'
    };

    // Custom events for authentication state changes
    const Events = {
        AUTH_SUCCESS: 'tc-auth-success',
        AUTH_FAILURE: 'tc-auth-failure',
        AUTH_LOGOUT: 'tc-auth-logout',
        AUTH_STATE_CHANGE: 'tc-auth-state-change',
        TOKEN_REFRESHED: 'tc-auth-token-refreshed'
    };

    /**
     * Initialize the authentication connector
     * Configures tc-auth-lib iframe communication
     */
    const init = function() {
        if (_initPromise) {
            return _initPromise;
        }

        _initPromise = new Promise(function(resolve, reject) {
            try {
                // On localhost, skip iframe connector to avoid CSP issues
                // Use direct auth flow instead (redirect-based)
                if (AuthConfig.IS_LOCALHOST) {
                    console.log('Auth: Running on localhost, skipping iframe connector');
                    _isInitialized = true;
                    _connectorReady = false; // Iframe won't work on localhost
                    // Check for existing token from cookie
                    return checkExistingSession()
                        .then(function(authenticated) {
                            if (authenticated) {
                                dispatchEvent(Events.AUTH_SUCCESS, { memberInfo: _memberInfo });
                            }
                            resolve(true);
                        })
                        .catch(function() {
                            resolve(false);
                        });
                }

                // Configure connector (following tc-auth-lib pattern)
                configureConnector();

                // Wait for connector to be ready
                waitForConnector()
                    .then(function() {
                        _connectorReady = true;
                        _isInitialized = true;
                        // Check for existing session
                        return checkExistingSession();
                    })
                    .then(function(authenticated) {
                        if (authenticated) {
                            dispatchEvent(Events.AUTH_SUCCESS, { memberInfo: _memberInfo });
                        }
                        resolve(true);
                    })
                    .catch(function(error) {
                        console.error('Auth initialization failed:', error);
                        _isInitialized = true; // Mark as initialized even on failure
                        dispatchEvent(Events.AUTH_FAILURE, { error: error.message });
                        resolve(false); // Resolve with false, don't reject
                    });
            } catch (error) {
                console.error('Auth init exception:', error);
                reject(createError(ErrorTypes.INIT_FAILED, error.message));
            }
        });

        return _initPromise;
    };

    /**
     * Configure the authentication connector
     * Following tc-auth-lib configureConnector() pattern
     */
    const configureConnector = function() {
        // Skip iframe on localhost to avoid CSP issues
        if (AuthConfig.IS_LOCALHOST) {
            console.log('Auth: Skipping iframe creation on localhost');
            return;
        }

        // Check if connector iframe already exists
        let connector = document.getElementById('tc-accounts-iframe');
        if (!connector) {
            connector = document.getElementById('tc-auth-connector');
        }

        if (!connector) {
            // Create the connector iframe (tc-auth-lib pattern)
            connector = document.createElement('iframe');
            connector.id = 'tc-accounts-iframe';
            connector.src = AuthConfig.AUTH_CONNECTOR_URL;
            connector.width = 0;
            connector.height = 0;
            connector.frameBorder = 0;
            connector.style.display = 'none';
            connector.title = 'Topcoder Authentication Connector';
            document.body.appendChild(connector);
        }
    };

    /**
     * Wait for the connector iframe to be ready
     */
    const waitForConnector = function() {
        return new Promise(function(resolve, reject) {
            let attempts = 0;
            const maxAttempts = 50; // 5 seconds total
            const interval = 100;

            const check = function() {
                attempts++;
                const connector = document.getElementById('tc-accounts-iframe') ||
                                  document.getElementById('tc-auth-connector');

                if (connector && connector.contentWindow) {
                    // Add load event listener for proper initialization
                    if (connector.contentDocument && connector.contentDocument.readyState === 'complete') {
                        resolve();
                        return;
                    }

                    connector.onload = function() {
                        resolve();
                    };

                    // If already loaded
                    if (attempts > 10) {
                        resolve();
                        return;
                    }
                }

                if (attempts >= maxAttempts) {
                    reject(new Error('Connector iframe timeout'));
                    return;
                }

                setTimeout(check, interval);
            };

            check();
        });
    };

    /**
     * Check for existing authentication session
     */
    const checkExistingSession = function() {
        return getFreshToken()
            .then(function(token) {
                if (token) {
                    _currentToken = token;
                    const decoded = decodeToken(token);
                    if (decoded && !isTokenExpired(decoded)) {
                        _memberInfo = extractMemberInfo(decoded);
                        // Schedule automatic token refresh
                        scheduleTokenRefresh(decoded);
                        return true;
                    }
                }
                return false;
            })
            .catch(function() {
                return false;
            });
    };

    /**
     * Get fresh token - following tc-auth-lib getFreshToken() pattern
     * First checks tcjwt cookie, then requests from connector if expired
     */
    const getFreshToken = function() {
        return new Promise(function(resolve, reject) {
            // Try to read V2 token from cookie first (tcjwt)
            const tokenV2 = getCookie(AuthConfig.COOKIE_NAME);

            if (tokenV2) {
                const decoded = decodeToken(tokenV2);
                // 65 is the offset in seconds, same as tc-auth-lib
                if (decoded && !isTokenExpired(decoded, 65)) {
                    resolve(tokenV2);
                    return;
                }
            }

            // If no valid token in cookie, try to refresh from connector
            if (_connectorReady) {
                requestTokenRefresh()
                    .then(function() {
                        // After refresh, read the new cookie
                        const newToken = getCookie(AuthConfig.COOKIE_NAME);
                        if (newToken) {
                            resolve(newToken);
                        } else {
                            resolve(null);
                        }
                    })
                    .catch(function() {
                        resolve(null);
                    });
            } else {
                resolve(null);
            }
        });
    };

    /**
     * Get token - wrapper for backward compatibility
     */
    const getToken = function() {
        if (_currentToken) {
            const decoded = decodeToken(_currentToken);
            if (decoded && !isTokenExpired(decoded)) {
                return Promise.resolve(_currentToken);
            }
        }
        return getFreshToken();
    };

    /**
     * Request token refresh from connector iframe
     * Following tc-auth-lib proxyCall pattern
     *
     * Note: On localhost, iframe postMessage won't work due to cross-origin
     * restrictions. Token refresh will fail silently - users must re-login
     * when token expires. This is expected for local development.
     */
    const requestTokenRefresh = function() {
        return new Promise(function(resolve, reject) {
            // Skip iframe refresh on localhost - cross-origin won't work
            const isLocalhost = window.location.hostname === 'localhost' ||
                                window.location.hostname === '127.0.0.1';

            const connector = document.getElementById('tc-accounts-iframe') ||
                              document.getElementById('tc-auth-connector');

            console.log('Auth: requestTokenRefresh - connector found:', !!connector);

            if (!connector || !connector.contentWindow) {
                console.log('Auth: Connector not available or no contentWindow');
                if (isLocalhost) {
                    resolve(null); // Silently fail on localhost
                } else {
                    reject(new Error('Connector not available'));
                }
                return;
            }

            const timeout = setTimeout(function() {
                console.log('Auth: Token refresh timeout');
                window.removeEventListener('message', handler);
                if (isLocalhost) {
                    resolve(null); // Silently fail on localhost
                } else {
                    reject(new Error('Token refresh timeout'));
                }
            }, isLocalhost ? 3000 : 10000); // Shorter timeout on localhost

            const handler = function(event) {
                console.log('Auth: Message received from:', event.origin);

                // Validate origin
                try {
                    const connectorOrigin = new URL(AuthConfig.AUTH_CONNECTOR_URL).origin;
                    if (event.origin !== connectorOrigin) {
                        console.log('Auth: Origin mismatch, expected:', connectorOrigin);
                        return;
                    }
                } catch (e) {
                    return;
                }

                console.log('Auth: Message data:', event.data);

                // Handle response (tc-auth-lib format: SUCCESS or FAILURE)
                const safeFormat = event.data &&
                    (event.data.type === 'SUCCESS' || event.data.type === 'FAILURE');

                if (safeFormat) {
                    clearTimeout(timeout);
                    window.removeEventListener('message', handler);

                    if (event.data.type === 'SUCCESS') {
                        const newToken = getCookie(AuthConfig.COOKIE_NAME);
                        console.log('Auth: SUCCESS received, cookie found:', !!newToken);
                        if (newToken) {
                            _currentToken = newToken;
                            dispatchEvent(Events.TOKEN_REFRESHED, { token: newToken });
                            resolve(newToken);
                        } else {
                            reject(new Error('tcjwt cookie not found after refresh'));
                        }
                    } else {
                        console.log('Auth: FAILURE received');
                        reject(new Error('Unable to refresh token'));
                    }
                }
            };

            window.addEventListener('message', handler);

            // Send refresh request (tc-auth-lib format)
            try {
                const payload = { type: 'REFRESH_TOKEN' };
                console.log('Auth: Sending REFRESH_TOKEN to connector');
                connector.contentWindow.postMessage(payload, AuthConfig.AUTH_CONNECTOR_URL);
            } catch (e) {
                // postMessage may fail on localhost due to cross-origin
                console.warn('Auth: postMessage failed:', e.message);
                if (!isLocalhost) {
                    console.warn('Token refresh postMessage failed:', e.message);
                }
            }
        });
    };

    /**
     * Schedule automatic token refresh before expiration
     */
    const scheduleTokenRefresh = function(decodedToken) {
        // Clear any existing timer
        if (_refreshTimer) {
            clearTimeout(_refreshTimer);
            _refreshTimer = null;
        }

        if (!decodedToken || !decodedToken.exp) {
            return;
        }

        // Calculate time until refresh needed (using same offset as tc-auth-lib)
        const expirationMs = decodedToken.exp * 1000;
        const offsetMs = AuthConfig.TOKEN_EXPIRATION_OFFSET * 1000;
        const refreshTime = expirationMs - offsetMs - Date.now();

        if (refreshTime > 0) {
            _refreshTimer = setTimeout(function() {
                getFreshToken()
                    .then(function(token) {
                        if (token) {
                            const decoded = decodeToken(token);
                            if (decoded) {
                                _memberInfo = extractMemberInfo(decoded);
                                scheduleTokenRefresh(decoded);
                            }
                        } else {
                            // Token refresh failed, user needs to re-authenticate
                            _memberInfo = null;
                            dispatchEvent(Events.AUTH_STATE_CHANGE, {
                                authenticated: false,
                                reason: 'token_expired'
                            });
                        }
                    })
                    .catch(function(error) {
                        console.error('Automatic token refresh failed:', error);
                    });
            }, refreshTime);
        }
    };

    /**
     * Decode JWT token and extract payload
     * Following tc-auth-lib decodeToken pattern
     */
    const decodeToken = function(token) {
        if (!token) return null;

        try {
            const parts = token.split('.');
            if (parts.length !== 3) {
                return null;
            }

            // URL-safe base64 decode (tc-auth-lib pattern)
            let payload = parts[1];
            payload = payload.replace(/-/g, '+').replace(/_/g, '/');

            // Add padding if needed
            switch (payload.length % 4) {
                case 2:
                    payload += '==';
                    break;
                case 3:
                    payload += '=';
                    break;
            }

            const decoded = decodeURIComponent(escape(atob(payload)));
            const parsed = JSON.parse(decoded);

            // Extract claims following tc-auth-lib pattern (handles namespaced claims)
            // Use Object.keys for safer iteration (avoids prototype pollution)
            const keys = Object.keys(parsed);

            // userId - find any key containing 'userId'
            // Keys are from Object.keys(parsed), not user input - safe access
            if (!parsed.userId) {
                for (let i = 0; i < keys.length; i++) {
                    const k = keys[i];
                    if (k.indexOf('userId') !== -1 && Object.prototype.hasOwnProperty.call(parsed, k)) {
                        // nosemgrep: detect-object-injection
                        parsed.userId = parseInt(parsed[k], 10);
                        break;
                    }
                }
            }

            // handle - find any key containing 'handle'
            // Keys are from Object.keys(parsed), not user input - safe access
            if (!parsed.handle) {
                for (let i = 0; i < keys.length; i++) {
                    const k = keys[i];
                    if (k.indexOf('handle') !== -1 && Object.prototype.hasOwnProperty.call(parsed, k)) {
                        // nosemgrep: detect-object-injection
                        parsed.handle = parsed[k];
                        break;
                    }
                }
            }

            // roles - find any key containing 'roles'
            // Keys are from Object.keys(parsed), not user input - safe access
            if (!parsed.roles) {
                for (let i = 0; i < keys.length; i++) {
                    const k = keys[i];
                    if (k.indexOf('roles') !== -1 && Object.prototype.hasOwnProperty.call(parsed, k)) {
                        // nosemgrep: detect-object-injection
                        parsed.roles = parsed[k];
                        break;
                    }
                }
            }

            return parsed;
        } catch (error) {
            console.error('Failed to decode token:', error);
            return null;
        }
    };

    /**
     * Check if token is expired
     * Following tc-auth-lib isTokenExpired pattern
     */
    const isTokenExpired = function(decodedToken, offsetSeconds) {
        if (!decodedToken || typeof decodedToken.exp === 'undefined') {
            return false; // No expiration means not expired (tc-auth-lib behavior)
        }

        const offset = offsetSeconds || AuthConfig.TOKEN_EXPIRATION_OFFSET;
        const expirationDate = new Date(0);
        expirationDate.setUTCSeconds(decodedToken.exp);

        return !(expirationDate.valueOf() > (new Date().valueOf() + (offset * 1000)));
    };

    /**
     * Extract member info from decoded token
     * Supports both V2 (direct claims) and V3 (namespaced claims) formats
     */
    const extractMemberInfo = function(decodedToken) {
        if (!decodedToken) return null;

        // After tc-auth-lib processing, handle/userId/roles should be at top level
        const handle = decodedToken.handle;
        const userId = decodedToken.userId || decodedToken.sub;
        const email = decodedToken.email;
        const roles = decodedToken.roles || [];

        if (!handle) {
            return null;
        }

        // Determine if V3 token by checking for namespaced claims
        const isV3Token = Object.keys(decodedToken).some(function(key) {
            return key.indexOf('https://topcoder.com') !== -1;
        });

        return {
            handle: handle,
            userId: userId,
            email: email,
            roles: Array.isArray(roles) ? roles : [],
            isV3Token: isV3Token
        };
    };

    /**
     * Check if user is authenticated
     */
    const isAuthenticated = function() {
        return _memberInfo !== null;
    };

    /**
     * Get member handle from current session
     */
    const getMemberHandle = function() {
        return _memberInfo ? _memberInfo.handle : null;
    };

    /**
     * Get full member info from current session
     */
    const getMemberInfo = function() {
        return _memberInfo ? Object.assign({}, _memberInfo) : null;
    };

    /**
     * Check if current user has a specific role
     */
    const hasRole = function(role) {
        if (!_memberInfo || !_memberInfo.roles) {
            return false;
        }
        return _memberInfo.roles.some(function(r) {
            return r.toLowerCase() === role.toLowerCase();
        });
    };

    /**
     * Check if current user is an admin
     */
    const isAdmin = function() {
        return hasRole(AuthConfig.ROLES.ADMIN);
    };

    /**
     * Validate return URL to prevent open redirect attacks.
     * Only allows URLs on the same origin or trusted topcoder domains.
     */
    const validateReturnUrl = function(url) {
        if (!url) {
            return window.location.href;
        }

        // Allow relative URLs
        if (url.startsWith('/') && !url.startsWith('//')) {
            return window.location.origin + url;
        }

        try {
            const parsed = new URL(url);
            const host = parsed.hostname.toLowerCase();

            // Allow same origin
            if (parsed.origin === window.location.origin) {
                return url;
            }

            // Allow topcoder domains
            if (host.endsWith('.topcoder.com') || host.endsWith('.topcoder-dev.com') ||
                host === 'topcoder.com' || host === 'topcoder-dev.com') {
                return url;
            }

            // Reject other external URLs - use current page instead
            console.warn('Auth: Rejected external return URL:', url);
            return window.location.href;
        } catch (e) {
            // Invalid URL format - use current page
            return window.location.href;
        }
    };

    /**
     * Generate login URL with return URL
     * Following platform-ui pattern: ${authUrl}?retUrl=${encodedRetUrl}
     */
    // nosemgrep: js-open-redirect-from-function
    const generateLoginUrl = function(returnUrl) {
        // Validate return URL to prevent open redirect (see validateReturnUrl above)
        const safeRetUrl = validateReturnUrl(returnUrl) || window.location.href.match(/[^?]*/)?.[0] || window.location.href;
        // Add a marker to detect post-login redirect
        const retUrlWithMarker = safeRetUrl + (safeRetUrl.indexOf('?') === -1 ? '?' : '&') + '_auth=1';
        const encodedReturnUrl = encodeURIComponent(retUrlWithMarker);
        return AuthConfig.AUTH_CONNECTOR_URL + '?retUrl=' + encodedReturnUrl;
    };

    /**
     * Generate logout URL with return URL
     * Following platform-ui pattern: ${authUrl}?logout=true&retUrl=${encodedRetUrl}
     */
    // nosemgrep: js-open-redirect-from-function
    const generateLogoutUrl = function(returnUrl) {
        // Validate return URL to prevent open redirect (see validateReturnUrl above)
        const safeRetUrl = validateReturnUrl(returnUrl) || 'https://' + window.location.host;
        const encodedReturnUrl = encodeURIComponent(safeRetUrl);
        return AuthConfig.AUTH_CONNECTOR_URL + '?logout=true&retUrl=' + encodedReturnUrl;
    };

    /**
     * Redirect to login page
     * Return URL is validated by generateLoginUrl -> validateReturnUrl
     */
    const login = function(returnUrl) {
        // nosemgrep: js-open-redirect-from-function
        window.location.href = generateLoginUrl(returnUrl);
    };

    /**
     * Clear session and redirect to logout
     */
    const logout = function(returnUrl) {
        // Clear refresh timer
        if (_refreshTimer) {
            clearTimeout(_refreshTimer);
            _refreshTimer = null;
        }

        // Clear local state
        _memberInfo = null;
        _currentToken = null;

        // Dispatch logout event
        dispatchEvent(Events.AUTH_LOGOUT, {});

        // Clear cookies (they're httpOnly so this may not work, but try)
        clearCookie(AuthConfig.COOKIE_NAME);
        clearCookie(AuthConfig.V3_COOKIE_NAME);
        clearCookie(AuthConfig.REFRESH_COOKIE_NAME);

        // Redirect to logout URL (validated by generateLogoutUrl -> validateReturnUrl)
        // nosemgrep: js-open-redirect-from-function
        window.location.href = generateLogoutUrl(returnUrl);
    };

    /**
     * Get cookie value by name
     * Cookie names are hardcoded constants, not user input
     */
    const getCookie = function(name) {
        const nameEQ = name + '=';
        const cookieList = document.cookie.split(';');
        for (let i = 0; i < cookieList.length; i++) {
            // nosemgrep: detect-object-injection
            let cookie = cookieList[i].trim();
            if (cookie.indexOf(nameEQ) === 0) {
                return cookie.substring(nameEQ.length);
            }
        }
        return null;
    };

    /**
     * Clear a cookie
     */
    const clearCookie = function(name) {
        document.cookie = name + '=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;';
    };

    /**
     * Dispatch custom authentication event
     */
    const dispatchEvent = function(eventName, detail) {
        const event = new CustomEvent(eventName, {
            bubbles: true,
            detail: detail
        });
        document.dispatchEvent(event);
    };

    /**
     * Create standardized error object
     */
    const createError = function(type, message, details) {
        return {
            type: type,
            message: message,
            details: details,
            timestamp: new Date().toISOString()
        };
    };

    /**
     * Verify authentication status with backend
     */
    const verifyWithBackend = function() {
        return new Promise(function(resolve, reject) {
            getToken()
                .then(function(token) {
                    if (!token) {
                        resolve({ authenticated: false });
                        return;
                    }

                    return fetch(AuthConfig.getAuthStatusUrl(), {
                        method: 'GET',
                        credentials: 'include',
                        headers: {
                            'Authorization': 'Bearer ' + token,
                            'Content-Type': 'application/json'
                        }
                    });
                })
                .then(function(response) {
                    if (!response) return;

                    if (response.ok) {
                        return response.json();
                    }

                    if (response.status === 401) {
                        _memberInfo = null;
                        resolve({ authenticated: false });
                        return;
                    }

                    throw createError(ErrorTypes.SERVICE_UNAVAILABLE, 'Auth service error');
                })
                .then(function(data) {
                    if (data) {
                        if (data.authenticated && data.memberInfo) {
                            _memberInfo = data.memberInfo;
                        }
                        resolve(data);
                    }
                })
                .catch(function(error) {
                    console.error('Backend verification failed:', error);
                    reject(createError(ErrorTypes.NETWORK_ERROR, error.message));
                });
        });
    };

    /**
     * Get user profile by handle
     */
    const getMemberProfile = function(handle) {
        return new Promise(function(resolve, reject) {
            fetch(AuthConfig.getMemberProfileUrl(handle), {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            })
            .then(function(response) {
                if (response.ok) {
                    return response.json();
                }
                if (response.status === 404) {
                    reject(createError(ErrorTypes.UNAUTHORIZED, 'Member not found'));
                    return;
                }
                throw new Error('Failed to fetch profile');
            })
            .then(function(data) {
                resolve(data);
            })
            .catch(function(error) {
                reject(createError(ErrorTypes.NETWORK_ERROR, error.message));
            });
        });
    };

    /**
     * Reset initialization state to allow re-initialization
     */
    const reset = function() {
        _initPromise = null;
        _isInitialized = false;
    };

    // Public API
    return {
        // Initialization
        init: init,
        reset: reset,
        isInitialized: function() { return _isInitialized; },

        // Token management (following tc-auth-lib API)
        getToken: getToken,
        getFreshToken: getFreshToken,
        decodeToken: decodeToken,
        isTokenExpired: isTokenExpired,

        // Authentication state
        isAuthenticated: isAuthenticated,
        getMemberHandle: getMemberHandle,
        getMemberInfo: getMemberInfo,
        hasRole: hasRole,
        isAdmin: isAdmin,

        // Login/Logout
        generateLoginUrl: generateLoginUrl,
        generateLogoutUrl: generateLogoutUrl,
        login: login,
        logout: logout,

        // Backend integration
        verifyWithBackend: verifyWithBackend,
        getMemberProfile: getMemberProfile,

        // Error types and events for consumers
        ErrorTypes: ErrorTypes,
        Events: Events
    };
})();

// Export for module systems if available
if (typeof module !== 'undefined' && module.exports) {
    module.exports = AuthService;
}
