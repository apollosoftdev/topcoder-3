/**
 * Authentication UI Manager
 * Handles UI state updates based on authentication status
 *
 * Listens for authentication events and updates UI accordingly:
 * - Loading state during authentication check
 * - Unauthenticated state (login button)
 * - Authenticated state (member handle + logout)
 * - Error state with retry option
 */
const AuthUI = (function() {
    'use strict';

    // UI Element references
    let _elements = null;

    // State change callbacks
    let _stateChangeCallbacks = [];

    // UI States
    const States = {
        LOADING: 'loading',
        UNAUTHENTICATED: 'unauthenticated',
        AUTHENTICATED: 'authenticated',
        ERROR: 'error'
    };

    // Current state
    let _currentState = States.LOADING;

    /**
     * Initialize the authentication UI
     */
    const init = function() {
        // Check if this is a post-login redirect before we clean the URL
        const wasPostLogin = isPostLoginRedirect();

        // Cache DOM elements
        cacheElements();

        // Set up event listeners
        setupEventListeners();

        // Listen for authentication events
        setupAuthEventListeners();

        // Initialize authentication service
        showLoadingState();

        AuthService.init()
            .then(function() {
                if (AuthService.isAuthenticated()) {
                    showAuthenticatedState(AuthService.getMemberInfo());
                } else if (wasPostLogin) {
                    // User just came back from login - retry after delay
                    // (iframe might need more time to load and get token)
                    console.log('Auth: Post-login redirect detected, retrying...');
                    setTimeout(function() {
                        retryAuthCheck();
                    }, 2000);
                } else {
                    showUnauthenticatedState();
                }
            })
            .catch(function(error) {
                console.error('Auth init error:', error);
                if (wasPostLogin) {
                    // Retry on post-login even on error
                    setTimeout(function() {
                        retryAuthCheck();
                    }, 2000);
                } else {
                    showErrorState('Failed to initialize authentication');
                }
            });
    };

    /**
     * Retry auth check (used after post-login redirect)
     */
    let _retryCount = 0;
    const MAX_RETRIES = 3;
    const RETRY_DELAYS = [2000, 3000, 5000]; // Increasing delays

    const retryAuthCheck = function() {
        if (_retryCount >= MAX_RETRIES) {
            console.log('Auth: Max retries reached, showing unauthenticated');
            showUnauthenticatedState();
            _retryCount = 0;
            return;
        }

        console.log('Auth: Retry attempt', _retryCount + 1, 'of', MAX_RETRIES);

        // Re-check for token via iframe connector
        AuthService.getFreshToken()
            .then(function(token) {
                if (token) {
                    const decoded = AuthService.decodeToken(token);
                    if (decoded && !AuthService.isTokenExpired(decoded)) {
                        // Token found! Re-init to update state
                        console.log('Auth: Token found on retry');
                        _retryCount = 0;
                        AuthService.reset();
                        return AuthService.init();
                    }
                }
                return false;
            })
            .then(function() {
                if (AuthService.isAuthenticated()) {
                    showAuthenticatedState(AuthService.getMemberInfo());
                    _retryCount = 0;
                } else {
                    // Schedule another retry
                    _retryCount++;
                    if (_retryCount < MAX_RETRIES) {
                        setTimeout(retryAuthCheck, RETRY_DELAYS[_retryCount]);
                    } else {
                        showUnauthenticatedState();
                        _retryCount = 0;
                    }
                }
            })
            .catch(function() {
                _retryCount++;
                if (_retryCount < MAX_RETRIES) {
                    setTimeout(retryAuthCheck, RETRY_DELAYS[_retryCount]);
                } else {
                    showUnauthenticatedState();
                    _retryCount = 0;
                }
            });
    };

    /**
     * Cache DOM element references
     */
    const cacheElements = function() {
        _elements = {
            container: document.getElementById('auth-container'),
            loading: document.getElementById('auth-loading'),
            error: document.getElementById('auth-error'),
            errorMessage: document.getElementById('auth-error-message'),
            retryBtn: document.getElementById('auth-retry-btn'),
            unauthenticated: document.getElementById('auth-unauthenticated'),
            loginBtn: document.getElementById('login-btn'),
            authenticated: document.getElementById('auth-authenticated'),
            userIcon: document.getElementById('auth-user-icon'),
            userHandle: document.getElementById('auth-user-handle'),
            logoutBtn: document.getElementById('logout-btn')
        };
    };

    /**
     * Set up event listeners for auth buttons
     */
    const setupEventListeners = function() {
        // Login button
        if (_elements.loginBtn) {
            _elements.loginBtn.addEventListener('click', handleLogin);
        }

        // Logout button
        if (_elements.logoutBtn) {
            _elements.logoutBtn.addEventListener('click', handleLogout);
        }

        // Retry button
        if (_elements.retryBtn) {
            _elements.retryBtn.addEventListener('click', handleRetry);
        }

        // Check for post-login redirect (token might be available after redirect)
        checkPostLoginState();
    };

    /**
     * Set up listeners for authentication events from AuthService
     */
    const setupAuthEventListeners = function() {
        // Listen for auth success
        document.addEventListener(AuthService.Events.AUTH_SUCCESS, function(event) {
            const memberInfo = event.detail && event.detail.memberInfo;
            if (memberInfo) {
                showAuthenticatedState(memberInfo);
            }
        });

        // Listen for auth failure
        document.addEventListener(AuthService.Events.AUTH_FAILURE, function(event) {
            const error = event.detail && event.detail.error;
            console.warn('Auth failure:', error);
            showUnauthenticatedState();
        });

        // Listen for logout
        document.addEventListener(AuthService.Events.AUTH_LOGOUT, function() {
            showUnauthenticatedState();
        });

        // Listen for auth state changes (e.g., token expiration)
        document.addEventListener(AuthService.Events.AUTH_STATE_CHANGE, function(event) {
            if (event.detail && !event.detail.authenticated) {
                showUnauthenticatedState();
                // Show message if token expired
                if (event.detail.reason === 'token_expired') {
                    showErrorState('Session expired. Please log in again.');
                }
            }
        });

        // Listen for token refresh
        document.addEventListener(AuthService.Events.TOKEN_REFRESHED, function() {
            // Token refreshed successfully, update UI if needed
            if (AuthService.isAuthenticated()) {
                showAuthenticatedState(AuthService.getMemberInfo());
            }
        });
    };

    /**
     * Check if user just logged in (post-redirect)
     * Returns true if this is a post-login redirect
     */
    const checkPostLoginState = function() {
        // Check URL parameters for login indicators
        const urlParams = new URLSearchParams(window.location.search);
        const hasAuthCallback = urlParams.has('code') || urlParams.has('state');
        const hasAuthMarker = urlParams.has('_auth');

        if (hasAuthCallback || hasAuthMarker) {
            // Clean up URL after login redirect
            urlParams.delete('code');
            urlParams.delete('state');
            urlParams.delete('_auth');
            const remainingParams = urlParams.toString();
            const cleanUrl = window.location.origin + window.location.pathname +
                (remainingParams ? '?' + remainingParams : '');
            window.history.replaceState({}, document.title, cleanUrl);
            return true;
        }
        return false;
    };

    /**
     * Check if this is a post-login redirect and retry auth after delay
     */
    const isPostLoginRedirect = function() {
        const urlParams = new URLSearchParams(window.location.search);
        return urlParams.has('_auth') || urlParams.has('code') || urlParams.has('state');
    };

    /**
     * Handle login button click
     */
    const handleLogin = function(event) {
        event.preventDefault();
        showLoadingState();
        AuthService.login();
    };

    /**
     * Handle logout button click
     */
    const handleLogout = function(event) {
        event.preventDefault();
        showLoadingState();
        AuthService.logout();
    };

    /**
     * Handle retry button click
     */
    const handleRetry = function(event) {
        event.preventDefault();
        // Reset initialization state to allow retry
        AuthService.reset();
        init();
    };

    /**
     * Show loading state
     */
    const showLoadingState = function() {
        _currentState = States.LOADING;
        hideAllStates();

        if (_elements.loading) {
            _elements.loading.classList.remove('hidden');
        }
    };

    /**
     * Hide loading state
     */
    const hideLoadingState = function() {
        if (_elements.loading) {
            _elements.loading.classList.add('hidden');
        }
    };

    /**
     * Show unauthenticated state (login button)
     */
    const showUnauthenticatedState = function() {
        _currentState = States.UNAUTHENTICATED;
        hideAllStates();

        if (_elements.unauthenticated) {
            _elements.unauthenticated.classList.remove('hidden');
        }

        notifyStateChange(false, null);
    };

    /**
     * Show authenticated state (user info and logout)
     */
    const showAuthenticatedState = function(memberInfo) {
        _currentState = States.AUTHENTICATED;
        hideAllStates();

        if (_elements.authenticated) {
            _elements.authenticated.classList.remove('hidden');
        }

        // Update user icon with first letter of handle
        if (_elements.userIcon && memberInfo && memberInfo.handle) {
            _elements.userIcon.textContent = memberInfo.handle.charAt(0).toUpperCase();
        }

        // Update user handle display
        if (_elements.userHandle && memberInfo && memberInfo.handle) {
            _elements.userHandle.textContent = memberInfo.handle;
        }

        notifyStateChange(true, memberInfo);
    };

    /**
     * Show error state with message and retry option
     */
    const showErrorState = function(message) {
        _currentState = States.ERROR;
        hideAllStates();

        if (_elements.error) {
            _elements.error.classList.remove('hidden');
        }

        if (_elements.errorMessage) {
            _elements.errorMessage.textContent = message || 'Authentication error';
        }

        notifyStateChange(false, null);
    };

    /**
     * Hide all authentication states
     */
    const hideAllStates = function() {
        if (_elements.loading) _elements.loading.classList.add('hidden');
        if (_elements.error) _elements.error.classList.add('hidden');
        if (_elements.unauthenticated) _elements.unauthenticated.classList.add('hidden');
        if (_elements.authenticated) _elements.authenticated.classList.add('hidden');
    };

    /**
     * Register a callback for auth state changes
     */
    const onAuthStateChange = function(callback) {
        if (typeof callback === 'function') {
            _stateChangeCallbacks.push(callback);

            // If already initialized, call immediately with current state
            if (_currentState !== States.LOADING) {
                const isAuth = _currentState === States.AUTHENTICATED;
                const memberInfo = isAuth ? AuthService.getMemberInfo() : null;
                callback(isAuth, memberInfo);
            }
        }
    };

    /**
     * Remove a state change callback
     */
    const offAuthStateChange = function(callback) {
        const index = _stateChangeCallbacks.indexOf(callback);
        if (index > -1) {
            _stateChangeCallbacks.splice(index, 1);
        }
    };

    /**
     * Notify all registered callbacks of state change
     */
    const notifyStateChange = function(isAuthenticated, memberInfo) {
        _stateChangeCallbacks.forEach(function(callback) {
            try {
                callback(isAuthenticated, memberInfo);
            } catch (error) {
                console.error('Auth state change callback error:', error);
            }
        });
    };

    /**
     * Get current authentication state
     */
    const getCurrentState = function() {
        return _currentState;
    };

    /**
     * Check if currently authenticated
     */
    const isAuthenticated = function() {
        return _currentState === States.AUTHENTICATED;
    };

    /**
     * Refresh authentication state (re-check with server)
     */
    const refresh = function() {
        showLoadingState();

        return AuthService.verifyWithBackend()
            .then(function(result) {
                if (result.authenticated) {
                    showAuthenticatedState(result.memberInfo || AuthService.getMemberInfo());
                } else {
                    showUnauthenticatedState();
                }
                return result.authenticated;
            })
            .catch(function(error) {
                console.error('Auth refresh error:', error);
                // On error, try local state
                if (AuthService.isAuthenticated()) {
                    showAuthenticatedState(AuthService.getMemberInfo());
                } else {
                    showUnauthenticatedState();
                }
                return AuthService.isAuthenticated();
            });
    };

    // Public API
    return {
        // Initialization
        init: init,

        // State management
        getCurrentState: getCurrentState,
        isAuthenticated: isAuthenticated,
        refresh: refresh,

        // UI state methods
        showLoadingState: showLoadingState,
        hideLoadingState: hideLoadingState,
        showErrorState: showErrorState,

        // State change notifications
        onAuthStateChange: onAuthStateChange,
        offAuthStateChange: offAuthStateChange,

        // States enum for external use
        States: States
    };
})();

// Export for module systems if available
if (typeof module !== 'undefined' && module.exports) {
    module.exports = AuthUI;
}
