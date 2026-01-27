/**
 * Authentication UI Manager
 * Handles UI state updates based on authentication status
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
        // Cache DOM elements
        cacheElements();

        // Set up event listeners
        setupEventListeners();

        // Initialize authentication service
        showLoadingState();

        AuthService.init()
            .then(function(success) {
                if (AuthService.isAuthenticated()) {
                    showAuthenticatedState(AuthService.getMemberInfo());
                } else {
                    showUnauthenticatedState();
                }
            })
            .catch(function(error) {
                console.error('Auth init error:', error);
                showErrorState('Failed to initialize authentication');
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
     * Check if user just logged in (post-redirect)
     */
    const checkPostLoginState = function() {
        // Check URL parameters for login indicators
        const urlParams = new URLSearchParams(window.location.search);
        const hasAuthCallback = urlParams.has('code') || urlParams.has('state');

        if (hasAuthCallback) {
            // Clean up URL after login redirect
            const cleanUrl = window.location.origin + window.location.pathname;
            window.history.replaceState({}, document.title, cleanUrl);
        }
    };

    /**
     * Handle login button click
     */
    const handleLogin = function(event) {
        event.preventDefault();
        AuthService.login();
    };

    /**
     * Handle logout button click
     */
    const handleLogout = function(event) {
        event.preventDefault();
        AuthService.logout();
    };

    /**
     * Handle retry button click
     */
    const handleRetry = function(event) {
        event.preventDefault();
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
            _elements.userIcon.textContent = memberInfo.handle.charAt(0);
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
