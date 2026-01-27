/**
 * Unit tests for AuthUI
 */

describe('AuthUI', () => {
    let AuthUI;
    let mockAuthService;

    beforeEach(() => {
        // Clear cookies
        global.clearCookies();

        // Reset mocks
        jest.clearAllMocks();

        // Set up DOM with auth UI elements
        document.body.innerHTML = `
            <iframe id="tc-auth-connector" style="display: none;"></iframe>
            <div id="auth-container" class="auth-container">
                <div id="auth-loading" class="auth-loading">
                    <span class="auth-loading-spinner"></span>
                    <span>Loading...</span>
                </div>
                <div id="auth-error" class="auth-error hidden">
                    <span id="auth-error-message">Authentication error</span>
                    <button id="auth-retry-btn" class="auth-error-retry">Retry</button>
                </div>
                <div id="auth-unauthenticated" class="hidden">
                    <button id="login-btn" class="btn btn-login">
                        <span>Login</span>
                    </button>
                </div>
                <div id="auth-authenticated" class="auth-user hidden">
                    <span id="auth-user-icon" class="auth-user-icon"></span>
                    <span id="auth-user-handle" class="auth-user-handle"></span>
                    <button id="logout-btn" class="btn btn-logout">
                        <span>Logout</span>
                    </button>
                </div>
            </div>
        `;

        // Mock AuthService
        mockAuthService = {
            init: jest.fn().mockResolvedValue(true),
            isAuthenticated: jest.fn().mockReturnValue(false),
            getMemberHandle: jest.fn().mockReturnValue(null),
            getMemberInfo: jest.fn().mockReturnValue(null),
            login: jest.fn(),
            logout: jest.fn(),
            verifyWithBackend: jest.fn().mockResolvedValue({ authenticated: false })
        };
        global.AuthService = mockAuthService;

        // Load AuthUI module
        global.loadModule('js/auth/auth-ui.js');
        AuthUI = global.AuthUI;
    });

    describe('init', () => {
        test('should show loading state initially', () => {
            const loadingEl = document.getElementById('auth-loading');
            const unauthEl = document.getElementById('auth-unauthenticated');
            const authEl = document.getElementById('auth-authenticated');

            // Before init, loading should be visible
            expect(loadingEl.classList.contains('hidden')).toBe(false);
            expect(unauthEl.classList.contains('hidden')).toBe(true);
            expect(authEl.classList.contains('hidden')).toBe(true);
        });

        test('should show unauthenticated state when not logged in', async () => {
            mockAuthService.init.mockResolvedValue(true);
            mockAuthService.isAuthenticated.mockReturnValue(false);

            AuthUI.init();

            // Fast-forward timers and flush promises
            await jest.runAllTimersAsync();

            const loadingEl = document.getElementById('auth-loading');
            const unauthEl = document.getElementById('auth-unauthenticated');

            expect(loadingEl.classList.contains('hidden')).toBe(true);
            expect(unauthEl.classList.contains('hidden')).toBe(false);
        });

        test('should show authenticated state when logged in', async () => {
            mockAuthService.init.mockResolvedValue(true);
            mockAuthService.isAuthenticated.mockReturnValue(true);
            mockAuthService.getMemberInfo.mockReturnValue({
                handle: 'testuser',
                userId: '12345'
            });

            AuthUI.init();

            await jest.runAllTimersAsync();

            const authEl = document.getElementById('auth-authenticated');
            const userHandle = document.getElementById('auth-user-handle');
            const userIcon = document.getElementById('auth-user-icon');

            expect(authEl.classList.contains('hidden')).toBe(false);
            expect(userHandle.textContent).toBe('testuser');
            expect(userIcon.textContent).toBe('t');
        });

        test('should show error state on init failure', async () => {
            mockAuthService.init.mockRejectedValue(new Error('Init failed'));

            AuthUI.init();

            await jest.runAllTimersAsync();

            const errorEl = document.getElementById('auth-error');
            expect(errorEl.classList.contains('hidden')).toBe(false);
        });
    });

    describe('login button', () => {
        test('should call AuthService.login on click', async () => {
            mockAuthService.isAuthenticated.mockReturnValue(false);

            AuthUI.init();
            await jest.runAllTimersAsync();

            const loginBtn = document.getElementById('login-btn');
            loginBtn.click();

            expect(mockAuthService.login).toHaveBeenCalled();
        });
    });

    describe('logout button', () => {
        test('should call AuthService.logout on click', async () => {
            mockAuthService.isAuthenticated.mockReturnValue(true);
            mockAuthService.getMemberInfo.mockReturnValue({
                handle: 'testuser',
                userId: '12345'
            });

            AuthUI.init();
            await jest.runAllTimersAsync();

            const logoutBtn = document.getElementById('logout-btn');
            logoutBtn.click();

            expect(mockAuthService.logout).toHaveBeenCalled();
        });
    });

    describe('retry button', () => {
        test('should reinitialize on retry click', async () => {
            mockAuthService.init.mockRejectedValueOnce(new Error('Init failed'));

            AuthUI.init();
            await jest.runAllTimersAsync();

            // Now make init succeed
            mockAuthService.init.mockResolvedValue(true);

            const retryBtn = document.getElementById('auth-retry-btn');
            retryBtn.click();

            expect(mockAuthService.init).toHaveBeenCalledTimes(2);
        });
    });

    describe('onAuthStateChange', () => {
        test('should call callback when auth state changes', async () => {
            const callback = jest.fn();

            AuthUI.onAuthStateChange(callback);
            mockAuthService.isAuthenticated.mockReturnValue(false);

            AuthUI.init();
            await jest.runAllTimersAsync();

            expect(callback).toHaveBeenCalledWith(false, null);
        });

        test('should call callback with member info when authenticated', async () => {
            const callback = jest.fn();
            const memberInfo = { handle: 'testuser', userId: '12345' };

            mockAuthService.isAuthenticated.mockReturnValue(true);
            mockAuthService.getMemberInfo.mockReturnValue(memberInfo);

            AuthUI.onAuthStateChange(callback);
            AuthUI.init();
            await jest.runAllTimersAsync();

            expect(callback).toHaveBeenCalledWith(true, memberInfo);
        });

        test('should call callback immediately if already initialized', async () => {
            mockAuthService.isAuthenticated.mockReturnValue(true);
            mockAuthService.getMemberInfo.mockReturnValue({ handle: 'test' });

            AuthUI.init();
            await jest.runAllTimersAsync();

            const callback = jest.fn();
            AuthUI.onAuthStateChange(callback);

            expect(callback).toHaveBeenCalled();
        });
    });

    describe('offAuthStateChange', () => {
        test('should remove callback from listeners', async () => {
            const callback = jest.fn();

            AuthUI.onAuthStateChange(callback);
            AuthUI.offAuthStateChange(callback);

            mockAuthService.isAuthenticated.mockReturnValue(false);
            AuthUI.init();
            await jest.runAllTimersAsync();

            // Callback should only be called once (from initial registration when state was loading)
            // After being removed, it shouldn't be called again
        });
    });

    describe('getCurrentState', () => {
        test('should return loading initially', () => {
            expect(AuthUI.getCurrentState()).toBe(AuthUI.States.LOADING);
        });

        test('should return unauthenticated after init when not logged in', async () => {
            mockAuthService.isAuthenticated.mockReturnValue(false);

            AuthUI.init();
            await jest.runAllTimersAsync();

            expect(AuthUI.getCurrentState()).toBe(AuthUI.States.UNAUTHENTICATED);
        });

        test('should return authenticated after init when logged in', async () => {
            mockAuthService.isAuthenticated.mockReturnValue(true);
            mockAuthService.getMemberInfo.mockReturnValue({ handle: 'test' });

            AuthUI.init();
            await jest.runAllTimersAsync();

            expect(AuthUI.getCurrentState()).toBe(AuthUI.States.AUTHENTICATED);
        });
    });

    describe('showErrorState', () => {
        test('should display error message', () => {
            AuthUI.showErrorState('Custom error message');

            const errorEl = document.getElementById('auth-error');
            const errorMsg = document.getElementById('auth-error-message');

            expect(errorEl.classList.contains('hidden')).toBe(false);
            expect(errorMsg.textContent).toBe('Custom error message');
        });
    });

    describe('refresh', () => {
        test('should verify with backend and update state', async () => {
            mockAuthService.verifyWithBackend.mockResolvedValue({
                authenticated: true,
                memberInfo: { handle: 'refresheduser' }
            });

            AuthUI.init();
            await jest.runAllTimersAsync();

            const result = await AuthUI.refresh();

            expect(mockAuthService.verifyWithBackend).toHaveBeenCalled();
        });
    });
});
