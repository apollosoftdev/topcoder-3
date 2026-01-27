/**
 * Topcoder Authentication Service
 * Handles authentication with tc-auth-lib, token management, and user session
 */
const AuthService = (function() {
    'use strict';

    // Private state - stored in memory only (not localStorage)
    let _memberInfo = null;
    let _isInitialized = false;
    let _connectorReady = false;
    let _initPromise = null;

    // Error types
    const ErrorTypes = {
        INIT_FAILED: 'INIT_FAILED',
        TOKEN_EXPIRED: 'TOKEN_EXPIRED',
        TOKEN_INVALID: 'TOKEN_INVALID',
        NETWORK_ERROR: 'NETWORK_ERROR',
        SERVICE_UNAVAILABLE: 'SERVICE_UNAVAILABLE',
        UNAUTHORIZED: 'UNAUTHORIZED'
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
                // Check if tc-auth-lib connector is available
                const connector = document.getElementById('tc-auth-connector');
                if (!connector) {
                    console.warn('Auth connector iframe not found, creating dynamically');
                    createConnectorIframe();
                }

                // Wait for connector to be ready
                waitForConnector()
                    .then(function() {
                        _connectorReady = true;
                        _isInitialized = true;
                        // Check for existing session
                        return checkExistingSession();
                    })
                    .then(function() {
                        resolve(true);
                    })
                    .catch(function(error) {
                        console.error('Auth initialization failed:', error);
                        _isInitialized = true; // Mark as initialized even on failure
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
     * Create the connector iframe dynamically if not present
     */
    const createConnectorIframe = function() {
        const iframe = document.createElement('iframe');
        iframe.id = 'tc-auth-connector';
        iframe.src = AuthConfig.AUTH_CONNECTOR_URL + '/connector.html';
        iframe.style.display = 'none';
        iframe.title = 'Authentication Connector';
        document.body.appendChild(iframe);
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
                const connector = document.getElementById('tc-auth-connector');

                if (connector && connector.contentWindow) {
                    resolve();
                    return;
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
        return getToken()
            .then(function(token) {
                if (token) {
                    const decoded = decodeToken(token);
                    if (decoded && !isTokenExpired(decoded)) {
                        _memberInfo = extractMemberInfo(decoded);
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
     * Get fresh token from tc-auth-lib
     * Wrapper for getFreshToken() - refreshes if needed
     */
    const getToken = function() {
        return new Promise(function(resolve, reject) {
            // Try to read token from cookie
            const token = getCookie(AuthConfig.COOKIE_NAME) || getCookie(AuthConfig.V3_COOKIE_NAME);

            if (token) {
                const decoded = decodeToken(token);
                if (decoded && !isTokenExpired(decoded)) {
                    resolve(token);
                    return;
                }
            }

            // If no valid token in cookie, try to get from connector
            if (_connectorReady) {
                requestTokenFromConnector()
                    .then(resolve)
                    .catch(function() {
                        resolve(null); // Return null instead of rejecting
                    });
            } else {
                resolve(null);
            }
        });
    };

    /**
     * Request token from the connector iframe via postMessage
     */
    const requestTokenFromConnector = function() {
        return new Promise(function(resolve, reject) {
            const connector = document.getElementById('tc-auth-connector');
            if (!connector || !connector.contentWindow) {
                reject(new Error('Connector not available'));
                return;
            }

            const timeout = setTimeout(function() {
                window.removeEventListener('message', handler);
                reject(new Error('Token request timeout'));
            }, 5000);

            const handler = function(event) {
                if (event.origin !== new URL(AuthConfig.AUTH_CONNECTOR_URL).origin) {
                    return;
                }

                if (event.data && event.data.type === 'AUTH_TOKEN_RESPONSE') {
                    clearTimeout(timeout);
                    window.removeEventListener('message', handler);
                    resolve(event.data.token);
                }
            };

            window.addEventListener('message', handler);

            connector.contentWindow.postMessage(
                { type: 'GET_AUTH_TOKEN' },
                AuthConfig.AUTH_CONNECTOR_URL
            );
        });
    };

    /**
     * Decode JWT token and extract payload
     */
    const decodeToken = function(token) {
        if (!token) return null;

        try {
            const parts = token.split('.');
            if (parts.length !== 3) {
                return null;
            }

            const payload = parts[1];
            const decoded = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
            return JSON.parse(decoded);
        } catch (error) {
            console.error('Failed to decode token:', error);
            return null;
        }
    };

    /**
     * Check if token is expired
     */
    const isTokenExpired = function(decodedToken) {
        if (!decodedToken || !decodedToken.exp) {
            return true;
        }

        const now = Math.floor(Date.now() / 1000);
        const expirationWithOffset = decodedToken.exp - AuthConfig.TOKEN_EXPIRATION_OFFSET;
        return now >= expirationWithOffset;
    };

    /**
     * Extract member info from decoded token
     * Supports both V2 (HS256) and V3 (RS256) token formats
     */
    const extractMemberInfo = function(decodedToken) {
        if (!decodedToken) return null;

        // V3 token format - namespaced claims
        const namespace = AuthConfig.CLAIMS_NAMESPACE;
        if (decodedToken[namespace + 'handle']) {
            return {
                handle: decodedToken[namespace + 'handle'],
                userId: decodedToken[namespace + 'userId'] || decodedToken.sub,
                email: decodedToken[namespace + 'email'] || decodedToken.email,
                roles: decodedToken[namespace + 'roles'] || [],
                isV3Token: true
            };
        }

        // V2 token format - direct claims
        if (decodedToken.handle) {
            return {
                handle: decodedToken.handle,
                userId: decodedToken.userId || decodedToken.sub,
                email: decodedToken.email,
                roles: decodedToken.roles || [],
                isV3Token: false
            };
        }

        return null;
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
     * Generate login URL with return URL
     */
    const generateLoginUrl = function(returnUrl) {
        const currentUrl = returnUrl || window.location.href;
        const encodedReturnUrl = encodeURIComponent(currentUrl);
        return AuthConfig.ACCOUNTS_APP_URL + '?retUrl=' + encodedReturnUrl;
    };

    /**
     * Generate logout URL with return URL
     */
    const generateLogoutUrl = function(returnUrl) {
        const currentUrl = returnUrl || window.location.href;
        const encodedReturnUrl = encodeURIComponent(currentUrl);
        return AuthConfig.ACCOUNTS_APP_URL + '?logout=true&retUrl=' + encodedReturnUrl;
    };

    /**
     * Redirect to login page
     */
    const login = function(returnUrl) {
        window.location.href = generateLoginUrl(returnUrl);
    };

    /**
     * Clear session and redirect to logout
     */
    const logout = function(returnUrl) {
        // Clear local state
        _memberInfo = null;

        // Clear cookies (they're httpOnly so this may not work, but try)
        clearCookie(AuthConfig.COOKIE_NAME);
        clearCookie(AuthConfig.V3_COOKIE_NAME);

        // Redirect to logout URL
        window.location.href = generateLogoutUrl(returnUrl);
    };

    /**
     * Get cookie value by name
     */
    const getCookie = function(name) {
        const nameEQ = name + '=';
        const cookies = document.cookie.split(';');
        for (let i = 0; i < cookies.length; i++) {
            let cookie = cookies[i].trim();
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

    // Public API
    return {
        // Initialization
        init: init,
        isInitialized: function() { return _isInitialized; },

        // Token management
        getToken: getToken,
        decodeToken: decodeToken,

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

        // Error types for consumers
        ErrorTypes: ErrorTypes
    };
})();

// Export for module systems if available
if (typeof module !== 'undefined' && module.exports) {
    module.exports = AuthService;
}
